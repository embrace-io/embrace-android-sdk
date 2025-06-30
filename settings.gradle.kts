include(
    ":embrace-android-api",
    ":embrace-internal-api",
    ":embrace-android-sdk",
    ":embrace-android-core",
    ":embrace-android-infra",
    ":embrace-android-features",
    ":embrace-android-payload",
    ":embrace-android-delivery",
    ":embrace-android-okhttp3",
    ":embrace-android-fcm",
    ":embrace-android-compose",
    ":embrace-android-otel",
    ":embrace-lint",
    ":embrace-test-common",
    ":embrace-test-fakes",
    ":embrace-gradle-plugin",
    ":embrace-bytecode-instrumentation-tests",
    ":embrace-gradle-plugin-integration-tests",
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
