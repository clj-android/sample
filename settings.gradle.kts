pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // Build the Gradle plugin from source when the sibling checkout exists
    val pluginDir = file("../android-clojure-plugin")
    if (pluginDir.isDirectory) {
        includeBuild(pluginDir)
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven { url = uri("https://clojars.org/repo") }
    }
}

rootProject.name = "clojure-android-sample"
include(":app")

// Build sibling projects from source when their checkouts exist.
// Without these, artifacts must be published to mavenLocal first.
listOf("neko", "runtime-core", "runtime-repl", "clojure-patched").forEach { name ->
    val dir = file("../$name")
    if (dir.isDirectory) {
        includeBuild(dir)
    }
}
