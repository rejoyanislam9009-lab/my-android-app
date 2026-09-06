pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://github.com/jitsi/jitsi-maven-repository/raw/master/releases")
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "GlobalCall"
include(":app")
