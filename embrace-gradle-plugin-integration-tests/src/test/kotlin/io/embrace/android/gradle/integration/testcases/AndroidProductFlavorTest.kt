package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Rule
import org.junit.Test

class AndroidProductFlavorTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val variants = listOf("demoDebug", "fullDebug", "demoRelease", "fullRelease")
    private val appIds = listOf("demod", "fulld", "demor", "fullr")
    private val obfuscatedAppIds = listOf("demor", "fullr")

    @Test
    fun assemble() {
        rule.runTest(
            fixture = "android-product-flavors",
            tasks = listOf("assemble"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(obfuscatedAppIds)
            },
        )
    }

    @Test
    fun assembleProductFlavor() {
        rule.runTest(
            fixture = "android-product-flavors",
            tasks = listOf("assembleDemo"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(listOf("demor"))
            },
        )
    }

    @Test
    fun bundle() {
        rule.runTest(
            fixture = "android-product-flavors",
            tasks = listOf("bundle"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(obfuscatedAppIds)
            },
        )
    }

    @Test
    fun bundleProductFlavor() {
        rule.runTest(
            fixture = "android-product-flavors",
            tasks = listOf("bundleFull"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(listOf("fullr"))
            },
        )
    }
}
