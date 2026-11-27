package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.fakes.config.FakeAppStateLocalConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class AppStateConfigFeatureTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    @Test
    fun `local app state config is seen by an app with no app id and off by default`() {
        val noAppId = FakeInstrumentedConfig(project = FakeProjectConfig(appId = null))
        testRule.runTest(
            instrumentedConfig = noAppId,
            setupAction = { appStateLocalConfig = FakeAppStateLocalConfig(enabled = true, exportMode = "verbose") },
            testCaseAction = {},
            assertAction = {
                val appState = testRule.services.configService.config.appState
                assertEquals(true, appState.enabled)
                assertEquals("verbose", appState.exportMode)
            },
        )
    }

    @Test
    fun `remote app state config overrides local`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(pctAppStateEnabled = 0f, appStateExportMode = "association"),
            setupAction = { appStateLocalConfig = FakeAppStateLocalConfig(enabled = true, exportMode = "verbose") },
            testCaseAction = {},
            assertAction = {
                val appState = testRule.services.configService.config.appState
                assertEquals(false, appState.enabled)
                assertEquals("association", appState.exportMode)
            },
        )
    }

    @Test
    fun `app state config is off and empty by default`() {
        testRule.runTest(
            testCaseAction = {},
            assertAction = {
                val appState = testRule.services.configService.config.appState
                assertEquals(false, appState.enabled)
                assertEquals("", appState.exportMode)
            },
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
