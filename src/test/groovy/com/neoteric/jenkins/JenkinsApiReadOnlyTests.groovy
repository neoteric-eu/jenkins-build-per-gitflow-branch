package com.neoteric.jenkins

import org.junit.Test
import org.apache.http.HttpStatus

import com.neoteric.jenkins.JenkinsApi;
import com.neoteric.jenkins.JenkinsApiReadOnly;

class JenkinsApiReadOnlyTests {
	
    @Test
	public void testAllReadOnlyPostsReturnOK() {
        JenkinsApi api = new JenkinsApiReadOnly(jenkinsServerUrl: "http://localhost:9090/jenkins")
        assert api.post("http://foo.com") == HttpStatus.SC_OK
    }
}
