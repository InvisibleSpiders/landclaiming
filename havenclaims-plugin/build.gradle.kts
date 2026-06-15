plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.1"
}

dependencies {
    api(project(":havenclaims-api"))

    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    compileOnly("dev.invisiblespiders.haven:haven-api:1.0.2")
    compileOnly("net.milkbowl.vault:VaultAPI:1.7")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("dev.invisiblespiders.haven:haven-api:1.0.2")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
    testCompileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    testRuntimeOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    testImplementation("net.kyori:adventure-api:4.26.1")
    testImplementation("net.kyori:adventure-text-minimessage:4.26.1")
    testImplementation("net.kyori:adventure-text-serializer-plain:4.26.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("HavenClaims")
    archiveVersion.set(project.version.toString())
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("HavenClaims")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
