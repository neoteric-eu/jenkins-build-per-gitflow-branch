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

	String developmentSuffix = "development"
	String featureSuffix = "feature/"
	String hotfixSuffix = "hotfix/"
	String releaseSuffix = "release"

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
		println "\n-------------------------------------"
		println "All branch names:" + allBranchNames +"\t"

		List<String> allJobNames = jenkinsApi.jobNames
		println "\n-------------------------------------"
		println "All job names:" + allJobNames +"\t"

		List<TemplateJob> templateJobs = findRequiredTemplateJobs(allJobNames);
		println "\n-------------------------------------"
		println "Template Jobs:" + templateJobs +"\t"

		List<String> jobsWithJobPrefix = allJobNames.findAll {
			jobName ->
			jobName.startsWith(jobPrefix + '-')
		}
		println "\n-------------------------------------"
		println "Jobs with provided prefix:" + jobsWithJobPrefix +"\t"

		// create any missing template jobs and delete any jobs matching the template patterns that no longer have branches
		syncJobs(allBranchNames, jobsWithJobPrefix, templateJobs)

	}


	public List<TemplateJob> findRequiredTemplateJobs(List<String> allJobNames) {
		String regex = /^($templateJobPrefix)-(.*)-($templateFeatureSuffix|$templateReleaseSuffix|$templateHotfixSuffix)$/

		List<TemplateJob> templateJobs = allJobNames.findResults {
			String jobName ->

			TemplateJob templateJob = null
			jobName.find(regex) {
				full, templateName, baseJobName, branchName ->
				templateJob = new TemplateJob(jobName: full, baseJobName: baseJobName, templateBranchName: branchName)
			}
			return templateJob
		}

		assert templateJobs?.size() > 0, "Unable to find any jobs matching template regex: $regex\nYou need at least one job to match the templateJobPrefix and templateBranchName (feature, hotfix, release) suffix arguments"
		return templateJobs
	}

		/*public List<TemplateJob> findRequiredTemplateJobs(List<String> allJobNames, String baseName) {
		 String regex = /^($templateJobPrefix)-(.*)-($templateFeatureSuffix|$templateReleaseSuffix|$templateHotfixSuffix)$/
		 List<TemplateJob> templateJobs = new ArrayList<TemplateJob>()
		 List<String> jobs = removeNonMatchingJobs(allJobNames)
		 for(String jobName : allJobNames){
		 int suffixStarts
		 if(jobName.contains(templateDevelopmentSuffix)){
		 suffixStarts = jobName.indexOf(templateDevelopmentSuffix)
		 //println "\n\tdev "+suffixStarts
		 }
		 else if(jobName.contains(templateFeatureSuffix)){
		 suffixStarts = jobName.indexOf(templateFeatureSuffix)
		 //println "\n\tfeature "+suffixStarts
		 }
		 else if(jobName.contains(templateHotfixSuffix)) {
		 suffixStarts = jobName.indexOf(templateHotfixSuffix)
		 //println "\n\thotfix "+suffixStarts
		 }
		 else if(jobName.contains(templateReleaseSuffix)) {
		 suffixStarts = jobName.indexOf(templateReleaseSuffix)
		 //println "\n\trelease "+suffixStarts
		 }
		 else {
		 continue;
		 }
		 String branchName = jobName.substring(suffixStarts,jobName.length());
		 //	println "\n\tjobName "+jobName
		 //println "\n\t index of prefix "+jobName.indexOf(jobPrefix)
		 //println "\n\tprefix length "+jobPrefix.length()
		 //int where = jobPrefix.length()+1
		 //println "\n\tprefix length and some "+where
		 //int begin = jobPrefix.length() + where
		 //println "\tbegin "+begin
		 TemplateJob t = new TemplateJob(jobName,baseName,branchName);
		 println "\tadded "+jobName
		 templateJobs.add(t);
		 }
		 assert templateJobs?.size() > 0, "Unable to find any jobs matching template regex: $regex\nYou need at least one job to match the templateJobPrefix and templateBranchName (development, feature, hotfix, release) suffix arguments"
		 return templateJobs
		 }*/

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

			println "tempjobs size is $templateJobs.size()"
			println "all branches size is $allBranchNames.size()"
			def templateJobsByBranch = templateJobs.groupBy({
				template -> template.templateBranchName
			})
			println "size is $templateJobsByBranch"

			List<ConcreteJob> missingJobs = [];
			List<String> jobsToDelete = [];

			templateJobsByBranch.keySet().each {
				templateBranchToProcess ->
				println "\tChecking $templateBranchToProcess branches"
				List<String> branchesWithCorrespondingTemplate = allBranchNames.findAll {
					branchName ->
					println "\t\tbranch name is $branchName\n"
					branchName.startsWith(branchSuffixMatch[templateBranchToProcess])
				}

				println "\tFound corresponding branches: $branchesWithCorrespondingTemplate"
				branchesWithCorrespondingTemplate.each {
					branchToProcess ->

					println "\t\tProcessing branch: $branchToProcess"

					List<ConcreteJob> expectedJobsPerBranch = templateJobsByBranch[templateBranchToProcess].collect {
						TemplateJob templateJob ->
						templateJob.concreteJobForBranch(jobPrefix, branchToProcess)
					}

					println "\t\t\tExpected jobs for : "+jobNameForBranch(branchToProcess,jobPrefix)

					expectedJobsPerBranch.each {
						println "\t\t\t$it"
					}
					List<String> jobNamesPerBranch = jobNames.findAll{
						it.contains(jobNameForBranch(branchToProcess,jobPrefix))
					}

					println "\t\tJob Names per branch:"

					jobNamesPerBranch.each {
						println "           $it"
					}
					List<ConcreteJob> missingJobsPerBranch = expectedJobsPerBranch.findAll {
						expectedJob ->
						!jobNamesPerBranch.any {
							it.contains(expectedJob.jobNameForBranch())
						}
					}

					println "\n\t\tMissing jobs:"

					missingJobsPerBranch.each {
						println "\t\t\t$it"
					}
					missingJobs.addAll(missingJobsPerBranch)
				}

				List<String> deleteCandidates = jobNames.findAll {
					it.contains(branchSuffixMatch[templateBranchToProcess])
				}
				List<String> jobsToDeletePerBranch = deleteCandidates.findAll {
					candidate ->
					!branchesWithCorrespondingTemplate.any {
						candidate.endsWith(it)
					}
				}

				println "\n\tJobs to delete:"
				jobsToDeletePerBranch.each {
					println "         $it"
				}
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
