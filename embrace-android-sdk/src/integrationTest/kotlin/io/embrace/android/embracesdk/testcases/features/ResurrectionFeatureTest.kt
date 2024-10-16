package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ResurrectionFeatureTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `resurrection attempt with v2 delivery layer off does not crash the SDK`() {
        testRule.runTest(
            setupAction = {
                useMockWebServer = false
                overriddenConfigService.autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(
                    v2StorageEnabled = false
                )
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertNotNull(getSessionEnvelopesV1(1).single())
            }
        )
    }
}
