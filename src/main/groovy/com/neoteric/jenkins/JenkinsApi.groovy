package com.neoteric.jenkins


import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import org.apache.http.HttpRequest
import org.apache.http.HttpRequestInterceptor
import org.apache.http.HttpStatus
import org.apache.http.client.HttpResponseException
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.protocol.HttpContext

import static groovyx.net.http.ContentType.TEXT

class JenkinsApi {


    final String SHOULD_START_PARAM_NAME = "startOnCreate"
	final String SHOULD_ENABLE_ON_CREATE = "enableOnCreate"
    String jenkinsServerUrl
    RESTClient restClient
    HttpRequestInterceptor requestInterceptor
    boolean findCrumb = true
    def crumbInfo

    public void setJenkinsServerUrl(String jenkinsServerUrl) {
        if (!jenkinsServerUrl.endsWith("/")) jenkinsServerUrl += "/"
        this.jenkinsServerUrl = jenkinsServerUrl
        this.restClient = new RESTClient(jenkinsServerUrl)
    }

    public void addBasicAuth(String jenkinsServerUser, String jenkinsServerPassword) {
        println "use basic authentication"

        this.requestInterceptor = new HttpRequestInterceptor() {
            void process(HttpRequest httpRequest, HttpContext httpContext) {
                def auth = jenkinsServerUser + ':' + jenkinsServerPassword
                httpRequest.addHeader('Authorization', 'Basic ' + auth.bytes.encodeBase64().toString())
            }
        }

        this.restClient.client.addRequestInterceptor(this.requestInterceptor)
    }

    List<String> getJobNames(String prefix = null) {
        println "getting project names from " + jenkinsServerUrl + "api/json"
        def response = get(path: 'api/json')
        def jobNames = response.data.jobs.name
        if (prefix) return jobNames.findAll { it.startsWith(prefix) }
        return jobNames
    }

    String getJobConfig(String jobName) {
        def response = get(path: "job/${jobName}/config.xml", contentType: TEXT,
                headers: [Accept: 'application/xml'])
        response.data.text
    }

    void cloneJobForBranch(String jobPrefix, ConcreteJob missingJob, String createJobInView, String gitUrl, String scriptCommand, List<ConcreteJob> jobsForBranch) {
        String createJobInViewPath = resolveViewPath(createJobInView)
        println "-----> createInView after" + createJobInView
        String missingJobConfig = configForMissingJob(missingJob, gitUrl, scriptCommand, jobsForBranch)
        TemplateJob templateJob = missingJob.templateJob

        //Copy job with jenkins copy job api, this will make sure jenkins plugins get the call to make a copy if needed (promoted builds plugin needs this)
        post(createJobInViewPath + 'createItem', missingJobConfig, [name: missingJob.jobName, mode: 'copy', from: templateJob.jobName], ContentType.XML)

        post('job/' + missingJob.jobName + "/config.xml", missingJobConfig, [:], ContentType.XML)
        //Forced disable enable to work around Jenkins' automatic disabling of clones jobs
        //But only if the original job was enabled
        post('job/' + missingJob.jobName + '/disable')
        if (!missingJobConfig.contains("<disabled>true</disabled>")) {
            post('job/' + missingJob.jobName + '/enable')
        }
    }

    public String resolveViewPath(String createInView) {
        if (!createInView) {
            return ""
        }
        List<String> elements = createInView.tokenize("/")
        elements = elements.collect { "view/" + it + "/" }
        elements.join();
    }

    String configForMissingJob(ConcreteJob missingJob, String gitUrl, String scriptCommand, List<ConcreteJob> jobsForBranch) {
        TemplateJob templateJob = missingJob.templateJob
        String config = getJobConfig(templateJob.jobName)
        return processConfig(config, missingJob.branchName, gitUrl, scriptCommand, jobsForBranch)
    }

    public String processConfig(String entryConfig, String branchName, String gitUrl, String scriptCommand, List<ConcreteJob> jobsForBranch) {

        def root = new XmlParser().parseText(entryConfig)
        // update branch name
        root.scm.branches."hudson.plugins.git.BranchSpec".name[0].value = "*/$branchName"

        // update GIT url
        root.scm.userRemoteConfigs."hudson.plugins.git.UserRemoteConfig".url[0].value = "$gitUrl"

        //update Sonar
        if (root.publishers."hudson.plugins.sonar.SonarPublisher".branch[0] != null) {
            root.publishers."hudson.plugins.sonar.SonarPublisher".branch[0].value = "$branchName"
        }

        //update Publish over SSH exec
        def publishers = root.postbuilders."jenkins.plugins.publish__over__ssh.BapSshBuilderPlugin".delegate.delegate.publishers."jenkins.plugins.publish__over__ssh.BapSshPublisher"
        if (publishers != null) {
            for (publisher in publishers) {
                publisher.transfers[0]."jenkins.plugins.publish__over__ssh.BapSshTransfer"[0].execCommand[0].value = "$scriptCommand"
            }
        }

        // update project relationships
        replaceJobName(jobsForBranch, root.prebuilders."hudson.plugins.copyartifact.CopyArtifact".project)
        replaceJobName(jobsForBranch, root.publishers."hudson.tasks.BuildTrigger".childProjects)

        //remove template build / enable variable
        Node startOnCreateParam = findStartOnCreateParameter(root)
        if (startOnCreateParam) {
            startOnCreateParam.parent().remove(startOnCreateParam)
        }
        Node enableOnCreateParam = findEnableOnCreateParameter(root)
		if (enableOnCreateParam) {
			if (enableOnCreateParam.defaultValue[0]?.text().toBoolean()) {
				root.disabled[0].value = "false"
			}
			enableOnCreateParam.parent().remove(enableOnCreateParam)
		}

        //check if it was the only parameter - if so, remove the enclosing tag, so the project won't be seen as build with parameters
        def propertiesNode = root.properties
        def parameterDefinitionsProperty = propertiesNode."hudson.model.ParametersDefinitionProperty".parameterDefinitions[0]

        if (parameterDefinitionsProperty != null && !parameterDefinitionsProperty.attributes() && !parameterDefinitionsProperty.children() && !parameterDefinitionsProperty.text()) {
            root.remove(propertiesNode)
            new Node(root, 'properties')
        }


        def writer = new StringWriter()
        XmlNodePrinter xmlPrinter = new XmlNodePrinter(new PrintWriter(writer))
        xmlPrinter.setPreserveWhitespace(true)
        xmlPrinter.print(root)
        return writer.toString()
    }

    void startJob(ConcreteJob job) {
        String templateConfig = getJobConfig(job.templateJob.jobName)
        if (shouldStartJob(templateConfig)) {
            println "Starting job ${job.jobName}."
            post('job/' + job.jobName + '/build')
        }
    }

    public boolean shouldStartJob(String config) {
        Node root = new XmlParser().parseText(config)
        Node startOnCreateParam = findStartOnCreateParameter(root)
        if (!startOnCreateParam) {
            return false
        }
        return startOnCreateParam.defaultValue[0]?.text().toBoolean()
    }

	Node findStartOnCreateParameter(Node root) {
		return findParameter(root, SHOULD_START_PARAM_NAME)
	}

	Node findEnableOnCreateParameter(Node root) {
		return findParameter(root, SHOULD_ENABLE_ON_CREATE)
	}

	static Node findParameter(Node root, String parameter) {
		return root.properties."hudson.model.ParametersDefinitionProperty".parameterDefinitions."hudson.model.BooleanParameterDefinition".find {
			it.name[0].text() == parameter
		}
	}

    void deleteJob(String jobName) {
        println "deleting job $jobName"
        post("job/${jobName}/doDelete")
    }


    protected get(Map map) {
        // get is destructive to the map, if there's an error we want the values around still
        Map mapCopy = map.clone() as Map
        def response

        assert mapCopy.path != null, "'path' is a required attribute for the GET method"

        try {
            response = restClient.get(map)
        } catch (HttpHostConnectException ex) {
            println "Unable to connect to host: $jenkinsServerUrl"
            throw ex
        } catch (UnknownHostException ex) {
            println "Unknown host: $jenkinsServerUrl"
            throw ex
        } catch (HttpResponseException ex) {
            def message = "Unexpected failure with path $jenkinsServerUrl${mapCopy.path}, HTTP Status Code: ${ex.response?.status}, full map: $mapCopy"
            throw new Exception(message, ex)
        }

        assert response.status < 400
        return response
    }

    /**
     * @author Kelly Robinson
     * from https://github.com/kellyrob99/Jenkins-api-tour/blob/master/src/main/groovy/org/kar/hudson/api/PostRequestSupport.groovy
     */
    protected Integer post(String path, postBody = [:], params = [:], ContentType contentType = ContentType.URLENC) {
        println "----> MAKING POST with PATH: " + path
        //Added the support for jenkins CSRF option, this could be changed to be a build flag if needed.
        //http://jenkinsurl.com/crumbIssuer/api/json  get crumb for csrf protection  json: {"crumb":"c8d8812d615292d4c0a79520bacfa7d8","crumbRequestField":".crumb"}
        if (findCrumb) {
            findCrumb = false
            println "Trying to find crumb: ${jenkinsServerUrl}crumbIssuer/api/json"
            try {
                def response = restClient.get(path: "crumbIssuer/api/json")

                if (response.data.crumbRequestField && response.data.crumb) {
                    crumbInfo = [:]
                    crumbInfo['field'] = response.data.crumbRequestField
                    crumbInfo['crumb'] = response.data.crumb
                    def cookies = []
                    response.headers['Set-Cookie'].each {
                        //[Set-Cookie: JSESSIONID=E68D4799D4D6282F0348FDB7E8B88AE9; Path=/frontoffice/; HttpOnly]
                        String cookie = it.value.split(';')[0]
                        cookies.add(cookie)
                    }
                    crumbInfo['cookies'] = cookies
                    println "Found crumb data: " + crumbInfo
                } else {
                    println "Found crumbIssuer but didn't understand the response data trying to move on."
                    println "Response data: " + response.data
                }
            }
            catch (HttpResponseException e) {
                if (e.response?.status == 404) {
                    println "Couldn't find crumbIssuer for jenkins. Just moving on it may not be needed."
                } else {
                    def msg = "Unexpected failure on ${jenkinsServerUrl}crumbIssuer/api/json: ${resp.statusLine} ${resp.status}"
                    throw new Exception(msg)
                }
            }
        }

        HTTPBuilder http = new HTTPBuilder(jenkinsServerUrl)
        if (crumbInfo) {
            http.getHeaders().put(crumbInfo.field, crumbInfo.crumb)
            http.getHeaders().put('Cookie', crumbInfo.cookies.join(';'))
        }

        if (requestInterceptor) {
            http.client.addRequestInterceptor(this.requestInterceptor)
        }

        Integer status = HttpStatus.SC_EXPECTATION_FAILED

        http.handler.failure = { resp ->
            def msg = "Unexpected failure on $jenkinsServerUrl$path: ${resp.statusLine} ${resp.status}"
            status = resp.statusLine.statusCode
            throw new Exception(msg)
        }

        http.post(path: path, body: postBody, query: params,
                requestContentType: contentType) { resp ->
            assert resp.statusLine.statusCode < 400
            status = resp.statusLine.statusCode
        }
        return status
    }

    protected static void replaceJobName(List<ConcreteJob> jobs, NodeList nodes) {
        nodes.each {Node node ->
            String text = node.text();
            jobs.each { ConcreteJob job ->
                text = text.replace(job.templateJob.jobName, job.jobName)
            }
            node.value = text
        }
    }
}
