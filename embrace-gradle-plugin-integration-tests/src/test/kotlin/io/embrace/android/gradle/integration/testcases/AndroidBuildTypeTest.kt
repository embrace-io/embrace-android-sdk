package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Rule
import org.junit.Test

class AndroidBuildTypeTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val variants = listOf("debug", "release", "custom")
    private val appIds = listOf("debug", "relea", "custo")
    private val obfuscatedAppIds = listOf("custo", "relea")

    @Test
    fun assemble() {
        rule.runTest(
            fixture = "android-build-types",
            task = "assemble",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(obfuscatedAppIds)
            }
        )
    }

    @Test
    fun assembleBuildType() {
        rule.runTest(
            fixture = "android-build-types",
            task = "assembleCustom",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(listOf("custo"))
            }
        )
    }

    @Test
    fun bundle() {
        rule.runTest(
            fixture = "android-build-types",
            task = "bundle",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(obfuscatedAppIds)
            }
        )
    }

    @Test
    fun bundleBuildType() {
        rule.runTest(
            fixture = "android-build-types",
            task = "bundleCustom",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(listOf("custo"))
            }
        )
    }
}
