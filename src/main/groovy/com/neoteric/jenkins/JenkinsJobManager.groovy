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

	String templateDevelopmentSuffix = "development"
	String templateFeatureSuffix = "feature"
	String templateHotfixSuffix = "hotfix"
	String templateReleaseSuffix = "release"
	List<String> missingJobs
	List<String> jobsToDelete
	def jobNameToBranchName

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
		println "\n-------------------------------------"
		println "All branch names:" + allBranchNames +"\t"

		List<String> allJobNames = jenkinsApi.jobNames
		println "\n-------------------------------------"
		println "All job names:" + allJobNames +"\t"

		missingJobs = [];
		jobsToDelete = [];
		jobNameToBranchName = [:]
		// create any missing template jobs and delete any jobs matching the template patterns that no longer have branches
		syncJobs(allBranchNames, allJobNames)
		addMissingJobs()
		deleteJobs()
	}

	void syncJobs(List<String> allBranchNames, List<String> allJobNames){
		//first check for missing jobs
		List<String> jobsWithJobPrefix = allJobNames.findAll {
			jobName ->
			jobName.startsWith(jobPrefix + '-')
		}
		println "\n-------------------------------------"
		println "Jobs with provided prefix:" + jobsWithJobPrefix +"\t"
		List<String> jenkinsBranchNames = new ArrayList<String>()

		//first check for branches that don't have jobs yet and add them
		for(String branch:allBranchNames){
			String trueName = jobNameForBranch(branch,jobPrefix+"-"+templateJobPrefix);
			jenkinsBranchNames.add(trueName)
			if(!allJobNames.contains(trueName)){
				//add job
				missingJobs.add(trueName)
				jobNameToBranchName[trueName] = branch
			}
		}

		//then check for jobs that don't have branches anymore and need to be deleted
		for(String job:allJobNames){
			if(!jenkinsBranchNames.contains(job)){
				//delete job
				jobsToDelete.add(job)
			}
		}
	}

	void addMissingJobs(){
		for(String job:missingJobs){
			String templateJobName = jobPrefix+"-"+templateJobPrefix

			if(job.contains(templateJobName+"-"+templateDevelopmentSuffix)){
				templateJobName = templateJobName + "-" + templateDevelopmentSuffix
			}
			else if(job.contains(templateJobName+"-"+templateFeatureSuffix)){
				templateJobName = templateJobName + "-" + templateFeatureSuffix
			}
			else if(job.contains(templateJobName+"-"+templateHotfixSuffix)){
				templateJobName = templateJobName + "-" + templateHotfixSuffix
			}
			else if(job.contains(templateJobName+"-"+templateReleaseSuffix)){
				templateJobName = templateJobName + "-" + templateReleaseSuffix
			}
			else {
				//throw an error because a template job for this branch doesn't exist
			}

			String branchName = jobNameToBranchName[job]

			println "Creating missing job: ${job} from ${templateJobName}"

			jenkinsApi.cloneJobForBranch(templateJobName, missingJob, branchName, createJobInView, gitUrl)
			jenkinsApi.startJob(templateJobName, missingJob)
		}
	}

	void deleteJobs(){
		if (!noDelete && jobsToDelete) {
			println "Deleting deprecated jobs:\n\t${jobsToDelete.join('\n\t')}"
			jobsToDelete.each {
				String jobName ->
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
			String safeBranchName = branchName.replaceAll('/', '-')
			return "$baseJobName-$safeBranchName"
		}
	}
