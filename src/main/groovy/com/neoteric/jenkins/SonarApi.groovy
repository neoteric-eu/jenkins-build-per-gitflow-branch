package com.neoteric.jenkins

import groovyx.net.http.RESTClient
import org.apache.http.HttpRequest
import org.apache.http.HttpRequestInterceptor
import org.apache.http.client.HttpResponseException
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.protocol.HttpContext

class SonarApi {

    String sonarServerUrl
    RESTClient restClient
    HttpRequestInterceptor requestInterceptor

    public void setSonarServerUrl(String sonarServerUrl) {
        if (!sonarServerUrl.endsWith("/")) sonarServerUrl += "/"
        this.sonarServerUrl = sonarServerUrl
        this.restClient = new RESTClient(sonarServerUrl)

        println ("Sonar API - registered restClient with " + sonarServerUrl)
    }

    public void addBasicAuth(String sonarServerUser, String sonarServerPassword) {
        println "Sonar API - use basic authentication"

        this.requestInterceptor = new HttpRequestInterceptor() {
            void process(HttpRequest httpRequest, HttpContext httpContext) {
                def auth = sonarServerUser + ':' + sonarServerPassword
                httpRequest.addHeader('Authorization', 'Basic ' + auth.bytes.encodeBase64().toString())
            }
        }

        this.restClient.client.addRequestInterceptor(this.requestInterceptor)
    }

    protected void delete(String entryConfig) {

        def root = new XmlParser().parseText(entryConfig)
        def branchName = root.publishers."hudson.plugins.sonar.SonarPublisher".branch.text()

        String groupId = root.rootModule.groupId.text()

        String artifactId = root.rootModule.artifactId.text()

        StringBuilder sonarProject = new StringBuilder("/api/projects/")
        sonarProject.append(groupId).append(":").append(artifactId).append(":").append(branchName);

        println "Sonar API - path to delete: " + sonarProject

        try {
            restClient.delete(path: sonarProject)
        } catch (HttpResponseException e) {
            println "Sonar API - Error: $e.statusCode"
        }

    }
}
