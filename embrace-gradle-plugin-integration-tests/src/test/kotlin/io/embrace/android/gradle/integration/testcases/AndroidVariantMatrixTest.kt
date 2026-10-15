package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.AssertionInterface
import io.embrace.android.gradle.integration.framework.IntegrationTestDefaults
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AndroidVariantMatrixTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val default = IntegrationTestDefaults.APP_ID

    // app IDs resolve from src/<variant>, src/<flavors>, src/<flavor>, src/<buildType>, then src/main
    private val variantAppIds = linkedMapOf(
        "demoPlayDebug" to "playy",
        "demoAmazonDebug" to default,
        "fullPlayDebug" to "playy",
        "fullAmazonDebug" to "fulam",
        "demoPlayRelease" to "dplyr",
        "demoAmazonRelease" to default,
        "fullPlayRelease" to "playy",
        "fullAmazonRelease" to "fulam",
        "demoPlayCustom" to "playy",
        "demoAmazonCustom" to "custo",
        "fullPlayCustom" to "playy",
        "fullAmazonCustom" to "fulam",
    )

    @Test
    fun `variant matrix`() {
        rule.runTest(
            fixture = "android-flavor-dimensions",
            tasks = listOf("assembleDemoPlayDebug", "assembleRelease", "bundleDemoAmazonCustom"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyAllVariantsInTelemetry()

                // only the built minified variants upload: not debug, not unbuilt variants, and not
                // fullPlayRelease, which disables the plugin
                verifyJvmMappingRequestsSent(listOf("dplyr", default, "fulam", "custo"))
            },
        )

        // each assemble/bundle entry point uploads only its minified variants, never fullPlayRelease.
        // Tasks share a build only when they select disjoint variants, so each upload is attributable.
        val allUploads = listOf("dplyr", default, "fulam", "playy", "custo", "playy", "fulam")
        verifyEntryPoints(listOf("assemble"), allUploads)
        verifyEntryPoints(listOf("bundle"), allUploads)
        verifyEntryPoints(listOf("assembleCustom", "bundleRelease"), allUploads)
        verifyEntryPoints(listOf("assembleDemo", "bundleFullPlay"), listOf("dplyr", default, "playy", "custo", "playy"))
        verifyEntryPoints(listOf("assembleFullAmazon", "bundlePlay"), listOf("fulam", "fulam", "dplyr", "playy", "playy"))
    }

    private fun verifyEntryPoints(tasks: List<String>, expectedUploads: List<String>) {
        rule.runTest(
            fixture = "android-flavor-dimensions",
            tasks = listOf("clean") + tasks,
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyAllVariantsInTelemetry()
                verifyJvmMappingRequestsSent(expectedUploads)
            },
        )
    }

    private fun AssertionInterface.verifyAllVariantsInTelemetry() {
        verifyBuildTelemetryRequestSent(
            expectedVariants = variantAppIds.keys.toList(),
            expectedAppIds = variantAppIds.values.toList(),
        ) {
            assertEquals(variantAppIds.keys.toList(), variantBuildTelemetry?.map { it.variantName })
        }
    }
}
