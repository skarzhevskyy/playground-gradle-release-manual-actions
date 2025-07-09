plugins {
    java
    application
    id("org.cyclonedx.bom") version "2.3.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("net.researchgate.release") version "3.1.0"
}

group = project.group
version = project.version

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.0")
}

application {
    mainClass.set("com.example.demo.DemoApplication")
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
        attributes("Main-Class" to "com.example.demo.DemoApplication")
    }
}

extensions.configure<net.researchgate.release.ReleaseExtension> {
    git {
        requireBranch.set("main")
        releaseCommitMessage.set("Release: v${project.version}")
        tagName.set("v${project.version}")
    }

    buildTasks.set(listOf("clean", "build"))
}

tasks.named("pushPostReleaseVersion") {
    enabled = false
}

tasks.withType<JavaCompile> {
    options.isDeprecation = true
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }
}

// Disable the default cyclonedxBom task: https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/596
tasks.named("cyclonedxBom") {
    enabled = false
}

// Example: gradle sbom; vk-sbom-diff sbom-1.json sbom.json
tasks.register("sbom", org.cyclonedx.gradle.CycloneDxTask::class) {
    setIncludeConfigs(listOf("runtimeClasspath"))
    setProjectType("application")
    setSchemaVersion("1.6")
    setDestination(project.file("."))
    setOutputName("sbom")
    setOutputFormat("json")
    setIncludeBomSerialNumber(false)
    setIncludeLicenseText(false)
}
