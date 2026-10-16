plugins {
    kotlin("jvm") version "2.1.21"
    application
}

val rsprotVersion = "1.0.0-ALPHA-20261001"

dependencies {
    implementation("net.rsprot:osrs-241-api:$rsprotVersion")
    // Codec/internal modules are runtime-only in the api POM; the server uses some types directly.
    for (module in listOf("desktop", "android", "internal", "model", "common", "shared")) {
        implementation("net.rsprot:osrs-241-$module:$rsprotVersion")
    }
    implementation("com.michael-bull.kotlin-inline-logger:kotlin-inline-logger:1.0.6")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.apache.logging.log4j:log4j-core:2.25.0")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.0")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.addAll("kotlin.ExperimentalUnsignedTypes", "kotlin.ExperimentalStdlibApi")
    }
}

application {
    mainClass.set("rsps.MainKt")
    applicationDefaultJvmArgs = listOf("-XX:+UseZGC", "-Xmx1g")
}
