import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.16-SNAPSHOT"
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

version = project.property("mod_version")!!
group = project.property("maven_group")!!

loom {
    mods {
        register("chiyoko-player") {
            sourceSet(sourceSets.main.get())
        }
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

repositories {
    maven("https://maven.latticg.com/") {
        name = "LattiCG"
    }
}

dependencies {
    implementation("com.google.guava:guava:33.0.0-jre")

    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:1.21.11+build.6:v2")

    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("fabric_kotlin_version")}")

    include(implementation("com.seedfinding:latticg:1.07")!!)
}

tasks.processResources {
    inputs.property("version", version)

    val minecraftVersion = project.property("minecraft_version")
    val loaderVersion = project.property("loader_version")
    val kotlinLoaderVersion = project.property("fabric_kotlin_version")

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to version,
                "minecraft_version" to minecraftVersion,
                "loader_version" to loaderVersion,
                "kotlin_loader_version" to kotlinLoaderVersion
            )
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    inputs.property("projectName", project.name)

    from("LICENSE") {
        rename { "${it}_${project.name}" }
    }
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}