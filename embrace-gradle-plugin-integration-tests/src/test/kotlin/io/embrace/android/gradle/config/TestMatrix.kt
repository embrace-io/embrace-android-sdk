package io.embrace.android.gradle.config

/**
 * Defines the test matrix we use in one place to ensure that test cases use sane version
 * combinations. Our strategy is to pick 3 version combinations that between them cover every branch
 * the plugin has: AGP 8 vs 9, external vs built-in Kotlin, and JDK 17 vs 21. Each extra combination
 * costs roughly a minute of CI time per test that uses it, so avoid adding more without a new branch
 * to cover.
 *
 * These versions should be regularly reviewed & updated. The AGP/Gradle compatibility matrix may
 * be helpful: https://developer.android.com/build/releases/gradle-plugin#updating-gradle
 *
 * If a specific version is required then we can add it, but we should strive to reduce special
 * cases wherever possible.
 */
sealed class TestMatrix(
    val agp: String,
    val gradle: String,
    val kotlin: String,
    val jdk: JdkEnv,
    val compileAndTargetSdk: String,
) {

    object UnsupportedOldGradleVersion : TestMatrix("8.0.0", "8.0", "2.0.0", JdkEnv.JAVA_17, "34")

    /**
     * The minimum version we support & run tests against.
     */
    object MinVersion : TestMatrix("8.0.2", "8.0.2", "2.0.21", JdkEnv.JAVA_17, "34")

    /**
     * The last AGP 8 line, and the first combination running on JDK 21.
     */
    object MiddleVersion : TestMatrix("8.13.2", "8.14.5", "2.2.21", JdkEnv.JAVA_21, "36")

    /**
     * The maximum version we currently run tests against. Newer versions may work, but are not
     * explicitly tested. AGP, Kotlin and compileSdk come from the version catalog and Gradle from the
     * wrapper.
     */
    object MaxVersion : TestMatrix(
        maxVersionProperty("agp"),
        maxVersionProperty("gradle"),
        maxVersionProperty("kotlin"),
        JdkEnv.JAVA_21,
        maxVersionProperty("compileSdk"),
    )
}

private fun maxVersionProperty(name: String): String {
    val key = "embrace.maxVersion.$name"
    return checkNotNull(System.getProperty(key)) { "System property '$key' must be set by the Gradle test task" }
}
