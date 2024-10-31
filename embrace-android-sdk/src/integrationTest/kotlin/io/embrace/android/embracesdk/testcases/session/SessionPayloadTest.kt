package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SessionPayloadTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `device and app attributes are present in session envelope`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()

            },
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertEquals("spans", type)
                    with(checkNotNull(resource)) {
                        assertTrue(checkNotNull(appVersion).isNotBlank())
                        assertTrue(checkNotNull(sdkVersion).isNotBlank())
                        assertTrue(checkNotNull(osVersion).isNotBlank())
                        assertTrue(checkNotNull(osName).isNotBlank())
                        assertTrue(checkNotNull(deviceModel).isNotBlank())
                        assertEquals(AppFramework.NATIVE, appFramework)
                    }
                    assertTrue(getSessionId().isNotBlank())
                }
            }
        )
    }
}
