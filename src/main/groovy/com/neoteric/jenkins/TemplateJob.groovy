package com.neoteric.jenkins

import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;

@ToString
@EqualsAndHashCode
class TemplateJob {
	String jobName
	String baseJobName
	String templateBranchName

	TemplateJob () {}

	TemplateJob(String jobName, String baseJobName, String templateBranchName) {
		this.jobName = jobName
		this.baseJobName = baseJobName
		this.templateBranchName = templateBranchName
	}

	String jobNameForBranch(String branchName) {
		// git branches often have a forward slash in them, but they make jenkins cranky, turn it into an underscore
		String safeBranchName = branchName.replaceAll('/', '-')
		println "\t\t\t\t jenkins branch name is "+safeBranchName
		return "$baseJobName-$safeBranchName"
	}

	ConcreteJob concreteJobForBranch(String jobPrefix, String branchName) {
		ConcreteJob concreteJob = new ConcreteJob(templateJob: this, branchName: branchName, jobName: jobPrefix + '-' + jobNameForBranch(branchName) )
	}
}
