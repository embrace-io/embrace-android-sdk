package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class UserFeaturesTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    @Test
    fun `user identifier setting and clearing`() {
        testRule.runTest(
            setupAction = {
                getStore().editAndCommit {
                    putString("io.embrace.userid", "customId")
                }
            },
            testCaseAction = {
                recordSession {
                    assertEquals("customId", embrace.userIdentifier)
                }
                recordSession {
                    embrace.userIdentifier = null
                    assertNull(embrace.userIdentifier)
                }
                recordSession {
                    embrace.userIdentifier = "newId"
                    assertEquals("newId", embrace.userIdentifier)
                }
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(4)
                sessions[0].assertUserId("customId")
                sessions[1].assertUserId(null)
                sessions[2].assertUserId("newId")
                sessions[3].assertUserId("newId")
            }
        )
    }

    @Test
    fun `user identifier set with the superior storage system enabled stays out of prefs`() {
        testRule.runTest(
            setupAction = {
                superiorStorageSystemEnabled = true
            },
            testCaseAction = {
                recordSession {
                    embrace.userIdentifier = "newId"
                }
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                sessions[0].assertUserId("newId")
                sessions[1].assertUserId("newId")
                assertNull(testRule.setup.getStore().getString("io.embrace.userid"))
            }
        )
    }

    private fun Envelope<SessionPartPayload>.assertUserId(userId: String?) {
        assertEquals(userId, checkNotNull(metadata).userId)
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
