pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        // The Kotlin/JS plugin needs a Node (and Yarn) toolchain for `kotlinNodeJsSetup`.
        // It would register these itself, but FAIL_ON_PROJECT_REPOS above rejects
        // plugin-added repositories, so they are declared here instead of weakening the mode.
        ivy {
            name = "Node Distributions"
            setUrl("https://nodejs.org/dist")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy {
            name = "Yarn Distributions"
            setUrl("https://github.com/yarnpkg/yarn/releases/download")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

// npm-safe: the Kotlin/JS plugin uses this verbatim as the name in the aggregating
// build/js/package.json, and yarn rejects a name containing a space.
rootProject.name = "expense-tracker"
include(":app")
include(":shared")
