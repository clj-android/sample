# Clojure Android Sample

A sample Android app built with Clojure, demonstrating AOT compilation, the neko UI DSL, and REPL-driven development via nREPL.

The app has two activities:

- **Neko UI Demo** (launcher) — a declarative UI built with neko's Clojure DSL, with a counter and buttons. The UI can be hot-reloaded from an nREPL session.
- **Validation Tests** — runs diagnostics on the Clojure runtime, classloader, and nREPL infrastructure. Provides controls to start/stop the nREPL server.

## Prerequisites

- **JDK 17+** (21 recommended; 26 may have compatibility issues)
- **Android SDK** with platform API 35 installed. Set `sdk.dir` in `local.properties` or the `ANDROID_HOME` environment variable to point to your SDK installation.

## Dependencies

This app depends on several libraries from the [clj-android](https://github.com/clj-android) organization:

| Dependency | Role |
|---|---|
| [android-clojure-plugin](https://github.com/clj-android/android-clojure-plugin) | Gradle plugin — AOT-compiles Clojure sources and wires them into the Android build |
| [neko](https://github.com/clj-android/neko) | Idiomatic Clojure wrappers for Android APIs (UI DSL, activities, logging) |
| [runtime-core](https://github.com/clj-android/runtime-core) | Core Android runtime support (ClojureApp, ClojureActivity base classes) |
| [runtime-repl](https://github.com/clj-android/runtime-repl) | nREPL server with on-device DEX compilation for REPL-driven development |
| [clojure-patched](https://github.com/clj-android/clojure-patched) | Clojure 1.12.0 with Android-aware classloader (used automatically in debug builds) |

Dependencies are resolved automatically: on the first build, `settings.gradle.kts` clones them from GitHub into `build/deps/` and includes them as Gradle composite builds. No manual setup is required.

If you prefer to install dependencies to your local Maven repository instead of using composite builds:

```bash
./gradlew publishDepsToMavenLocal
```

This cleans, builds, and publishes all five dependencies in the correct order. If cloning fails (e.g. no network), you can clone the repositories manually into `build/deps/`:

```bash
mkdir -p build/deps
for repo in android-clojure-plugin neko runtime-core runtime-repl clojure-patched; do
  git clone https://github.com/clj-android/$repo.git build/deps/$repo
done
```

## Building

```bash
# Debug APK (includes nREPL server)
./gradlew assembleDebug

# Release APK (AOT-only, no REPL infrastructure)
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/`.

## Running

Install and launch on a connected device or emulator:

```bash
./gradlew installDebug
adb shell am start -n com.example.clojuredroid/.MainActivity
```

## Connecting to the nREPL

Debug builds start an nREPL server on port 7888. Forward the port and connect:

```bash
adb forward tcp:7888 tcp:7888
```

Then connect from any nREPL client (Calva, CIDER, `lein repl :connect`, etc.) to `localhost:7888`.

Example — hot-reload the UI from the REPL:

```clojure
(require '[com.example.clojuredroid.main-activity :as ui])

(reset! ui/ui-tree*
  [:linear-layout {:orientation :vertical :padding [32 32 32 32]}
   [:text-view {:text "Modified from REPL!" :text-size [24 :sp]}]])

(ui/reload-ui!)
```

## Project structure

```
app/
  src/main/
    clojure/        Clojure source (AOT-compiled into the APK)
    java/           Java source (activities)
    res/            Android resources
    AndroidManifest.xml
  build.gradle.kts  App-level build config
build.gradle.kts    Top-level plugin declarations
settings.gradle.kts Repository and composite build configuration
```

## License

This sample app is released under [CC0 1.0](LICENSE). The Gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) are from the Gradle project and licensed under the Apache License 2.0.
