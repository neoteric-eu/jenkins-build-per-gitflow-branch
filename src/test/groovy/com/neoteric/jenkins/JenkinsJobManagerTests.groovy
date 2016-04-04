package com.neoteric.jenkins

import static org.junit.Assert.*;
import static org.assertj.core.api.Assertions.assertThat
import groovy.mock.interceptor.MockFor;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test
import org.junit.contrib.java.lang.system.StandardOutputStreamLog

import com.neoteric.jenkins.JenkinsJobManager;
import com.neoteric.jenkins.TemplateJob;

class JenkinsJobManagerTests {

	@Rule
	public final StandardOutputStreamLog log = new StandardOutputStreamLog();
	
	final shouldFail = new GroovyTestCase().&shouldFail

	@Test
	public void testFindTemplateJobs() {
		JenkinsJobManager jenkinsJobManager =
				new JenkinsJobManager(templateJobPrefix: "template", jobPrefix: "myproj", jenkinsUrl: "http://dummy.com", gitUrl: "git@dummy.com:company/myproj.git")

		List<String> allJobNames = [
			"myproj-foo-master",
			"otherproj-foo-master",
			"template-foo-feature"
		]
		List<TemplateJob> templateJobs = jenkinsJobManager.findRequiredTemplateJobs(allJobNames)
		assert templateJobs.size() == 1
		TemplateJob templateJob = templateJobs.first()
		assert templateJob.jobName == "template-foo-feature"
		assert templateJob.baseJobName == "foo"
		assert templateJob.templateBranchName == "feature"
	}


	@Test
	public void testFindTemplateJobs_noMatchingJobsThrowsException() {
		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(templateJobPrefix: "template", jobPrefix: "myproj", jenkinsUrl: "http://dummy.com", gitUrl: "git@dummy.com:company/myproj.git")
		List<String> allJobNames = [
			"otherproj-foo-master",
			"myproj-foo-featurebranch"
		]
		String result = shouldFail(AssertionError) { jenkinsJobManager.findRequiredTemplateJobs(allJobNames) }

		assertThat(result).contains("Unable to find any jobs matching template regex")
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
			"NeoDocs-build-hotfix-emergency",
			// do nothing - already there
			"NeoDocs-build-release" // do nothing - no template avail
		]

		List<String> branchNames = [
			"feature-test1",
			"feature-test2",
			"master",
			"release-1.0.0",
			"hotfix-emergency"
		]
		JenkinsJobManager jenkinsJobManager = new JenkinsJobManager(jobPrefix: "NeoDocs", templateJobPrefix: "NeoDocsTemplates", gitUrl: "git@dummy.com:company/myproj.git", jenkinsUrl: "http://dummy.com")

		jenkinsJobManager.jenkinsApi = new JenkinsApiMocked()
		jenkinsJobManager.syncJobs(branchNames ,jobNames, templateJobs)

		assertThat(log.getLog().substring(log.getLog().indexOf("Summary"))).containsSequence( 
			"Creating", "NeoDocs-deploy-feature-test1 from NeoDocsTemplates-deploy-feature",
						"NeoDocs-build-feature-test2 from NeoDocsTemplates-build-feature",
			"Deleting", "NeoDocs-deploy-feature-test3")
		}

	class JenkinsApiMocked extends JenkinsApi {
		
		@Override
		public void cloneJobForBranch(String jobPrefix, ConcreteJob missingJob, String createJobInView, String gitUrl, String scriptCommand) {
		}
		
		@Override
		public void deleteJob(String jobName) {
		}
		
		@Override
		public void startJob(ConcreteJob job) {
		}
	}
	
}
