@file:Suppress("SpellCheckingInspection")

// disabled for BashSupport Pro
// import jetbrains.sign.GpgSignSignatoryProvider
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.readText
// BashSupport Pro
import java.util.Date
import java.text.SimpleDateFormat

// disabled for BashSupport Pro
/*buildscript {
  repositories {
    maven { url = uri("https://packages.jetbrains.team/maven/p/jcs/maven") }
  }
  dependencies {
    classpath("com.jetbrains:jet-sign:45.58")
  }
}*/

plugins {
  `java-library`
  kotlin("jvm") version "1.9.22"
  `maven-publish`
  id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
  // signing // disabled for BashSupport Pro
  id("com.github.johnrengelman.shadow") version "7.1.2" // BashSupport Pro
}

repositories {
  maven {
    url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
  }
  mavenCentral()
}

group = "org.jetbrains.pty4j"

//val pathToNativeInJar = "resources/com/pty4j/native" // Disabled for BashSupport Pro
val projectVersion = rootProject.projectDir.toPath().resolve("VERSION").readText().trim()

// BashSupport Pro
group = "com.bashsupport.pty4j"
val pathToNativeInJar = "resources/bashpro/pty4j/native"

version = projectVersion

sourceSets {
  main {
    java.srcDirs("src")
  }
  test {
    java.srcDirs("test")
  }
}

java {
  withSourcesJar()
  // Disabled for BashSupport Pro
  // withJavadocJar()
}

tasks {
  compileJava {
    sourceCompatibility = "11"
    targetCompatibility = "11"
  }
  compileKotlin {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_11)
    }
  }
  test {
    testLogging {
      events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED,
             TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR)
      exceptionFormat = TestExceptionFormat.FULL
      showExceptions = true
      showCauses = true
      showStackTraces = true
      showStandardStreams = true
    }
  }

  jar {
    // BashSupport Pro
    archiveClassifier.set("original")

    from("os") {
      include("**/*")
      into(pathToNativeInJar)
    }
    manifest {
      attributes(
        "Build-Timestamp" to DateTimeFormatter.ISO_INSTANT.format(ZonedDateTime.now()),
        "Created-By" to "Gradle ${gradle.gradleVersion}",
        "Build-Jdk" to System.getProperty("java.runtime.version"),
        "Build-OS" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")} ${System.getProperty("os.version")}"
      )
    }
  }

  javadoc {
    options {
      (this as CoreJavadocOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
    }
  }
}

tasks.register<Test>("testJar") {
  dependsOn(tasks.jar, tasks.testClasses)
  description = "Runs tests on built jar instead of build/classes/java/main/**/*.class files"
  group = "verification"

  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = project.files(
    tasks.jar.get().archiveFile.get().asFile.absolutePath,
    sourceSets.test.get().output.classesDirs,
    configurations.testRuntimeClasspath
  )
  // BashSupport Pro
  systemProperty("pty4j-bashsupport.use.preferred.native.folder", false)
  // Disabled for BashSupport Pro
  // systemProperty("use.pty4j.preferred.native.folder", false)
  shouldRunAfter(tasks.test)
}

tasks.check {
  dependsOn("testJar")
}

dependencies {
  implementation("org.jetbrains.pty4j:purejavacomm:0.0.11.1")
  implementation("org.jetbrains:annotations:24.0.1")
  implementation("org.slf4j:slf4j-api:2.0.9")
  implementation("net.java.dev.jna:jna:5.13.0")
  implementation("net.java.dev.jna:jna-platform:5.13.0")
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.jetbrains.format-ripper:format-ripper:1.1.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.1")
  testImplementation("org.assertj:assertj-core:3.26.0")
  testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")

  // BashSupport Pro
  implementation("org.jetbrains.pty4j:pty4j:0.12.34")
}

val publishingUser: String? = System.getenv("PUBLISHING_USER")
val publishingPassword: String? = System.getenv("PUBLISHING_PASSWORD")

nexusPublishing.repositories.sonatype {
  username = publishingUser
  password = publishingPassword
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
      groupId = rootProject.group.toString()
      artifactId = rootProject.name
      version = if (publishingUser != null) projectVersion else "$projectVersion-SNAPSHOT"
      pom {
        name = rootProject.name
        description = "Pseudo terminal(PTY) implementation in Java"
        url = "https://github.com/JetBrains/pty4j"
        licenses {
          license {
            name = "Eclipse Public License 1.0"
            url = "https://opensource.org/licenses/eclipse-1.0.php"
          }
        }
        developers {
          developer {
            id = "sergey.simonchik"
            name = "Sergey Simonchik"
            organization = "JetBrains"
            organizationUrl = "https://www.jetbrains.com"
            email = "sergey.simonchik@jetbrains.com"
          }
          developer {
            id = "dmitry.trofimov"
            name = "Dmitry Trofimov"
            organization = "JetBrains"
            organizationUrl = "https://www.jetbrains.com"
            email = "dmitry.trofimov@jetbrains.com"
          }
        }
        scm {
          connection = "scm:git:git@github.com:JetBrains/pty4j.git"
          developerConnection = "scm:git:ssh:github.com/JetBrains/pty4j.git"
          url = "https://github.com/JetBrains/pty4j"
        }
      }
    }
  }
}

// Diabled for BashSupport Pro
/*signing {
  sign(publishing.publications["mavenJava"])
  signatories = GpgSignSignatoryProvider()
}*/

// changes for BashSupport Pro
tasks.withType<ShadowJar> {
  configurations = emptyList()
  archiveClassifier.set("")
  relocate("com.pty4j", "bashpro.pty4j") {
    exclude("com.pty4j.PtyProcess")
    exclude("com.pty4j.WinSize")
    exclude("com.pty4j.util.LazyValue")
  }
  // copied from jar task
  from("os") {
    include("**/*")
    into(pathToNativeInJar)
  }
  manifest {
    attributes(
      "Build-Timestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(Date()),
      "Created-By" to "Gradle ${gradle.gradleVersion}",
      "Build-Jdk" to System.getProperty("java.runtime.version"),
      "Build-OS" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")} ${System.getProperty("os.version")}"
    )
  }
}

tasks.withType<Test> {
  dependsOn(tasks.shadowJar)
}
