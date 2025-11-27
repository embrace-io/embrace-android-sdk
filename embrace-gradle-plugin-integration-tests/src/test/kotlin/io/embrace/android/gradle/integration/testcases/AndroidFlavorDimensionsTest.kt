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
    private val appIds = listOf("demod", "fulld", "demor", "fullr", "democ", "fullc")
    private val obfuscatedAppIds = listOf("democ", "fullc", "demor", "fullr")

    @Test
    fun assemble() {
        rule.runTest(
            fixture = "android-build-types-product-flavors",
            task = "assemble",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(obfuscatedAppIds)
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
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(listOf("democ", "demor"))
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
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(listOf("demor", "fullr"))
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
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(listOf("fullc"))
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
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(obfuscatedAppIds)
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
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(listOf("democ", "demor"))
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
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(listOf("demor", "fullr"))
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
                verifyBuildTelemetryRequestSent(expectedVariants = variants, expectedAppIds = appIds)
                verifyJvmMappingRequestsSent(listOf("fullc"))
            }
        )
    }
}
