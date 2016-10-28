package com.neoteric.jenkins


class SonarApiReadOnly extends SonarApi{

    @Override
    protected void delete(String entryConfig) {

        println "Sonar API - warning - DRY RUN - no changes will be applied to Sonar projects"

        def root = new XmlParser().parseText(entryConfig)
        def branchName = root.publishers."hudson.plugins.sonar.SonarPublisher".branch.text()

        String groupId = root.rootModule.groupId.text()

        String artifactId = root.rootModule.artifactId.text()

        StringBuilder sonarProject = new StringBuilder("")
        sonarProject.append(groupId).append(":").append(artifactId).append(":").append(branchName);

        println "Sonar API - project to delete: " + sonarProject
        println "Sonar API - warning - DRY RUN - skipped delete"
    }
}
