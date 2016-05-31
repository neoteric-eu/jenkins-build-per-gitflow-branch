package com.neoteric.jenkins

/*
Bootstrap class that parses command line arguments, or system properties passed in by jenkins, and starts the jenkins-build-per-branch sync process
 */

class Main {
    public static final Map<String, Map<String, Object>> opts = [
            h  : [longOpt: 'help', required: false, args: 0, argName: 'help', description: "Print usage information - gradle flag -Dhelp=true"],
            j  : [longOpt: 'jenkins-url', required: true, args: 1, argName: 'jenkinsUrl', description: "Jenkins URL - gradle flag -DjenkinsUrl=<jenkinsUrl>"],
            u  : [longOpt: 'git-url', required: true, args: 1, argName: 'gitUrl', description: "Git Repository URL - gradle flag -DgitUrl=<gitUrl>"],
            p  : [longOpt: 'job-prefix', required: true, args: 1, argName: 'jobPrefix', description: "Job Prefix, - gradle flag -DjobPrefix=<jobPrefix>"],
            a  : [longOpt: 'template-job-prefix', required: true, args: 1, argName: 'templateJobPrefix', description: "Template Job Prefix, - gradle flag -DtemplatejobPrefix=<templateJobPrefix>"],
            d  : [longOpt: 'dry-run', required: false, args: 0, argName: 'dryRun', description: "Dry run, don't actually modify, create, or delete any jobs, just print out what would happen - gradle flag: -DdryRun=true"],
            i  : [longOpt: 'create-job-in-view', required: false, args: 1, argName: 'createJobInView', description: "Create new job in specified view. When using this suppress view creation as well (-DnoViews=true) - gradle flag -DcreateInView=nestedView/view"],
            s  : [longOpt: 'script-command', required: false, args: 1, argName: 'scriptCommand', description: "Script command to execute on remote server via Publish over SSH plugin"],
            k  : [longOpt: 'no-delete', required: false, args: 0, argName: 'noDelete', description: "Do not delete (keep) branches and views - gradle flag -DnoDelete=true"],
            usr: [longOpt: 'jenkins-user', required: false, args: 1, argName: 'jenkinsUser', description: "Jenkins username - gradle flag -DjenkinsUser=<jenkinsUser>"],
            pwd: [longOpt: 'jenkins-password', required: false, args: 1, argName: 'jenkinsPassword', description: "Jenkins password - gradle flag -DjenkinsPassword=<jenkinsPassword>"],
            sp : [longOpt: 'sonar-password', required: false, args: 1, argName: 'sonarPassword', description: "Sonar password - gradle flag -DsonarPassword=<sonarPassword>"],
            su : [longOpt: 'sonar-user', required: false, args: 1, argName: 'sonarUser', description: "Sonar username - gradle flag -DsonarUser=<sonarUser>"],
            surl : [longOpt: 'sonar-url', required: false, args: 1, argName: 'sonarUrl', description: "Sonar URL - gradle flag -DsonarUrl=<sonarUrl>"],
            bp: [longOpt: 'branch-prefix', required: false, args: 1, argName: 'branchPrefix', description: "Git Branch Prefix - gradle flag -DbranchPrefix=<branchPrefix>"]
    ]

    public static void main(String[] args) {
        Map<String, String> argsMap = parseArgs(args)
        showConfiguration(argsMap)
        JenkinsJobManager manager = new JenkinsJobManager(argsMap)
        manager.syncWithRepo()
    }

    public static Map<String, String> parseArgs(String[] args) {
        def cli = createCliBuilder()
        OptionAccessor commandLineOptions = cli.parse(args)

        // this is necessary as Gradle's command line parsing stinks, it only allows you to pass in system properties (or task properties which are basically the same thing)
        // we need to merge in those properties in case the script is being called from `gradle syncWithGit` and the user is giving us system properties
        Map<String, String> argsMap = mergeSystemPropertyOptions(commandLineOptions)

        if (argsMap.help) {
            cli.usage()
            System.exit(0)
        }

        if (argsMap.printConfig) {
            showConfiguration(argsMap)
            System.exit(0)
        }

        def missingArgs = opts.findAll { shortOpt, optMap ->
            if (optMap.required) return !argsMap."${optMap.argName}"
        }

        if (missingArgs) {
            missingArgs.each { shortOpt, missingArg -> println "missing required argument: ${missingArg.argName}" }
            cli.usage()
            System.exit(1)
        }

        return argsMap
    }

    public static createCliBuilder() {
        def cli = new CliBuilder(usage: "jenkins-build-per-branch [options]", header: 'Options, if calling from `gradle syncWithGit`, you need to use a system property format -D<argName>=value, ex: (gradle -DgitUrl=git@github.com:yourname/yourrepo.git syncWithGit):')
        opts.each { String shortOpt, Map<String, Object> optMap ->
            if (optMap.args) {
                cli."$shortOpt"(longOpt: optMap.longOpt, args: optMap.args, argName: optMap.argName, optMap.description)
            } else {
                cli."$shortOpt"(longOpt: optMap.longOpt, optMap.description)
            }
        }
        return cli
    }

    public static showConfiguration(Map<String, String> argsMap) {
        println "==============================================================="
        argsMap.each { k, v -> println " $k: ${formatValue(k, v)}" }
        println "==============================================================="
    }

    public static formatValue(String key, String value) {
        return (key == "jenkinsPassword") ? "********" : value
    }

    public static Map<String, String> mergeSystemPropertyOptions(OptionAccessor commandLineOptions) {
        Map<String, String> mergedArgs = [:]
        opts.each { String shortOpt, Map<String, String> optMap ->
            if (optMap.argName) {
                mergedArgs[optMap.argName] = commandLineOptions."$shortOpt" ?: System.getProperty(optMap.argName)
            }
        }
        return mergedArgs.findAll { k, v -> v }
    }
}
