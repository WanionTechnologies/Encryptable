plugins {
    `maven-publish`
    signing
}

publishing {
    publications {
        create<MavenPublication>("mavenPom") {
            // Explicitly set coordinates (inherit from root project)
            groupId = rootProject.group.toString()
            version = rootProject.version.toString()
            artifactId = "encryptable-starter"

            // Create a POM-only publication (no attached jar) that declares starter dependencies
            pom {
                name.set("Encryptable Starter")
                description.set("POM-only starter that pulls in Encryptable and Spring Data MongoDB dependencies for easy consumption by Spring Boot applications.")
                url.set("https://github.com/WanionTechnologies/Encryptable")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("wanion")
                        name.set("WanionCane")
                        email.set("contact@wanion.tech")
                        organization.set("Wanion Technologies")
                        organizationUrl.set("https://wanion.tech/")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/WanionTechnologies/Encryptable.git")
                    developerConnection.set("scm:git:ssh://git@github.com:WanionTechnologies/Encryptable.git")
                    url.set("https://github.com/WanionTechnologies/Encryptable")
                }
                // Add the dependencies that the starter should bring transitively
                withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    fun addDep(groupId: String, artifactId: String, version: String? = null, scope: String = "compile") {
                        val dep = dependenciesNode.appendNode("dependency")
                        dep.appendNode("groupId", groupId)
                        dep.appendNode("artifactId", artifactId)
                        if (!version.isNullOrBlank()) {
                            dep.appendNode("version", version)
                        }
                        dep.appendNode("scope", scope)
                    }

                    val kotlinVersion = rootProject.extra["kotlinVersion"] as String
                    val springBootVersion = rootProject.extra["springBootVersion"] as String
                    val springAspectsVersion = rootProject.extra["springAspectsVersion"] as String
                    val aspectjVersion = rootProject.extra["aspectjVersion"] as String
                    val hkdfVersion = rootProject.extra["hkdfVersion"] as String

                    // Kotlin
                    addDep("org.jetbrains.kotlin", "kotlin-stdlib", kotlinVersion)
                    addDep("org.jetbrains.kotlin", "kotlin-reflect", kotlinVersion)

                    // Spring Boot starters (explicit version to match root plugin)
                    addDep("org.springframework.boot", "spring-boot-starter-webmvc", springBootVersion)
                    addDep("org.springframework.boot", "spring-boot-starter-data-mongodb", springBootVersion)

                    // HKDF for key derivation
                    addDep("at.favre.lib", "hkdf", hkdfVersion)

                    // AspectJ / AOP
                    addDep("org.springframework", "spring-aspects", springAspectsVersion)
                    addDep("org.aspectj", "aspectjrt", aspectjVersion)
                    addDep("org.aspectj", "aspectjweaver", aspectjVersion)

                    // Encryptable
                    addDep("tech.wanion", "encryptable", rootProject.version.toString())
                }
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (rootProject.version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = project.findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                password = project.findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    // Use in-memory ASCII-armored keys from gradle.properties or environment variables
    val signingKey = project.findProperty("signing.key") as String? ?: System.getenv("SIGNING_KEY")
    val signingPassword = project.findProperty("signing.password") as String? ?: System.getenv("SIGNING_PASSWORD")
    if (signingKey != null && signingPassword != null)
        useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["mavenPom"])
}