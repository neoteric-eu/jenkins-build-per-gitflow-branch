package com.entagen.jenkins

import java.util.regex.Pattern

class JenkinsJobManager {


	//TODO 1 - hardcoded template branches, enable to use only one template for all (feature, release, hotfix?)

	String templateJobPrefix
	String jobPrefix
	String gitUrl
	String nestedView
	String createJobInView
	String jenkinsUrl
	String jenkinsUser
	String jenkinsPassword

	String featureSuffix = "feature-"
	String hotfixSuffix = "hotfix-"
	String releaseSuffix = "release-"

	String templateFeatureSuffix = "feature"
	String templateHotfixSuffix = "hotfix"
	String templateReleaseSuffix = "release"

	def branchSuffixMatch = [(templateFeatureSuffix):featureSuffix,
		(templateHotfixSuffix): hotfixSuffix,
		(templateReleaseSuffix): releaseSuffix
	]

	Boolean dryRun = false
	Boolean noDelete = false
	Boolean startOnCreate = false

	JenkinsApi jenkinsApi
	GitApi gitApi

	JenkinsJobManager(Map props) {
		for (property in props) {
			this."${property.key}" = property.value
		}
		initJenkinsApi()
		initGitApi()
	}

	void syncWithRepo() {
		List<String> allBranchNames = gitApi.branchNames
		println "-------------------------------------"
		println "All branch names:" + allBranchNames

		List<String> allJobNames = jenkinsApi.jobNames
		println "-------------------------------------"
		println "All job names:" + allJobNames

		// ensure that there is at least one job matching the template pattern, collect the set of template jobs
		List<TemplateJob> templateJobs = findRequiredTemplateJobs(allJobNames)
		println "-------------------------------------"
		println "Template Jobs:" + templateJobs

		List<String> jobsWithJobPrefix = allJobNames.findAll { jobName ->
			jobName.startsWith(jobPrefix)
		}
		println "-------------------------------------"
		println "Jobs with provided prefix:" + jobsWithJobPrefix

		// create any missing template jobs and delete any jobs matching the template patterns that no longer have branches
		syncJobs(allBranchNames, jobsWithJobPrefix, templateJobs)

	}

	public List<TemplateJob> findRequiredTemplateJobs(List<String> allJobNames) {
		String regex = /^($templateJobPrefix)-(.*)-($templateFeatureSuffix|$templateReleaseSuffix|$templateHotfixSuffix)$/

		List<TemplateJob> templateJobs = allJobNames.findResults { String jobName ->

			TemplateJob templateJob = null
			jobName.find(regex) {full, templateName, baseJobName, branchName ->
				templateJob = new TemplateJob(jobName: full, baseJobName: baseJobName, templateBranchName: branchName)
			}
			return templateJob
		}

		assert templateJobs?.size() > 0, "Unable to find any jobs matching template regex: $regex\nYou need at least one job to match the templateJobPrefix and templateBranchName (feature, hotfix, release) suffix arguments"
		return templateJobs
	}

	public void syncJobs(List<String> allBranchNames, List<String> jobNames, List<TemplateJob> templateJobs) {

		def templateJobsByBranch = templateJobs.groupBy({ template -> template.templateBranchName })

		List<ConcreteJob> missingJobs = [];
		List<String> jobsToDelete = [];

		templateJobsByBranch.keySet().each { templateBranchToProcess ->
			println "-> Checking $templateBranchToProcess branches"
			List<String> branchesWithCorrespondingTemplate = allBranchNames.findAll { branchName ->
				branchName.startsWith(branchSuffixMatch[templateBranchToProcess])
			}

			println "---> Founded corresponding branches: $branchesWithCorrespondingTemplate"
			branchesWithCorrespondingTemplate.each { branchToProcess ->
				println "-----> Processing branch: $branchToProcess"
				List<ConcreteJob> expectedJobsPerBranch = templateJobsByBranch[templateBranchToProcess].collect { TemplateJob templateJob ->
					templateJob.concreteJobForBranch(jobPrefix, branchToProcess)
				}
				println "-------> Expected jobs:"
				expectedJobsPerBranch.each { println "           $it" }
				List<String> jobNamesPerBranch = jobNames.findAll{ it.endsWith(branchToProcess) }
				println "-------> Job Names per branch:"
				jobNamesPerBranch.each { println "           $it" }
				List<ConcreteJob> missingJobsPerBranch = expectedJobsPerBranch.findAll { expectedJob ->
					!jobNamesPerBranch.any {it.contains(expectedJob.jobName) }
				}
				println "-------> Missing jobs:"
				missingJobsPerBranch.each { println "           $it" }
				missingJobs.addAll(missingJobsPerBranch)
			}

			List<String> deleteCandidates = jobNames.findAll {  it.contains(branchSuffixMatch[templateBranchToProcess]) }
			List<String> jobsToDeletePerBranch = deleteCandidates.findAll { candidate ->
				!branchesWithCorrespondingTemplate.any { candidate.endsWith(it) }
			}

			println "-----> Jobs to delete:"
			jobsToDeletePerBranch.each { println "         $it" }
			jobsToDelete.addAll(jobsToDeletePerBranch)
		}
		println "\nSummary:\n---------------"
		if (missingJobs) {
			for(ConcreteJob missingJob in missingJobs) {
				println "Creating missing job: ${missingJob.jobName} from ${missingJob.templateJob.jobName}"
//				jenkinsApi.cloneJobForBranch(jobPrefix, missingJob, createJobInView)
				if (startOnCreate) {
//					jenkinsApi.startJob(missingJob)
				}
			}
		}
		
		if (!noDelete && jobsToDelete) {
			println "Deleting deprecated jobs:\n\t${jobsToDelete.join('\n\t')}"
			jobsToDelete.each { String jobName ->
				//			jenkinsApi.deleteJob(jobName)
			}
		}
	}


	JenkinsApi initJenkinsApi() {
		if (!jenkinsApi) {
			assert jenkinsUrl != null
			if (dryRun) {
				println "DRY RUN! Not executing any POST commands to Jenkins, only GET commands"
				this.jenkinsApi = new JenkinsApiReadOnly(jenkinsServerUrl: jenkinsUrl)
			} else {
				this.jenkinsApi = new JenkinsApi(jenkinsServerUrl: jenkinsUrl)
			}

			if (jenkinsUser || jenkinsPassword) this.jenkinsApi.addBasicAuth(jenkinsUser, jenkinsPassword)
		}

		return this.jenkinsApi
	}

	GitApi initGitApi() {
		if (!gitApi) {
			assert gitUrl != null
			this.gitApi = new GitApi(gitUrl: gitUrl)
		}

		return this.gitApi
	}
}
