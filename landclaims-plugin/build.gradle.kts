plugins {
    `java-library`
}

dependencies {
    api(project(":landclaims-api"))

    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("net.milkbowl.vault:VaultAPI:1.7")

    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

tasks.jar {
    archiveBaseName.set("LandClaims")
    archiveVersion.set(project.version.toString())
}
