plugins {
    id("com.android.application")
    id("com.goodanser.clj-android.android-clojure")
}

android {
    namespace = "com.example.clojuredroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.clojuredroid"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets["test"].java.srcDirs("src/test/java")
    sourceSets["test"].resources.srcDirs("src/test/clojure")

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            // Our Android-compatible stubs in runtime-repl shadow nREPL's originals
            pickFirsts += listOf("nrepl/socket.clj", "nrepl/socket/dynamic.clj")
        }
    }
}

clojureOptions {
    warnOnReflection.set(true)
}

dependencies {
    implementation("org.clojure:clojure:1.12.0")
    implementation("com.goodanser.clj-android:neko:5.0.0-SNAPSHOT")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    testImplementation("org.clojure:clojure:1.12.0")
    testImplementation("org.clojure:spec.alpha:0.5.238")
    testImplementation("org.clojure:core.specs.alpha:0.4.74")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.test:core:1.6.1")
}

tasks.withType<Test> {
    testLogging {
        events("failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
