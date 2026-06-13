plugins {
    `java-library`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
}
