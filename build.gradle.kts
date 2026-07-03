import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`
    `maven-publish`
    embeddedKotlin("jvm")
    embeddedKotlin("plugin.serialization")
    id("com.diffplug.spotless") version "6.18.0"
    id("com.gradle.plugin-publish") version "2.1.1"
}

group = "me.modmuss50"
version =
    System.getenv("GITHUB_REF_NAME")
        ?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" }
    ?: System.getenv("GITHUB_SHA")
        ?.takeIf { it.isNotBlank() }
        ?.take(7)
    ?: "dev"
description = "The Mod Publish Plugin is a plugin for the Gradle build system to help upload artifacts to a range of common destinations."

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation(kotlin("test"))
    testImplementation("io.javalin:javalin:7.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
}

// Workaround https://github.com/gradle/gradle/issues/25898
tasks.withType(Test::class.java).configureEach {
    jvmArgs = listOf(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED"
    )
}

tasks.withType(JavaCompile::class.java).all {
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
}

tasks.jar {
    manifest {
        attributes(mapOf("Implementation-Version" to version))
    }
}

spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    kotlin {
        ktlint()
    }
}

gradlePlugin {
    website = "https://github.com/modmuss50/mod-publish-plugin"
    vcsUrl = "https://github.com/modmuss50/mod-publish-plugin"
    testSourceSet(sourceSets["test"])

    plugins.create("mod-publish-plugin") {
        id = "me.modmuss50.mod-publish-plugin"
        implementationClass = "me.modmuss50.mpp.MppPlugin"
        displayName = "Mod Publish Plugin"
        description = project.description
        version = project.version
        tags = listOf("minecraft", )
        compatibility {
            features {
                configurationCache = true
            }
        }
    }
}

fun replaceVersion(path: String) {
    var content = project.file(path).readText()

    content = content.replace("(version \").*(\")".toRegex(), "version \"${project.version}\"")// project.version.toString())

    project.file(path).writeText(content)
}

replaceVersion("README.md")
replaceVersion("docs/pages/getting_started.mdx")

// Custom/additional repository
providers.environmentVariable("MAVEN_URL")
    .orNull
    ?.takeIf(String::isNotBlank)
    ?.let { mavenUrl -> publishing {
        publications.create<MavenPublication>("pluginMaven") {
            artifactId = "mod-publish-plugin"
            pom {
                name.set(project.name)
                description.set(project.description)
                url.set("https://github.com/modmuss50/mod-publish-plugin")
                packaging = "jar"

                developers {
                    developer {
                        id.set("modmuss50")
                        url.set("https://github.com/modmuss50")
                    }
                    developer {
                        id.set("srnyx")
                        url.set("https://srnyx.com")
                        email.set("contact@srnyx.com")
                        timezone.set("America/New_York")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/modmuss50/mod-publish-plugin.git")
                    developerConnection.set("scm:git:ssh://github.com/modmuss50/mod-publish-plugin.git")
                    url.set("https://github.com/modmuss50/mod-publish-plugin")
                }
            }
        }

        repositories.maven {
            name = "srnyx"
            url = uri(mavenUrl)

            // Username/password
            val mavenName = providers.environmentVariable("MAVEN_NAME")
                .orNull
                ?.takeIf(String::isNotBlank)
            val mavenSecret = providers.environmentVariable("MAVEN_SECRET")
                .orNull
                ?.takeIf(String::isNotBlank)
            if (mavenName != null && mavenSecret != null) credentials {
                username = mavenName
                password = mavenSecret
            }
        } }
}
