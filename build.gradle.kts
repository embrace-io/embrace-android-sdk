// Top-level build file where you can add configuration options common to all sub-projects/modules.
import java.io.FileInputStream
import java.util.Properties

buildscript {
    repositories {
        google()
        maven(url = "https://plugins.gradle.org/m2/")
    }

    dependencies {
        classpath(libs.detekt.gradle.plugin)
        classpath(libs.dokka.gradle.plugin)
        classpath(libs.dokka.docs)
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
