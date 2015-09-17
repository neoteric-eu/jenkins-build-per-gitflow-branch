package com.neoteric.jenkins

import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;

@ToString
@EqualsAndHashCode
class TemplateJob {
    String jobName
    String baseJobName
    String templateBranchName

    String jobNameForBranch(String branchName) {
        println "-----> no cranky jankins "+branchName
        // git branches often have a forward slash in them, but they make jenkins cranky, turn it into an underscore
        println "-----> branch name is "+branchName
        String safeBranchName = branchName.replaceAll('/', '_')
        println "-----? jenkins branch name is "+safeBranchName
        return "$baseJobName-$safeBranchName"
    }
    
    ConcreteJob concreteJobForBranch(String jobPrefix, String branchName) {
        ConcreteJob concreteJob = new ConcreteJob(templateJob: this, branchName: branchName, jobName: jobPrefix + '-' + jobNameForBranch(branchName) )
    }
}
