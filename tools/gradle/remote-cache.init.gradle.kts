// Gradle init script for the build containers (tools/gradle-cache.sh; passed with -I).
// Remote build cache only: the shared build cache node, push on; the local cache off (it would live
// in the container's own Gradle home and only duplicate the node). The URL is on the private docker
// network `gradle-cache`; GRADLE_REMOTE_CACHE_URL overrides it.
// Test tasks are never cached: a gate's tests always run (integration reads .data, which Gradle
// doesn't track as a test input, so a cached result could be stale for another branch).
gradle.settingsEvaluated {
    buildCache {
        local { isEnabled = false }
        remote<HttpBuildCache> {
            url = uri(System.getenv("GRADLE_REMOTE_CACHE_URL") ?: "http://gradle-cache-node:5071/cache/")
            isPush = true
            isAllowInsecureProtocol = true
        }
    }
}
gradle.allprojects {
    tasks.withType<Test>().configureEach {
        outputs.doNotCacheIf("tests always run (tools/gradle/remote-cache.init.gradle.kts)") { true }
    }
}
// qaCacheToolsClasspath: builds or-cache's runtime classpath (what freshCache/buildCache run on)
// without running them, so a gate compiles in one Gradle run and runs the cache tools in the next.
// In one run, the daemon after compiling (~2.3 GB) plus the cache tool's JVM (-Xmx2600m) overran
// the 4 GB container (OOM-killed daemon).
gradle.allprojects {
    if (path == ":or-cache") {
        plugins.withId("java") {
            tasks.register("qaCacheToolsClasspath") {
                group = "qa"
                description = "Builds or-cache's runtime classpath (qa.sh gate)"
                dependsOn(project.extensions.getByType(SourceSetContainer::class.java).getByName("main").runtimeClasspath)
            }
        }
    }
}
// -Pqa.testMaxHeap=<size> (qa.sh dev: 1536m in the 4 GB dev container) caps the test JVMs' heap;
// registered after the build's own conventions (afterEvaluate), so it wins over their 2048m.
gradle.allprojects {
    val testHeap = findProperty("qa.testMaxHeap")?.toString()
    if (testHeap != null) {
        afterEvaluate { tasks.withType<Test>().configureEach { maxHeapSize = testHeap } }
    }
}
