package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Rule
import org.junit.Test

class AndroidSimpleTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val variants = listOf("debug", "release")

    @Test
    fun smoke() {
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("assembleDebug", "assembleRelease"),
            projectType = ProjectType.ANDROID,
            assertions = { projectDir ->
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
                verifyNoHandshakes()
                verifyNoUploads()
                verifyNoBuildInfoExported(projectDir, "release")
            },
        )

        // each assemble/bundle entry point uploads only for the release variant.
        // Tasks share a build only when they select disjoint variants, so each upload is attributable.
        verifyEntryPoints(listOf("assemble"), expectedUploads = 1)
        verifyEntryPoints(listOf("bundle"), expectedUploads = 1)
        verifyEntryPoints(listOf("assembleDebug", "bundleRelease"), expectedUploads = 1)
        verifyEntryPoints(listOf("bundleDebug", "assembleRelease"), expectedUploads = 1)
    }

    private fun verifyEntryPoints(tasks: List<String>, expectedUploads: Int) {
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("clean") + tasks,
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(expectedUploads)
            },
        )
    }

    @Test
    fun build() {
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("build"),
            additionalArgs = listOf("-Pandroid.lint.checkReleaseBuilds=true"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            },
        )
    }
}
