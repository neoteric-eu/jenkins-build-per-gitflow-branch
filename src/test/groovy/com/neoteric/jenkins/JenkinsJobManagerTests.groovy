package com.neoteric.jenkins

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test

import com.neoteric.jenkins.JenkinsJobManager;
import com.neoteric.jenkins.TemplateJob;

class JenkinsJobManagerTests {

	JenkinsJobManager jenkinsJobManager
	
	@Before
	void before() {
		jenkinsJobManager = new JenkinsJobManager(jobPrefix: "myproj", jenkinsUrl: "http://dummy.com", gitUrl: "git@dummy.com:company/myproj.git")
	}
	
	@Test
	public void testFindTemplateJobs() {
		
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


	@Test
	public void testFindTemplateJobs_noMatchingJobsThrowsException() {
		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(jobPrefix: "myproj", templateBranchName: "master", jenkinsUrl: "http://dummy.com", gitUrl: "git@dummy.com:company/myproj.git")
		List<String> allJobNames = [
			"otherproj-foo-master",
			"myproj-foo-featurebranch"
		]
		String result = shouldFail(AssertionError) { jenkinsJobManager.findRequiredTemplateJobs(allJobNames) }

		assert result == "Unable to find any jobs matching template regex: ^(myproj-[^-]*)-(master)\$\nYou need at least one job to match the templateJobPrefix and templateBranchName suffix arguments. Expression: (templateJobs?.size() > 0)"
	}


	@Test
	public void testTemplateJobSafeNames() {
		TemplateJob templateJob = new TemplateJob(jobName: "myproj-foo-master", baseJobName: "myproj-foo", templateBranchName: "master")

		assert "myproj-foo-myfeature" == templateJob.jobNameForBranch("myfeature")
		assert "myproj-foo-ted_myfeature" == templateJob.jobNameForBranch("ted/myfeature")
	}


	@Test
	public void testInitGitApi_noBranchRegex() {
		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(gitUrl: "git@dummy.com:company/myproj.git", jenkinsUrl: "http://dummy.com")
		assert jenkinsJobManager.gitApi
	}

	@Test
	public void testInitGitApi_withBranchRegex() {
		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(gitUrl: "git@dummy.com:company/myproj.git", branchNameRegex: 'feature\\/.+|release\\/.+|master', jenkinsUrl: "http://dummy.com")
		assert jenkinsJobManager.gitApi
	}

	@Test
	public void testGetTemplateJobs() {
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

	@Test
	public void testSync() {

		List<TemplateJob> templateJobs = [
			new TemplateJob(jobName: "NeoDocsTemplates-build-feature", baseJobName: "build", templateBranchName: "feature"),
			new TemplateJob(jobName: "NeoDocsTemplates-deploy-feature", baseJobName: "deploy", templateBranchName: "feature"),
			new TemplateJob(jobName: "NeoDocsTemplates-build-hotfix", baseJobName: "build", templateBranchName: "hotfix")
		]

		List<String> jobNames = [
			"NeoDocs-build-feature-test1",
			// add missing deploy test1
			"NeoDocs-deploy-feature-test2",
			// add missing build test2
			"NeoDocs-deploy-feature-test3",
			// to delete
			"NeoDocs-build-hotfix-awaria",
			// do nothing - already there
			"NeoDocs-build-release" // do nothing - no template avail
		]

		List<String> branchNames = [
			"feature-test1",
			"feature-test2",
			"master",
			"release-1.0.0",
			"hotfix-awaria"
		]

		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(jobPrefix: "NeoDocs", templateJobPrefix: "NeoDocsTemplates", gitUrl: "git@dummy.com:company/myproj.git", jenkinsUrl: "http://dummy.com")
		jenkinsJobManager.syncJobs(branchNames ,jobNames, templateJobs)
	}
}