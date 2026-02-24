pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // Build the Gradle plugin from source — clone it if the checkout is missing.
    val pluginDir = java.io.File(file(".."), "android-clojure-plugin")
    if (!pluginDir.isDirectory) {
        println("Cloning android-clojure-plugin from https://github.com/clj-android/android-clojure-plugin.git ...")
        try {
            val proc = ProcessBuilder(
                "git", "clone", "--depth", "1",
                "https://github.com/clj-android/android-clojure-plugin.git",
                pluginDir.absolutePath
            ).redirectErrorStream(true).start()
            proc.inputStream.bufferedReader().forEachLine { println("  $it") }
            proc.waitFor()
        } catch (_: Exception) {
            println("WARNING: could not clone android-clojure-plugin — falling back to published artifacts")
        }
    }
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

// Clone a sibling repository from the clj-android GitHub organization if it is
// not already checked out.  Returns the directory, or null if cloning failed
// (in which case Gradle falls back to published artifacts from mavenLocal).
fun ensureSibling(parent: java.io.File, name: String): java.io.File? {
    val dir = java.io.File(parent, name)
    if (dir.isDirectory) return dir

    println("Cloning $name from https://github.com/clj-android/$name.git ...")
    return try {
        val proc = ProcessBuilder(
            "git", "clone", "--depth", "1",
            "https://github.com/clj-android/$name.git",
            dir.absolutePath
        ).redirectErrorStream(true).start()
        proc.inputStream.bufferedReader().forEachLine { println("  $it") }
        if (proc.waitFor() == 0) dir else {
            println("WARNING: failed to clone $name — falling back to published artifacts")
            null
        }
    } catch (_: Exception) {
        println("WARNING: could not clone $name — falling back to published artifacts")
        null
    }
}

// Build sibling projects from source when available.
// Missing repositories are cloned automatically from GitHub.
// Without these, artifacts must be published to mavenLocal first.
listOf("neko", "runtime-core", "runtime-repl", "clojure-patched").forEach { name ->
    ensureSibling(file(".."), name)?.let { includeBuild(it) }
}
