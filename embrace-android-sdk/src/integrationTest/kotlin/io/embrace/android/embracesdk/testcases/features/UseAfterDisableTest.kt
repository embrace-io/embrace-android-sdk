package io.embrace.android.embracesdk.testcases.features

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression tests that catch `IllegalStateException: SDK not initialized` thrown from
 * `UninitializedModuleGraph` after `Embrace.disable()`.
 *
 * `disable()` resets the module graph, but the startup tracker stays registered for activity lifecycle
 * callbacks, and the SDK init attributes provider captured in `markSdkInitComplete` still reads modules
 * through the bootstrapper.
 */
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
@RunWith(AndroidJUnit4::class)
internal class UseAfterDisableTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(workersToFake = listOf(Worker.Background.NonIoRegWorker))
    }

    @Test
    fun `first frame after disable does not crash while sdk init span is still queued`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(bgActivityCapture = true),
            ),
            setupAction = {
                getEmbLogger().throwOnInternalError = false
            },
            testCaseAction = {
                embrace.disable()
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = false,
                )
            },
        )
    }

    @Test
    fun `first frame after disable does not crash when queued sdk init span runs after the reset`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(bgActivityCapture = true),
            ),
            setupAction = {
                getEmbLogger().throwOnInternalError = false
            },
            testCaseAction = {
                embrace.disable()
                testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).runCurrentlyBlocked()
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = false,
                )
            },
        )
    }

    @Test
    fun `jvm crash after disable is handled without an sdk exception`() {
        testRule.runTest(
            setupAction = {
                getEmbLogger().throwOnInternalError = false
            },
            testCaseAction = {
                embrace.disable()
                simulateJvmUncaughtException(RuntimeException("host app crash"))
            },
        )
    }
}
