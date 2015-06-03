package com.neoteric.jenkins


class SonarApiReadOnly extends SonarApi{

    @Override
    protected void delete(String entryConfig) {

        Println "Sonar API - warning - DRY RUN - no changes will be applied to Sonar projects"

        def root = new XmlParser().parseText(entryConfig)
        def branchName = root.publishers."hudson.plugins.sonar.SonarPublisher".branch.text()

        String groupId = root.rootModule.groupId.text()

        String artifactId = root.rootModule.artifactId.text()

        StringBuilder sonarProject = new StringBuilder("/api/projects/")
        sonarProject.append(groupId).append(":").append(artifactId).append(":").append(branchName);

        println "Sonar API - path to delete: " + sonarProject
        Println "Sonar API - warning - DRY RUN - skipped delete"
    }
}
