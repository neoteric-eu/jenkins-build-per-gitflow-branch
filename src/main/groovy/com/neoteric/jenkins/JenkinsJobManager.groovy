package com.neoteric.jenkins

import java.util.regex.Pattern

class JenkinsJobManager {

	String templateJobPrefix
	String jobPrefix
	String gitUrl
	String jenkinsUrl
	String createJobInView
	String jenkinsUser
	String jenkinsPassword

	Boolean dryRun = false
	Boolean noDelete = false
	Boolean startOnCreate = false

	String developmentSuffix = "development-"
	String featureSuffix = "feature/"
	String hotfixSuffix = "hotfix-"
	String releaseSuffix = "release-"

	String templateDevelopmentSuffix = "development"
	String templateFeatureSuffix = "feature"
	String templateHotfixSuffix = "hotfix"
	String templateReleaseSuffix = "release"

	def branchSuffixMatch = [(templateDevelopmentSuffix) : developmentSuffix,
		(templateFeatureSuffix) : featureSuffix,
		(templateHotfixSuffix) : hotfixSuffix,
		(templateReleaseSuffix): releaseSuffix]

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

		List<TemplateJob> templateJobs = findRequiredTemplateJobs(allJobNames)
		println "-------------------------------------"
		println "Template Jobs:" + templateJobs

		List<String> jobsWithJobPrefix = allJobNames.findAll { jobName ->
			jobName.startsWith(jobPrefix + '-')
		}
		println "-------------------------------------"
		println "Jobs with provided prefix:" + jobsWithJobPrefix

		// create any missing template jobs and delete any jobs matching the template patterns that no longer have branches
		syncJobs(allBranchNames, jobsWithJobPrefix, templateJobs)

	}

	public List<TemplateJob> findRequiredTemplateJobs(List<String> allJobNames) {
		List<TemplateJob> templateJobs = new ArrayList<TemplateJob>()

		List<String> jobs = removeNonMatchingJobs(allJobNames)

		for(String jobName : allJobNames){

			int suffixStarts
			if(jobName.contains(templateDevelopmentSuffix)){
				suffixStarts = jobName.indexOf(templateDevelopmentSuffix)
				println "\n\tdev "+suffixStarts
			}
			else if(jobName.contains(templateFeatureSuffix)){
				suffixStarts = jobName.indexOf(templateFeatureSuffix)
				println "\n\tfeature "+suffixStarts
			}
			else if(jobName.contains(templateHotfixSuffix)) {
				suffixStarts = jobName.indexOf(templateHotfixSuffix)
				println "\n\thotfix "+suffixStarts
			}
			else if(jobName.contains(templateReleaseSuffix)) {
				suffixStarts = jobName.indexOf(templateReleaseSuffix)
				println "\n\trelease "+suffixStarts
			}
			else {
				continue;
			}

			int branchNameStarts = jobName.indexOf("_");
			String branchName = "";
			String templateName = "";

			if(branchNameStarts == -1){
				templateName = jobName.substring(suffixStarts,jobName.length());
			}
			else {
				templateName = jobName.substring(suffixStarts,branchNameStarts);
				branchName = jobName.substring(branchNameStarts+1,jobName.length());
			}

			println "\n\tjobName "+jobName
			println "\n\t index of prefix "+jobName.indexOf(jobPrefix)
			println "\n\tprefix length "+jobPrefix.length()
			int where = jobPrefix.length()+1
			println "\n\tprefix length and some "+where
			int begin = jobPrefix.length() + where
			println "\tbegin "+begin
			String baseName = jobName.substring(jobName.indexOf(jobPrefix)+jobPrefix.length()+1,suffixStarts-1);

			TemplateJob t = new TemplateJob(jobName,baseName,branchName);
			println "\tadded "+jobName
			templateJobs.add(t);
		}

		assert templateJobs?.size() > 0, "Unable to find any jobs matching template regex: $regex\nYou need at least one job to match the templateJobPrefix and templateBranchName (feature, hotfix, release) suffix arguments"
		return templateJobs
	}

	private List<String> removeNonMatchingJobs(List<String> allJobs){
		println "\tjobprefix "+jobPrefix
		Iterator iter = allJobs.iterator();

		while(iter.hasNext()){
			String jobName = iter.next()

			if(!jobName.contains(jobPrefix)){
				iter.remove()
			}//if
		}//while
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
				List<String> jobNamesPerBranch = jobNames.findAll{ it.endsWith(jobNameForBranch(branchToProcess,jobPrefix)) }
				println "-------> Job Names per branch:"
				jobNamesPerBranch.each { println "           $it" }
				List<ConcreteJob> missingJobsPerBranch = expectedJobsPerBranch.findAll { expectedJob ->
					!jobNamesPerBranch.any {it.contains(expectedJob.jobNameForBranch()) }
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
				jenkinsApi.cloneJobForBranch(jobPrefix, missingJob, createJobInView, gitUrl)
				jenkinsApi.startJob(missingJob)
			}
		}

		if (!noDelete && jobsToDelete) {
			println "Deleting deprecated jobs:\n\t${jobsToDelete.join('\n\t')}"
			jobsToDelete.each { String jobName ->
				jenkinsApi.deleteJob(jobName)
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

	String jobNameForBranch(String branchName, String baseJobName) {
		// git branches often have a forward slash in them, but they make jenkins cranky, turn it into an underscore
		String safeBranchName = branchName.replaceAll('/', '_')
		return "$baseJobName-$safeBranchName"
	}
}
