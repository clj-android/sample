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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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
}
