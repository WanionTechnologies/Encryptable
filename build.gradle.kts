import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.gradle.jvm.tasks.Jar
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

plugins {
    kotlin("jvm") version "2.2.21" // Kotlin JVM plugin
    kotlin("plugin.spring") version "2.2.21" // Kotlin Spring plugin
    id("org.springframework.boot") version "4.0.0" // Spring Boot plugin
    id("io.spring.dependency-management") version "1.1.7" // Spring Dependency Management
    id("io.freefair.aspectj.post-compile-weaving") version "9.0.0"
    id("maven-publish") // Required for publishing block
    id("signing") // Required for Maven Central
    id("org.jetbrains.dokka") version "1.9.20"
}

group = "tech.wanion"
version = "1.0.5"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
    withSourcesJar() // Publish sources JAR
}

repositories {
    mavenLocal()
	mavenCentral()
}

// Version variables from gradle.properties
val kotlinVersion: String by project
val springBootVersion: String by project
val springAspectsVersion: String by project
val aspectjVersion: String by project
val hkdfVersion: String by project

// Extra properties for use in subprojects
extra["kotlinVersion"] = kotlinVersion
extra["springBootVersion"] = springBootVersion
extra["springAspectsVersion"] = springAspectsVersion
extra["aspectjVersion"] = aspectjVersion
extra["hkdfVersion"] = hkdfVersion

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    // HKDF for key derivation
    implementation("at.favre.lib:hkdf:$hkdfVersion")

    // AspectJ for AOP support
    //implementation("org.springframework:spring-aspects:$springAspectsVersion")
    implementation("org.aspectj:aspectjrt:$aspectjVersion")
    implementation("org.aspectj:aspectjweaver:$aspectjVersion")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.0.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<KotlinCompile> {
	compilerOptions {
		languageVersion.set(KotlinVersion.KOTLIN_2_2)
		apiVersion.set(KotlinVersion.KOTLIN_2_2)
		freeCompilerArgs.addAll(listOf("-Xjvm-default=all", "-Xjsr305=strict"))
	}
}

tasks.withType<Test> {
	useJUnitPlatform()

	// Enable AspectJ load-time weaving for tests, using aop.xml.
	val aspectjWeaver = configurations.testRuntimeClasspath.get().files.find { it.name.contains("aspectjweaver") }
	if (aspectjWeaver != null) {
		jvmArgs("-javaagent:${aspectjWeaver.absolutePath}")
		// Point LTW to our test aop.xml that limits weaving scope to our packages
		systemProperty("org.aspectj.weaver.loadtime.configuration","META-INF/aop.xml")
		// Optional: reduce noise from missing types in third-party libs
		systemProperty("org.aspectj.weaver.DUMP.before","false")
	}

	// Add JVM arguments to open javax.crypto.spec and java.lang for reflection during tests
	jvmArgs(
		"--add-opens", "java.base/javax.crypto.spec=ALL-UNNAMED",
		"--add-opens", "java.base/java.lang=ALL-UNNAMED"
	)
}

tasks.withType<JavaExec> {
    // Add JVM arguments to open javax.crypto.spec and java.lang for reflection for all JavaExec tasks
    jvmArgs(
        "--add-opens", "java.base/javax.crypto.spec=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    )
}

// Disable bootJar for library projects (no main class)
tasks.named<BootJar>("bootJar") {
    enabled = false
}

// Ensure the main JAR is produced without a classifier and -plain is never generated
tasks.jar {
    // Empty String for no classifier & root of JAR
    val none = ""
    archiveClassifier.convention(none) // Ensures no classifier, produces artifactId-version.jar
    from("README.md") { into(none) } // Include README.md at the root of the JAR
}

defaultTasks("build")

// Configure Dokka Javadoc task for Kotlin documentation (including private/internal members)
tasks.withType<DokkaTask>().configureEach {
    outputDirectory.set(layout.buildDirectory.dir("dokkaJavadoc").get().asFile)
    dokkaSourceSets.configureEach {
        includeNonPublic.set(true)
        skipDeprecated.set(false)
        reportUndocumented.set(false)
        jdkVersion.set(21)
    }
}

tasks.register<Jar>("dokkaJavadocJar") {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    archiveClassifier.set("javadoc")
    dependsOn(tasks.named("dokkaJavadoc"))
    from(layout.buildDirectory.dir("dokkaJavadoc").map { it.asFile })
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.named("jar")) // Explicitly use the standard jar
            artifact(tasks.named("dokkaJavadocJar")) // Use Dokka Javadoc JAR
            artifact(tasks.named("sourcesJar")) // Use sources JAR
            pom {
                name.set("Encryptable")
                description.set("Enterprise-grade, zero-knowledge, stateless cryptographic framework for JVM applications. Provides secure, anonymous, and compliant data protection with minimal developer effort.")
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
            }
        }
    }

    repositories {
        maven {
            name = "ossrh-staging-api"
            val releasesRepoUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
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
    sign(publishing.publications["mavenJava"])
}

tasks.named("build") {
    dependsOn(tasks.named("dokkaJavadocJar"))
}

// Ensure publish only happens after successful tests
tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(tasks.named("test"))
}

subprojects {
    group = rootProject.group
    version = rootProject.version
}

tasks.register("publishToMavenCentral") {
    group = "publishing"
    description = "Publishes all Maven publications produced by this project to Maven Central."
    val mainPublish = tasks.matching { it.name == "publish" }
    // Collect publish tasks from subprojects
    val subprojectPublishes = subprojects.map { it.tasks.matching { t -> t.name == "publish" } }
    val allPublishes = mainPublish + subprojectPublishes
    dependsOn(allPublishes)
    doLast {
        val username = properties["ossrhUsername"] as String? ?: throw IllegalArgumentException("OSSRH username not found")
        val password = properties["ossrhPassword"] as String? ?: throw IllegalArgumentException("OSSRH password not found")
        val namespace = properties["ossrhNamespace"] as String? ?: project.group.toString()
        val token = Base64.getEncoder().encodeToString("$username:$password".encodeToByteArray())
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(uri("https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/$namespace"))
            .header("Authorization", "Bearer $token")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() < 400) {
            logger.info(response.body())
        } else {
            logger.error(response.body())
        }
    }
}