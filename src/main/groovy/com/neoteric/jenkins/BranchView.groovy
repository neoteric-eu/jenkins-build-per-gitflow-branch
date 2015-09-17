package com.neoteric.jenkins

class BranchView {
    String templateJobPrefix
    String branchName

    public String getViewName() {
        return "$templateJobPrefix-$safeBranchName"
    }

    public String getSafeBranchName() {
        println "---->geting the safe branch name -------->"
        return branchName.replaceAll('/', '_')
    }


    public String toString() {
        return this.viewName
    }
}
