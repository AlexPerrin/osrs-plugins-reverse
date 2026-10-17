// Gradle init script (tools/multiclient/desktop-build.sh): exports RSProx's root runtime classpath
// (proxy + GUI + deps) into build/osrs-test-runtime, for the headless launcher on Windows.
allprojects {
    if (this == rootProject) {
        afterEvaluate {
            tasks.register<Copy>("osrsTestRuntime") {
                from(configurations.getByName("runtimeClasspath"))
                into(layout.buildDirectory.dir("osrs-test-runtime"))
            }
        }
    }
}
