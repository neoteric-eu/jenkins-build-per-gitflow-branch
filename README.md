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

### Usage

[Jenkins Build Per Branch]:http://entagen.github.io/jenkins-build-per-branch/
[GitHub flow]:http://scottchacon.com/2011/08/31/github-flow.html
[Jenkins Git Plugin]:https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin
[Jenkins Gradle plugin]:https://wiki.jenkins-ci.org/display/JENKINS/Gradle+Plugin
