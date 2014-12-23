# Jenkins Build Per Git Flow Branch
This script will allow you to keep your Jenkins jobs in sync with your Git repository (following Git Flow branching model).

### Genesis
This is a variation of solution we found. Hence, the credit for the idea and initial implementation goes to Entagen and theirs [Jenkins Build Per Branch]. They explained it nicely, so it'a advisable to take a look there. As stated, Entagen's version would suit better in a [GitHub flow] convention. Our need is to have three different templates for each of the dynamic Git Flow branches: features, releases, hotfixes and to sync them all in one 'scanning session' (single Jenkins sync job execution). I found it impossible using the original solution.
So, we reused the concept of this nice script, but replaced the synchronisation logic with what suited us better.

### Installation
Requirements are the same for both script versions:
- [Jenkins Git Plugin]
- [Jenkins Gradle plugin]
- The git command line app also must be installed (this is already a requirement of the Jenkins Git Plugin), and it must be configured with credentials (likely an SSH key) that has authorization rights to the git repository
- The best idea is to clone / fork this repository for your own usage (makes sure that nothing is going to happen with the script). However, you can still use ours if you like.

###Naming convention
To make this script work properly, job names must follow few rules:

Template jobs should follow
`<templateJobPrefix>-<jobName>-<branchName>` name, where:
- *templateJobPrefix* - a prefix which distinguish particular type of template (one template type can be reused among several projects
- *jobName* - name of your Jenkins job purpose, ex. build etc. 
- *branchName* - one of the 3 Git Flow branch types: *feature*, *release*, *hotfix*

Regular jobs should follow similar pattern `<jobPrefix>-<jobName>-<branchName>`, where:
- *jobPrefix* - is a prefix which distinguish particular project
- for *jobName* and *branchName* apply the same rule as for templates 

Git Flow branches should start with *feature-*, *hotfix-*, *release-* prefixes. It is because, Jenkins is having hard time with slashes (i.e. *feature/*, *hotfix/*, *release/*). There are some workarounds (substituting '/' with and underscore for a jenkins job name - take a look into the [code of Entagen version]), but we were fine with this trade off. 

### Usage
Usage is also very similiar to the original, but let me retrace the steps:
##### 1. Create Jenkins synchronization job
The whole idea is to have a single Jenkins job which executes periodically, checks Git repository and creates / removes Jenkins jobs for each of the Git Flow dynamic branch (other than master nad development).
> **Note**: If no template for particular branch / job is available, branch will be ignored (job won't be created nor deleted).

- Create new "*Freestyle project*" kind of Jenkins job.
- Name it accordingly, ex. ProjectName-SyncJobs.
- For Git URL provide this script location (or your forked / cloned one): *git@github.com:neoteric-eu/jenkins-build-per-gitflow-branch.git*
- Set appropriate branch to build (ours is *origin/master*)
- Make sure it's triggered periodically (ex. every 5 minutes: **H/5 \* \* \* \***)
- Add a build step "*Invoke Gradle script*" and set it's *Tasks* field to **syncWithRepo**
- Provide script parameters (explained below) in *Switches* box


> **Important note from Entagen site**: This job is potentially destructive as it will delete old feature branch jobs for feature branches that no longer exist. It's strongly recommended that you back up your jenkins jobs directory before running, just in case. Another good alternative would be to put your jobs directory under git version control. Ignore workspace and builds directories and just about everything can be added. Commit periodocally and if something bad happens, revert back to the last known good version.

##### 2. Add script parameters (provided in Switches box)
- `-DjenkinsUrl` URL of the Jenkins.You should be able to append api/json to the URL to get JSON feed.
- `-DjenkinsUser` Jenkins HTTP basic authorization user name. (optional)
- `-DjenkinsPasswrd` Jenkins HTTP basic authorization password. (optional)
- `-DgitUrl` URL of the Git repository to make the synchronization against.
- `-DdryRun` Pass this flag with any value and it won't make any changes to Jenkins (preview mode). It is recommended to use dry run until everything is set up correctly. (optional)
- `-DtemplateJobPrefix` Prefix name of template jobs to use
- `-DjobPrefix` Prefix name of project jobs to create
- `-DcreateJobInView` If you want the script to create the job in a view provide the view name here. It also support nested views, just serparate them with slash '/', ex. *view/nestedview*
- `-DnoDelete` pass this flag with *true* value to avoid removing obsolete jobs (with no corresponding git branch) (optional)

Sample parameters configuration:
```
-DjenkinsUrl=http://myjenkinshost.com:8080/
-DjenkinsUser=username
-DjenkinsPassword=password
-DgitUrl=git@githost.com/project.git
-DtemplateJobPrefix=SimpleJarTemplate
-DjobPrefix=ProjectOne
-DcreateJobInView=ProjectOne
```

##### 3. Templates


[Jenkins Build Per Branch]:http://entagen.github.io/jenkins-build-per-branch/
[GitHub flow]:http://scottchacon.com/2011/08/31/github-flow.html
[Jenkins Git Plugin]:https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin
[Jenkins Gradle plugin]:https://wiki.jenkins-ci.org/display/JENKINS/Gradle+Plugin
[code of Entagen version]:https://github.com/entagen/jenkins-build-per-branch/blob/master/src/main/groovy/com/entagen/jenkins/TemplateJob.groovy
