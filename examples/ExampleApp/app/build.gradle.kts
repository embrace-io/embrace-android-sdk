import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.embrace)
    alias(libs.plugins.metro)
}

embrace {
    bytecodeInstrumentation {
        firebasePushNotificationsEnabled.set(true)
    }
}

android {
    namespace = "io.embrace.android.exampleapp"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "io.embrace.android.exampleapp"
        minSdk = 24
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
        create("obfuscated") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    add("kotlinCompilerPluginClasspath", libs.kotlin.serialization.compiler.plugin)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material)
    implementation(libs.androidx.material.icons)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(platform(libs.opentelemetry.bom))
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.firebase.messaging)
    implementation(libs.embrace.android.otel.java)
    // opentelemetry-kotlin processor API used by TelemetryVerificationTap; the Embrace SDK already
    // ships these at runtime, this just makes them visible at compile time. Keep the version in
    // sync with the SDK's otelKotlin version. compileOnly is load-bearing: as implementation these
    // override the SDK-under-test's own otel-kotlin on the runtime classpath, which crashes any SDK
    // built against a different version (8.3.0 died with NoSuchMethodError on Context.storeSpan in
    // a posted Handler callback - AFTER the activity was up, so macrobenchmark still reported rc=0
    // and produced 200 traces from a process that crashed on every launch).
    compileOnly("io.opentelemetry.kotlin:api:0.6.0")
    compileOnly("io.opentelemetry.kotlin:sdk-api:0.6.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // uncomment to enable debugging through source contained in those modules

//    implementation(libs.embrace.android.api)
//    implementation(libs.embrace.android.core)
//    implementation(libs.embrace.android.otel)
//    implementation(libs.embrace.android.payload)
//    implementation(libs.embrace.android.delivery)
//    implementation(libs.embrace.android.telemetry.persistence)
//    implementation(libs.embrace.android.instrumentation.api)
//    implementation(libs.embrace.android.okhttp)
//    implementation(libs.embrace.android.network.common)
//    implementation(libs.embrace.android.sdk)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
