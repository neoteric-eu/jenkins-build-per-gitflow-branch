package com.entagen.jenkins

import org.apache.http.conn.HttpHostConnectException
import org.junit.Test

import groovy.mock.interceptor.MockFor

import org.apache.http.client.HttpResponseException

import groovyx.net.http.RESTClient
import net.sf.json.JSON
import net.sf.json.JSONObject

class JenkinsApiTests extends GroovyTestCase {

	@Test public void testInvalidHostThrowsConnectionException() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://some-invalid-hostname:9090/jenkins")
		assert shouldFail(UnknownHostException) { api.getJobNames("myproj") }.contains("some-invalid-hostname")
	}

	@Test public void testCantConnectToEndpointThrowsException() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:12345/jenkins")
		assert "Connection to http://localhost:12345 refused" == shouldFail(HttpHostConnectException) { api.getJobNames("myproj") }
	}

	@Test public void test404ThrowsException() {
		MockFor mockRESTClient = new MockFor(RESTClient)
		mockRESTClient.demand.get { Map<String, ?> args ->
			def ex = new HttpResponseException(404, "Not Found")
			ex.metaClass.getResponse = {-> [status: 404] }
			throw ex
		}

		mockRESTClient.use {
			JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/goodHostAndPortBadUrl")
			assert "Unexpected failure with path http://localhost:9090/goodHostAndPortBadUrl/api/json, HTTP Status Code: 404, full map: [path:api/json]" == shouldFail() { api.getJobNames("myproj") }
		}
	}

	@Test public void testCreateInViewResolutor() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")
		assert api.resolveViewPath("abc/def") == "view/abc/view/def/"
	}

	@Test public void testGetJobNames_matchPrefix() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")

		Map json = [
			jobs: [
				[name: "myproj-FirstJob"],
				[name: "otherproj-SecondJob"]
			]
		]
		withJsonResponse(json) {
			List<String> projectNames = api.getJobNames("myproj")
			assert projectNames == ["myproj-FirstJob"]
		}
	}

	@Test public void testGetJobNames_noPrefix() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")

		Map json = [
			jobs: [
				[name: "myproj-FirstJob"],
				[name: "otherproj-SecondJob"]
			]
		]
		withJsonResponse(json) {
			List<String> projectNames = api.jobNames
			assert projectNames.sort() == [
				"myproj-FirstJob",
				"otherproj-SecondJob"
			]
		}
	}

	@Test public void testConfigForMissingJob_worksWithRemote() {
		JenkinsApi api = new JenkinsApi()
		api.metaClass.getJobConfig = { String jobName -> "<name>origin/master</name>" }

		TemplateJob templateJob = new TemplateJob(templateBranchName: "master")
		ConcreteJob missingJob = new ConcreteJob(branchName: "new/branch", templateJob: templateJob)
		assert "<name>origin/new/branch</name>" == api.configForMissingJob(missingJob, [])
	}

	@Test public void testConfigForMissingJob_worksWithoutRemote() {
		JenkinsApi api = new JenkinsApi()
		api.metaClass.getJobConfig = { String jobName -> "<name>master</name>" }

		TemplateJob templateJob = new TemplateJob(templateBranchName: "master")
		ConcreteJob missingJob = new ConcreteJob(branchName: "new/branch", templateJob: templateJob)
		assert "<name>new/branch</name>" == api.configForMissingJob(missingJob, [])
	}

	@Test public void testConfigForMissingJob_worksWithExclusions() {
		JenkinsApi api = new JenkinsApi()
		api.metaClass.getJobConfig = { String jobConfig -> "<assignedNode>master</assignedNode>" }

		TemplateJob templateJob = new TemplateJob(templateBranchName: "master")
		ConcreteJob missingJob = new ConcreteJob(branchName: "new/branch", templateJob:  templateJob)
		assert "<assignedNode>master</assignedNode>" == api.configForMissingJob(missingJob, [])
	}

	@Test public void testChangeBranchInConfig() {
		String config = '''
	<maven2-moduleset plugin="maven-plugin@2.7.1">
	  <actions/>
	  <description></description>
	  <keepDependencies>false</keepDependencies>
	  <properties>
    <hudson.model.ParametersDefinitionProperty>
      <parameterDefinitions>
        <hudson.model.BooleanParameterDefinition>
          <name>startOnCreate</name>
          <description></description>
          <defaultValue>true</defaultValue>
        </hudson.model.BooleanParameterDefinition>
        <hudson.model.BooleanParameterDefinition>
          <name>abc</name>
          <description></description>
          <defaultValue>false</defaultValue>
        </hudson.model.BooleanParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>abc</name>
          <description></description>
          <defaultValue>xyz</defaultValue>
        </hudson.model.StringParameterDefinition>
      </parameterDefinitions>
    </hudson.model.ParametersDefinitionProperty>
  </properties>
	  <scm class="hudson.plugins.git.GitSCM" plugin="git@2.2.1">
	    <configVersion>2</configVersion>
	    <userRemoteConfigs>
	      <hudson.plugins.git.UserRemoteConfig>
	        <url>git@gitlab.neoteric.eu:developers/neob2b-neodocs.git</url>
	        <credentialsId>469a31b3-b5e5-45e0-b9c6-9cc3ef61203e</credentialsId>
	      </hudson.plugins.git.UserRemoteConfig>
	    </userRemoteConfigs>
	    <branches>
	      <hudson.plugins.git.BranchSpec>
	        <name>*/templates-feature</name>
	      </hudson.plugins.git.BranchSpec>
	    </branches>
	    <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
	    <browser class="hudson.plugins.git.browser.GitLab">
	      <url></url>
	      <version>7.0</version>
	    </browser>
	    <submoduleCfg class="list"/>
	    <extensions/>
	  </scm>
	  <canRoam>true</canRoam>
	  <disabled>false</disabled>
	  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
	  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
	  <triggers/>
	  <concurrentBuild>false</concurrentBuild>
	  <rootModule>
	    <groupId>com.neoteric.b2b</groupId>
	    <artifactId>neodocs</artifactId>
	  </rootModule>
	  <goals>clean install</goals>
	  <aggregatorStyleBuild>true</aggregatorStyleBuild>
	  <incrementalBuild>false</incrementalBuild>
	  <ignoreUpstremChanges>true</ignoreUpstremChanges>
	  <archivingDisabled>false</archivingDisabled>
	  <siteArchivingDisabled>false</siteArchivingDisabled>
	  <fingerprintingDisabled>false</fingerprintingDisabled>
	  <resolveDependencies>false</resolveDependencies>
	  <processPlugins>false</processPlugins>
	  <mavenValidationLevel>-1</mavenValidationLevel>
	  <runHeadless>false</runHeadless>
	  <disableTriggerDownstreamProjects>false</disableTriggerDownstreamProjects>
	  <blockTriggerWhenBuilding>true</blockTriggerWhenBuilding>
	  <settings class="jenkins.mvn.DefaultSettingsProvider"/>
	  <globalSettings class="jenkins.mvn.DefaultGlobalSettingsProvider"/>
	  <reporters/>
	  <publishers/>
	  <buildWrappers/>
	  <prebuilders/>
	  <postbuilders/>
	  <runPostStepsIfResult>
	    <name>FAILURE</name>
	    <ordinal>2</ordinal>
	    <color>RED</color>
	    <completeBuild>true</completeBuild>
	  </runPostStepsIfResult>
	</maven2-moduleset>
	'''
		
	JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")
	def result = api.processConfig(config, "release-1.0.0");
	println result
	}
	
	@Test public void testShouldStartJob() {
		String config = '''
<maven2-moduleset plugin="maven-plugin@2.7.1">
  <actions/>
  <description></description>
  <keepDependencies>false</keepDependencies>
  <properties>
    <hudson.model.ParametersDefinitionProperty>
      <parameterDefinitions>
        <hudson.model.BooleanParameterDefinition>
          <name>startOnCreate</name>
          <description></description>
          <defaultValue>true</defaultValue>
        </hudson.model.BooleanParameterDefinition>
        <hudson.model.BooleanParameterDefinition>
          <name>abc</name>
          <description></description>
          <defaultValue>false</defaultValue>
        </hudson.model.BooleanParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>abc</name>
          <description></description>
          <defaultValue>xyz</defaultValue>
        </hudson.model.StringParameterDefinition>
      </parameterDefinitions>
    </hudson.model.ParametersDefinitionProperty>
  </properties>
  <scm class="hudson.plugins.git.GitSCM" plugin="git@2.2.1">
    <configVersion>2</configVersion>
    <userRemoteConfigs>
      <hudson.plugins.git.UserRemoteConfig>
        <url>git@gitlab.neoteric.eu:developers/neob2b-neodocs.git</url>
        <credentialsId>469a31b3-b5e5-45e0-b9c6-9cc3ef61203e</credentialsId>
      </hudson.plugins.git.UserRemoteConfig>
    </userRemoteConfigs>
    <branches>
      <hudson.plugins.git.BranchSpec>
        <name>*/whatever</name>
      </hudson.plugins.git.BranchSpec>
    </branches>
    <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
    <browser class="hudson.plugins.git.browser.GitLab">
      <url></url>
      <version>7.0</version>
    </browser>
    <submoduleCfg class="list"/>
    <extensions/>
  </scm>
  <canRoam>true</canRoam>
  <disabled>false</disabled>
  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
  <triggers/>
  <concurrentBuild>false</concurrentBuild>
  <rootModule>
    <groupId>com.neoteric.b2b</groupId>
    <artifactId>neodocs</artifactId>
  </rootModule>
  <goals>clean install</goals>
  <aggregatorStyleBuild>true</aggregatorStyleBuild>
  <incrementalBuild>false</incrementalBuild>
  <ignoreUpstremChanges>true</ignoreUpstremChanges>
  <archivingDisabled>false</archivingDisabled>
  <siteArchivingDisabled>false</siteArchivingDisabled>
  <fingerprintingDisabled>false</fingerprintingDisabled>
  <resolveDependencies>false</resolveDependencies>
  <processPlugins>false</processPlugins>
  <mavenValidationLevel>-1</mavenValidationLevel>
  <runHeadless>false</runHeadless>
  <disableTriggerDownstreamProjects>false</disableTriggerDownstreamProjects>
  <blockTriggerWhenBuilding>true</blockTriggerWhenBuilding>
  <settings class="jenkins.mvn.DefaultSettingsProvider"/>
  <globalSettings class="jenkins.mvn.DefaultGlobalSettingsProvider"/>
  <reporters/>
  <publishers/>
  <buildWrappers/>
  <prebuilders/>
  <postbuilders/>
  <runPostStepsIfResult>
    <name>FAILURE</name>
    <ordinal>2</ordinal>
    <color>RED</color>
    <completeBuild>true</completeBuild>
  </runPostStepsIfResult>
</maven2-moduleset>'''
		
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")
		assert true == api.shouldStartJob(config)
	}
	

	public void withJsonResponse(Map toJson, Closure closure) {
		JSON json = toJson as JSONObject
		MockFor mockRESTClient = new MockFor(RESTClient)
		mockRESTClient.demand.get { Map<String, ?> args ->
			return [data: json]
		}

		mockRESTClient.use { closure() }
	}
}

