val core = project(":${rootProject.name}-core")

subprojects {
    if (version == "unspecified") {
        version = rootProject.version
    }

    val projectName = name.removePrefix("ability-")

    project.extra.apply {
        set("projectName", projectName)
        set("packageName", name.removePrefix("ability-").replace("-", ""))
        set("abilityName", name.removePrefix("ability-").split('-').joinToString(separator = "") { it.replaceFirstChar(Char::uppercase) })
    }

    dependencies {
        implementation(core)
        compileOnly("io.github.Shai0717:Kolor:1.0.0")
    }

    tasks {
        processResources {
            filesMatching("**/*.yml") {
                expand(project.properties)
            }
        }

        val paperJar = register<Jar>("paperJar") {
            archiveVersion.set("")
            archiveBaseName.set("${project.group}.${project.name.removePrefix("ability-")}")
            from(sourceSets["main"].output)

        }

        rootProject.tasks {
            named<Zip>("distribution") {
                dependsOn(paperJar)
                from(paperJar) { into("plugins/Psychics/abilities") }
            }
            register<DefaultTask>(projectName) {
                dependsOn(paperJar)
            }
        }
    }
}
