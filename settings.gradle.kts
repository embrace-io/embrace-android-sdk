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
    ":embrace-lint",
    ":embrace-test-common",
    ":embrace-test-fakes"
)

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
