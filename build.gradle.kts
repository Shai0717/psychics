plugins {
    kotlin("jvm") version "2.3.20"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

allprojects {
    repositories {
        mavenCentral()
    }

    tasks {
        withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class) {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
            }
        }
    }
}

tasks.register<Zip>("distribution") {
    archiveBaseName.set("Psychics-26.3")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    dependsOn(":psychics-core:paperPluginJar", gradle.includedBuild("Kolor").task(":jar"))
    from("Kolor/build/libs/Kolor-1.0.0.jar") { into("plugins"); rename { "Kolor.jar" } }
    from(project(":psychics-core").layout.buildDirectory.file("libs/Psychics.jar")) { into("plugins") }
    from("examples") { into("plugins/Psychics/psychics") }
    from("LICENSE.md")
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    dependencies {
        compileOnly("io.papermc.paper:paper-api:26.3.build.254-stable")

        implementation(kotlin("stdlib"))

        testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.2")
        testImplementation("org.mockito:mockito-core:3.6.28")
    }
}
