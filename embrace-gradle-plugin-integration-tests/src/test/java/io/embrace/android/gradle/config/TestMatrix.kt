package io.embrace.android.gradle.config

/**
 * Defines the test matrix we use in one place to ensure that test cases use sane version
 * combinations. Our strategy is to pick ~5 version combinations that are representative of our
 * supported range of versions. The theory is that most customers will fall somewhere along the
 * middle of the version range distribution.
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
    val compileAndTargetSdk: String
) {

    object UnsupportedOldGradleVersion : TestMatrix("8.0.0", "8.0", "2.0.0", JdkEnv.JAVA_17, "34")

    /**
     * The minimum version we support & run tests against.
     */
    object MinVersion : TestMatrix("8.0.2", "8.0.2", "2.0.21", JdkEnv.JAVA_17, "34")

    /**
     * Middle of the pack.
     */
    object MiddleVersion : TestMatrix("8.3.2", "8.4", "2.1.21", JdkEnv.JAVA_17, "35")

    /**
     * The maximum version we currently run tests against. Newer versions may work, but are not
     * explicitly tested.
     */
    object MaxVersion : TestMatrix("8.9.1", "8.13", "2.2.20", JdkEnv.JAVA_21, "36")
}
