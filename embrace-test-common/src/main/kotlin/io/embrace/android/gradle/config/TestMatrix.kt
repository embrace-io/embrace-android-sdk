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
) {

    /**
     * The minimum version we support & run tests against.
     */
    object MinVersion : TestMatrix("7.4.2", "7.5.1", "1.8.22", JdkEnv.JAVA_11)

    /**
     * Older than middle of the pack, but not as bad as our minimum.
     */
    object OlderVersion : TestMatrix("8.1.4", "8.1.1", "1.8.22", JdkEnv.JAVA_17)

    /**
     * Middle of the pack.
     */
    object MiddleVersion : TestMatrix("8.3.2", "8.4", "1.9.22", JdkEnv.JAVA_17)

    /**
     * Not the latest, but newer than the middle of the pack.
     */
    object NewerVersion : TestMatrix("8.5.2", "8.7", "2.0.0", JdkEnv.JAVA_17)

    /**
     * The maximum version we currently run tests against. Newer versions may work, but are not
     * explicitly tested.
     */
    object MaxVersion : TestMatrix("8.8.0", "8.12.1", "2.1.10", JdkEnv.JAVA_17)
}
