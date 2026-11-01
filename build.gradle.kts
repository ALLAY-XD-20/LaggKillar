plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    id("dev.arbjerg.lavalink.gradle-plugin") version "1.0.15"
    `maven-publish`
}

group = "com.github.ALLAY-XD-20"
version = (System.getenv("VERSION") ?: "1.4.0")

lavalinkPlugin {
    name = "laggkillar"
    path = "dev.its2pawan.laggkillar"
    apiVersion = "4.0.0"
    serverVersion = "4.0.8"
    configurePublishing = false
}

repositories {
    mavenCentral()
    maven("https://maven.lavalink.dev/releases")
    maven("https://maven.lavalink.dev/snapshots")
    maven("https://jitpack.io")
}

kotlin { jvmToolchain(17) }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "LaggKillar"
            version = project.version.toString()
        }
    }
}
