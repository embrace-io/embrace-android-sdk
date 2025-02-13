package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Rule
import org.junit.Test

class AndroidFlavorDimensionsTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val variants = listOf("demoDebug", "fullDebug", "demoRelease", "fullRelease", "demoCustom", "fullCustom")

    @Test
    fun assemble() {
        rule.runTest(
            fixture = "android-build-types-product-flavors",
            task = "assemble",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(4)
            }
        )
    }

    @Test
    fun assembleProductFlavor() {
        rule.runTest(
            fixture = "android-build-types-product-flavors",
            task = "assembleDemo",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(2)
            }
        )
    }

    @Test
    fun assembleBuildType() {
        rule.runTest(
            fixture = "android-build-types-product-flavors",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(2)
            }
        )
    }

    @Test
    fun assembleBuildTypeProductFlavor() {
        rule.runTest(
            fixture = "android-build-types-product-flavors",
            task = "assembleFullCustom",
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
            fixture = "android-build-types-product-flavors",
            task = "bundle",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(4)
            }
        )
    }

    @Test
    fun bundleProductFlavor() {
        rule.runTest(
            fixture = "android-build-types-product-flavors",
            task = "bundleDemo",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(2)
            }
        )
    }

    @Test
    fun bundleBuildType() {
        rule.runTest(
            fixture = "android-build-types-product-flavors",
            task = "bundleRelease",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(2)
            }
        )
    }

    @Test
    fun bundleBuildTypeProductFlavor() {
        rule.runTest(
            fixture = "android-build-types-product-flavors",
            task = "bundleFullCustom",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            }
        )
    }
}
