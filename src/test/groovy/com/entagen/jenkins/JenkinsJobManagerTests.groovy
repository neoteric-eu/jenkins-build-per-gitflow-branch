package com.entagen.jenkins

import static org.junit.Assert.*;

import org.junit.Test

class JenkinsJobManagerTests extends GroovyTestCase {
	@Test public void testFindTemplateJobs() {
		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(jobPrefix: "myproj", templateBranchName: "master", jenkinsUrl: "http://dummy.com", gitUrl: "git@dummy.com:company/myproj.git")
		List<String> allJobNames = [
			"myproj-foo-master",
			"otherproj-foo-master",
			"myproj-foo-featurebranch"
		]
		List<TemplateJob> templateJobs = jenkinsJobManager.findRequiredTemplateJobs(allJobNames)
		assert templateJobs.size() == 1
		TemplateJob templateJob = templateJobs.first()
		assert templateJob.jobName == "myproj-foo-master"
		assert templateJob.baseJobName == "myproj-foo"
		assert templateJob.templateBranchName == "master"
	}


	@Test public void testFindTemplateJobs_noMatchingJobsThrowsException() {
		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(jobPrefix: "myproj", templateBranchName: "master", jenkinsUrl: "http://dummy.com", gitUrl: "git@dummy.com:company/myproj.git")
		List<String> allJobNames = [
			"otherproj-foo-master",
			"myproj-foo-featurebranch"
		]
		String result = shouldFail(AssertionError) { jenkinsJobManager.findRequiredTemplateJobs(allJobNames) }

		assert result == "Unable to find any jobs matching template regex: ^(myproj-[^-]*)-(master)\$\nYou need at least one job to match the templateJobPrefix and templateBranchName suffix arguments. Expression: (templateJobs?.size() > 0)"
	}


	@Test public void testTemplateJobSafeNames() {
		TemplateJob templateJob = new TemplateJob(jobName: "myproj-foo-master", baseJobName: "myproj-foo", templateBranchName: "master")

		assert "myproj-foo-myfeature" == templateJob.jobNameForBranch("myfeature")
		assert "myproj-foo-ted_myfeature" == templateJob.jobNameForBranch("ted/myfeature")
	}


	@Test public void testInitGitApi_noBranchRegex() {
		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(gitUrl: "git@dummy.com:company/myproj.git", jenkinsUrl: "http://dummy.com")
		assert jenkinsJobManager.gitApi
	}

	@Test public void testInitGitApi_withBranchRegex() {
		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(gitUrl: "git@dummy.com:company/myproj.git", branchNameRegex: 'feature\\/.+|release\\/.+|master', jenkinsUrl: "http://dummy.com")
		assert jenkinsJobManager.gitApi
	}

	@Test public void testGetTemplateJobs() {
		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(jobPrefix: "NeoDocs", templateJobPrefix: "NeoDocsTemplates", gitUrl: "git@dummy.com:company/myproj.git", jenkinsUrl: "http://dummy.com")

		List<String> allJobNames = [
			"NeoDocs-build-feature",
			"NeoDocsTemplates-build-feature",
			"NeoDocsTemplates-build-featured",
			"NeoDocsTemplates-deploy-feature",
			"NeoDocsTemplates-build-hotfix"
		]
		List<TemplateJob> templateJobs = [
			new TemplateJob(jobName: "NeoDocsTemplates-build-feature", baseJobName: "build", templateBranchName: "feature"),
			new TemplateJob(jobName: "NeoDocsTemplates-deploy-feature", baseJobName: "deploy", templateBranchName: "feature"),
			new TemplateJob(jobName: "NeoDocsTemplates-build-hotfix", baseJobName: "build", templateBranchName: "hotfix")
		]
		
		assert templateJobs == jenkinsJobManager.findRequiredTemplateJobs(allJobNames)
	}

	@Test public void testSync() {

		List<TemplateJob> templateJobs = [
			new TemplateJob(jobName: "NeoDocsTemplates-build-feature", baseJobName: "build", templateBranchName: "feature"),
			new TemplateJob(jobName: "NeoDocsTemplates-deploy-feature", baseJobName: "deploy", templateBranchName: "feature"),
			new TemplateJob(jobName: "NeoDocsTemplates-build-hotfix", baseJobName: "build", templateBranchName: "hotfix")
		]
		
		List<String> jobNames = [
			"NeoDocs-build-feature-test1", // add missing deploy test1
			"NeoDocs-deploy-feature-test2", // add missing build test2
			"NeoDocs-deploy-feature-test3", // to delete
			"NeoDocs-build-hotfix-awaria", // do nothing - already there
			"NeoDocs-build-release" // do nothing - no template avail
		]

		List<String> branchNames = ["feature-test1", "feature-test2", "master", "release-1.0.0", "hotfix-awaria"]
		
		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(jobPrefix: "NeoDocs", templateJobPrefix: "NeoDocsTemplates", gitUrl: "git@dummy.com:company/myproj.git", jenkinsUrl: "http://dummy.com")
		jenkinsJobManager.syncJobs(branchNames ,jobNames, templateJobs)
	}
	
	@Test public void testFetchingStartJobParameterValue() {
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
		
		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(jobPrefix: "NeoDocs", templateJobPrefix: "NeoDocsTemplates", gitUrl: "git@dummy.com:company/myproj.git", jenkinsUrl: "http://dummy.com")
		
		assert true ==jenkinsJobManager.shouldStartJob(config)
	}
}