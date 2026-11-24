dependencies {
    compileOnly("io.github.Shai0717:Kolor:1.0.0")
}

tasks {
    processResources {
        filesMatching("**/*.yml") {
            expand(project.properties)
        }
    }

    register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }

    register<Jar>("paperPluginJar") {
        archiveVersion.set("")
        archiveBaseName.set("Psychics")
        from(sourceSets["main"].output)
    }
}
