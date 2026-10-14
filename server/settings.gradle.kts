rootProject.name = "rsps-test-server"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// RSProt built from source (pinned commit + rsprot.patch) so the Android codecs can live in it.
// Gradle substitutes the net.rsprot:* dependencies with these projects automatically.
includeBuild("rsprot")
