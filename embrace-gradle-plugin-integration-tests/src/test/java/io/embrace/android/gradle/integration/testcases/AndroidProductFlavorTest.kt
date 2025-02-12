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

    @Test
    fun assemble() {
        rule.runTest(
            fixture = "android-product-flavors",
            task = "assemble",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(2)
            }
        )
    }

    @Test
    fun assembleProductFlavor() {
        rule.runTest(
            fixture = "android-product-flavors",
            task = "assembleDemo",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            }
        )
    }

    @Test
    fun bundle() {
        rule.runTest(
            fixture = "android-product-flavors",
            task = "bundle",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(2)
            }
        )
    }

    @Test
    fun bundleProductFlavor() {
        rule.runTest(
            fixture = "android-product-flavors",
            task = "bundleFull",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            }
        )
    }
}
