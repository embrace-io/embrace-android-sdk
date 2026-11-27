package io.embrace.android.gradle.config

/**
 * Defines the test matrix we use in one place to ensure that test cases use sane version
 * combinations. Our strategy is to pick 3 version combinations that span the supported range and
 * between them cover every branch the plugin has: AGP 8 vs 9, external vs built-in Kotlin, and
 * JDK 17 vs 21. Each extra combination costs roughly a minute of CI time per test that uses it, so
 * avoid adding more without a new branch to cover.
 *
 * The versions live in gradle/libs.versions.toml, and should be regularly reviewed & updated. The
 * AGP/Gradle compatibility matrix may be helpful:
 * https://developer.android.com/build/releases/gradle-plugin#updating-gradle
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

    /**
     * A Gradle version below the minimum, which the plugin must refuse to apply on.
     */
    object UnsupportedOldGradleVersion : TestMatrix(
        catalogVersion("testMatrixUnsupportedGradleAgp"),
        catalogVersion("testMatrixUnsupportedGradle"),
        catalogVersion("kotlinCoreLibrariesVersion"),
        catalogJdk("testMatrixMinJdk"),
        catalogVersion("minCompileSdk"),
    )

    /**
     * The minimum version we support & run tests against: AGP 8 with the external Kotlin plugin, on JDK 17.
     */
    object MinVersion : TestMatrix(
        catalogVersion("minAgpVersion"),
        catalogVersion("minGradleVersion"),
        catalogVersion("kotlinCoreLibrariesVersion"),
        catalogJdk("testMatrixMinJdk"),
        catalogVersion("minCompileSdk"),
    )

    /**
     * The first AGP 9 line on its minimum Gradle, with built-in Kotlin, on JDK 21. Kotlin matches the
     * Kotlin Gradle plugin that AGP 9.0 depends on.
     */
    object MiddleVersion : TestMatrix(
        catalogVersion("testMatrixMiddleAgp"),
        catalogVersion("testMatrixMiddleGradle"),
        catalogVersion("testMatrixMiddleKotlin"),
        catalogJdk("testMatrixMiddleJdk"),
        catalogVersion("testMatrixMiddleCompileSdk"),
    )

    /**
     * The maximum version we currently run tests against. Newer versions may work, but are not
     * explicitly tested.
     */
    object MaxVersion : TestMatrix(
        catalogVersion("testMatrixMaxAgp"),
        catalogVersion("testMatrixMaxGradle"),
        catalogVersion("testMatrixMaxKotlin"),
        catalogJdk("testMatrixMaxJdk"),
        catalogVersion("testMatrixMaxCompileSdk"),
    )
}

private fun catalogVersion(alias: String): String {
    val key = "embrace.version.$alias"
    return checkNotNull(System.getProperty(key)) { "System property '$key' must be set by the Gradle test task" }
}

private fun catalogJdk(alias: String): JdkEnv = JdkEnv.valueOf("JAVA_${catalogVersion(alias)}")
