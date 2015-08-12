package com.neoteric.jenkins

import static org.assertj.core.api.Assertions.assertThat

import org.apache.http.conn.HttpHostConnectException
import static org.joox.JOOX.*;
import org.junit.Before;
import org.junit.Test
import org.w3c.dom.Document

import groovy.mock.interceptor.MockFor

import org.apache.http.client.HttpResponseException

import com.neoteric.jenkins.ConcreteJob;
import com.neoteric.jenkins.JenkinsApi;
import com.neoteric.jenkins.TemplateJob;

import groovyx.net.http.RESTClient
import net.sf.json.JSON
import net.sf.json.JSONObject

class JenkinsApiTests {
	
	final shouldFail = new GroovyTestCase().&shouldFail

	@Test
	public void shouldThrowExceptionForInvalidUrl() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://some-invalid-hostname:9090/jenkins")
		assert shouldFail(UnknownHostException) { api.getJobNames("myproj") }.contains("some-invalid-hostname")
	}

	@Test
	public void shouldThrowHttpHostConnectExceptionWhenCantConnectToUrl() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:12345/jenkins")
		assert "Connection to http://localhost:12345 refused" == shouldFail(HttpHostConnectException) { api.getJobNames("myproj") }
	}

	@Test
	public void test404ThrowsException() {
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

	@Test
	public void testCreateInViewResolutor() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")
		assert api.resolveViewPath("abc/def") == "view/abc/view/def/"
	}

	@Test
	public void testGetJobNames_matchPrefix() {
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

	@Test
	public void testGetJobNames_noPrefix() {
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

	@Test
	public void shouldChangeConfigBranchName() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")
		def result = api.processConfig(CONFIG, "release-1.0.0", "newGitUrl", JOBS_FOR_BRANCH);
		assertThat(result).contains("<name>*/release-1.0.0</name>")
                .contains("<disabled>false</disabled>")
                .contains("<project>job-build-release-1.0.0</project>")
                .contains("<childProjects>job-deploy-release-1.0.0, some-other-job</childProjects>")
	}

	@Test
	public void shouldChangeGitUrl() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")
		def result = api.processConfig(CONFIG, "release-1.0.0", "newGitUrl", JOBS_FOR_BRANCH);
		assertThat(result).contains("<url>newGitUrl</url>")
                .contains("<disabled>false</disabled>")
                .contains("<project>job-build-release-1.0.0</project>")
                .contains("<childProjects>job-deploy-release-1.0.0, some-other-job</childProjects>")
    }
	
	@Test
	public void shouldChangeSonarBranchName() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")
		def result = api.processConfig(CONFIG, "release-1.0.0", "newGitUrl", JOBS_FOR_BRANCH);
		assertThat(result).contains("<branch>release-1.0.0</branch>")
                .contains("<disabled>false</disabled>")
                .contains("<project>job-build-release-1.0.0</project>")
                .contains("<childProjects>job-deploy-release-1.0.0, some-other-job</childProjects>")
	}
	
	@Test
	public void shouldNotThrowExceptionWhenNoSonarConfig() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")
		def result = api.processConfig(CONFIG_NO_SONAR, "release-1.0.0", "newGitUrl", JOBS_FOR_BRANCH);
        assertThat(result).contains("<disabled>true</disabled>")

    }
	
	@Test
	public void testShouldStartJob() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")
		assert true == api.shouldStartJob(CONFIG)
	}

	public void withJsonResponse(Map toJson, Closure closure) {
		JSON json = toJson as JSONObject
		MockFor mockRESTClient = new MockFor(RESTClient)
		mockRESTClient.demand.get { Map<String, ?> args ->
			return [data: json]
		}

		mockRESTClient.use { closure() }
	}
	
	static final String CONFIG = '''
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
          <name>enableOnCreate</name>
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
        <url>git@githost.com/repo.git</url>
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
  <disabled>true</disabled>
  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
  <triggers/>
  <concurrentBuild>false</concurrentBuild>
  <rootModule>
    <groupId>com.neoteric</groupId>
    <artifactId>artifactId</artifactId>
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
  <publishers>
    <hudson.plugins.sonar.SonarPublisher plugin="sonar@2.1">
      <jdk>(Inherit From Job)</jdk>
      <branch>toBeChanged</branch>
      <language></language>
      <mavenOpts></mavenOpts>
      <jobAdditionalProperties>-Dsonar.java.source=1.7</jobAdditionalProperties>
      <settings class="jenkins.mvn.DefaultSettingsProvider"/>
      <globalSettings class="jenkins.mvn.DefaultGlobalSettingsProvider"/>
      <usePrivateRepository>false</usePrivateRepository>
    </hudson.plugins.sonar.SonarPublisher>
    <hudson.tasks.BuildTrigger>
      <childProjects>myproj-deploy-release, some-other-job</childProjects>
      <threshold>
        <name>SUCCESS</name>
        <ordinal>0</ordinal>
        <color>BLUE</color>
        <completeBuild>true</completeBuild>
      </threshold>
    </hudson.tasks.BuildTrigger>
  </publishers>
  <buildWrappers/>
  <prebuilders>
    <hudson.plugins.copyartifact.CopyArtifact plugin="copyartifact@1.33">
      <project>myproj-build-release</project>
      <filter></filter>
      <target></target>
      <excludes></excludes>
      <selector class="hudson.plugins.copyartifact.StatusBuildSelector"/>
      <doNotFingerprintArtifacts>false</doNotFingerprintArtifacts>
    </hudson.plugins.copyartifact.CopyArtifact>
  </prebuilders>  <postbuilders/>
  <runPostStepsIfResult>
    <name>FAILURE</name>
    <ordinal>2</ordinal>
    <color>RED</color>
    <completeBuild>true</completeBuild>
  </runPostStepsIfResult>
</maven2-moduleset>'''

    static final List<ConcreteJob> JOBS_FOR_BRANCH =  [
            new ConcreteJob(templateJob: new TemplateJob(jobName: "myproj-build-release"),
                    jobName: "job-build-release-1.0.0"),
            new ConcreteJob(templateJob: new TemplateJob(jobName: "myproj-deploy-release"),
                    jobName: "job-deploy-release-1.0.0")
            ]
static final String CONFIG_NO_SONAR = '''
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
        <url>git@githost.com/repo.git</url>
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
  <disabled>true</disabled>
  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
  <triggers/>
  <concurrentBuild>false</concurrentBuild>
  <rootModule>
    <groupId>com.neoteric</groupId>
    <artifactId>artifactId</artifactId>
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
}

