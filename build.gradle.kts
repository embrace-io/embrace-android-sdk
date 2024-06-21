// Top-level build file where you can add configuration options common to all sub-projects/modules.
import io.embrace.gradle.Versions
import java.util.Properties
import java.io.FileInputStream

buildscript {
    repositories {
        google()
        maven(url = "https://plugins.gradle.org/m2/")
    }

    dependencies {
        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.0")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.9.10")
        classpath("org.jetbrains.dokka:android-documentation-plugin:1.9.10")
    }
}

plugins {
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") apply false
}

group = "io.embrace"
version = project.version//.properties.getVersion()

// load credentials from local properties if present
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

nexusPublishing {
    repositories {
        sonatype {
            username = System.getenv("SONATYPE_USERNAME") ?: localProperties.getProperty("ossrhUsername")
            password = System.getenv("SONATYPE_PASSWORD") ?: localProperties.getProperty("ossrhPassword")
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
        }
    }
}

allprojects {
    repositories {
        google()
        maven(url = "https://plugins.gradle.org/m2/")
    }
}
