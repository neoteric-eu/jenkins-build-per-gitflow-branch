package com.entagen.jenkins

import groovy.transform.ToString;

@ToString
class ConcreteJob {
    TemplateJob templateJob
    String jobName
    String branchName
}
