import io.embrace.gradle.Versions
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library") apply false
    id("kotlin-android") apply false
    id("io.gitlab.arturbosch.detekt") apply false
}

android {
    compileSdk = Versions.COMPILE_SDK

    defaultConfig {
        minSdk = Versions.MIN_SDK
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkAllWarnings = true
        checkReleaseBuilds = false // run on CI instead, speeds up release builds
        baseline = project.file("lint-baseline.xml")
        disable.addAll(mutableSetOf("GradleDependency", "NewerVersionAvailable"))
    }
}

dependencies {
    add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:${Versions.DETEKT}")
}

detekt {
    buildUponDefaultConfig = true
    autoCorrect = true
    config.from(project.files("${project.rootDir}/config/detekt/detekt.yml")) // overwrite default behaviour here
    baseline =
        project.file("${project.projectDir}/config/detekt/baseline.xml") // suppress pre-existing issues
}

project.tasks.withType(Detekt::class.java).configureEach {
    jvmTarget = "1.8"
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
    }
}

project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
    jvmTarget = "1.8"
}

project.tasks.withType(JavaCompile::class.java).configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}

project.tasks.withType(KotlinCompile::class.java).configureEach {
    kotlinOptions {
        apiVersion = "1.8"
        languageVersion = "1.8"
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs = freeCompilerArgs + "-Xexplicit-api=strict"
    }
}
