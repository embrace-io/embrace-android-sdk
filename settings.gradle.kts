include(
    ":embrace-android-api",
    ":embrace-internal-api",
    ":embrace-android-sdk",
    ":embrace-android-core",
    ":embrace-android-config",
    ":embrace-android-config-fakes",
    ":embrace-android-infra",
    ":embrace-android-utils",
    ":embrace-android-instrumentation-api",
    ":embrace-android-instrumentation-api-fakes",
    ":embrace-android-instrumentation-schema",
    ":embrace-android-instrumentation-anr",
    ":embrace-android-instrumentation-app-exit-info",
    ":embrace-android-instrumentation-compose-tap",
    ":embrace-android-instrumentation-crash-jvm",
    ":embrace-android-instrumentation-crash-ndk",
    ":embrace-android-instrumentation-fcm",
    ":embrace-android-instrumentation-huc",
    ":embrace-android-instrumentation-huc-lite",
    ":embrace-android-instrumentation-network-common",
    ":embrace-android-instrumentation-network-status",
    ":embrace-android-instrumentation-okhttp",
    ":embrace-android-instrumentation-power-save",
    ":embrace-android-instrumentation-startup-trace",
    ":embrace-android-instrumentation-taps",
    ":embrace-android-instrumentation-thermal-state",
    ":embrace-android-instrumentation-view",
    ":embrace-android-instrumentation-webview",
    ":embrace-android-payload",
    ":embrace-android-telemetry-persistence",
    ":embrace-android-delivery",
    ":embrace-android-delivery-fakes",
    ":embrace-android-otel",
    ":embrace-android-otel-fakes",
    ":embrace-android-otel-java",
    ":embrace-lint",
    ":embrace-test-common",
    ":embrace-test-fakes",
    ":embrace-gradle-plugin",
    ":embrace-bytecode-instrumentation-tests",
    ":embrace-gradle-plugin-integration-tests",
    ":embrace-microbenchmark",
)

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}
