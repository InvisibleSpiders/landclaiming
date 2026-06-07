plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.1"
}

dependencies {
    api(project(":landclaims-api"))

    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    compileOnly("net.milkbowl.vault:VaultAPI:1.7")

    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("LandClaims")
    archiveVersion.set(project.version.toString())
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("LandClaims")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
