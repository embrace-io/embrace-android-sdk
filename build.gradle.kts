plugins {
    kotlin("android") apply false
    kotlin("jvm") apply false
    alias(libs.plugins.google.ksp) apply false
    id("com.android.library") apply false
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.kover")
}

group = "io.embrace"
version = project.version

kover {
    merge {
        subprojects { project ->
            val ignoreList = listOf("embrace-lint", "embrace-microbenchmark")
            !project.name.contains("-test") &&
                !project.name.contains("-fakes") &&
                !ignoreList.contains(project.name)
        }
    }
    reports {
        filters {
            excludes {
                androidGeneratedClasses()
                classes("*.BuildConfig")
            }
        }
    }
}

dependencies {
    dokka(project(":embrace-android-api"))
    dokka(project(":embrace-android-sdk"))
}
