package com.neoteric.jenkins

import groovyx.net.http.RESTClient
import org.apache.http.HttpRequestInterceptor
import org.apache.http.conn.HttpHostConnectException

class SonarApi {

    String sonarServerUrl
    String sonarServerUser
    String sonarServerPassword
    RESTClient restClient
    HttpRequestInterceptor requestInterceptor

    public void setSonarServerUrl(String sonarServerUrl) {
        if (!sonarServerUrl.endsWith("/")) sonarServerUrl += "/"
        this.sonarServerUrl = sonarServerUrl
        this.restClient = new RESTClient(sonarServerUrl)

        println "Sonar API - registered restClient with " sonarServerUrl
    }

    protected Integer delete(String entryConfig) {

        def branchName = root.publishers."hudson.plugins.sonar.SonarPublisher".branch.text()

        String groupId = root.rootModule.groupId.text()

        String artifactId = root.rootModule.artifactId.text()

        StringBuilder sonarProject = new StringBuilder("/api/projects/")
        sonarProject.append(groupId).append(":").append(artifactId).append(":").append(branchName);

        println "Sonar API - path to delete: " + sonarProject

        restClient.auth.basic sonarServerUser, sonarServerPassword

        try {
            def response = restClient.delete(path: sonarProject)
        } catch (HttpHostConnectException ex) {
            println "Unable to connect to sonar host: $sonarServerUrl"
            throw ex;
        }

        assert response.status < 400
        return response.status
    }
}
