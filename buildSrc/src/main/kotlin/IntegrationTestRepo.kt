/**
 * Path, relative to the root project directory, of the Maven repository that projects publish to and
 * embrace-gradle-plugin-integration-tests fixtures resolve from.
 */
const val INTEGRATION_TEST_REPO_PATH = "build/integration-repo"

/**
 * Every project that publishes to the integration test repository. The integration tests depend on each project's
 * publish task, so a project that publishes must be listed here.
 */
val integrationTestPublishedProjects: Set<String> = setOf(
    ":embrace-android-api",
    ":embrace-android-config",
    ":embrace-android-core",
    ":embrace-android-delivery",
    ":embrace-android-envelope",
    ":embrace-android-infra",
    ":embrace-android-instrumentation-androidx-navigation",
    ":embrace-android-instrumentation-anr",
    ":embrace-android-instrumentation-api",
    ":embrace-android-instrumentation-app-exit-info",
    ":embrace-android-instrumentation-compose-tap",
    ":embrace-android-instrumentation-crash-jvm",
    ":embrace-android-instrumentation-crash-ndk",
    ":embrace-android-instrumentation-fcm",
    ":embrace-android-instrumentation-huc",
    ":embrace-android-instrumentation-huc-lite",
    ":embrace-android-instrumentation-leaks",
    ":embrace-android-instrumentation-navigation",
    ":embrace-android-instrumentation-network-common",
    ":embrace-android-instrumentation-network-status",
    ":embrace-android-instrumentation-okhttp",
    ":embrace-android-instrumentation-power-save",
    ":embrace-android-instrumentation-profiler",
    ":embrace-android-instrumentation-schema",
    ":embrace-android-instrumentation-startup-trace",
    ":embrace-android-instrumentation-taps",
    ":embrace-android-instrumentation-thermal-state",
    ":embrace-android-instrumentation-view",
    ":embrace-android-instrumentation-vitals",
    ":embrace-android-instrumentation-webview",
    ":embrace-android-limits",
    ":embrace-android-otel",
    ":embrace-android-otel-java",
    ":embrace-android-payload",
    ":embrace-android-sdk",
    ":embrace-android-semconv",
    ":embrace-android-session",
    ":embrace-android-session-persistence",
    ":embrace-android-telemetry-persistence",
    ":embrace-android-utils",
    ":embrace-gradle-plugin",
    ":embrace-gradle-plugin-integration-tests",
    ":embrace-internal-api",
)
