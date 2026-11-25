import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "io.embrace.android.benchmark"
    // the app's: the endpoint's MockWebServer brings the app's OkHttp, which requires it
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.dryRunMode.enable"] = "true"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
            proguardFiles("proguard-rules.pro")
        }
        create("benchmarkOtelRegular") {
            initWith(getByName("benchmark"))
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(project(":embrace-benchmark-endpoint"))
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark" || it.buildType == "benchmarkOtelRegular"
    }
}
