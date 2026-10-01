package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
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

    @Suppress("DEPRECATION")
    @Test
    fun `user info setting and clearing`() {
        testRule.runTest(
            setupAction = {
                getStore().editAndCommit {
                    putString("io.embrace.userid", "customId")
                    putString("io.embrace.username", "customUserName")
                    putString("io.embrace.useremail", "custom@domain.com")
                }
            },
            testCaseAction = {
                recordSession()
                recordSession {
                    embrace.clearUserIdentifier()
                    embrace.clearUsername()
                    embrace.clearUserEmail()
                }
                recordSession {
                    embrace.setUserIdentifier("newId")
                    embrace.setUsername("newUserName")
                    embrace.setUserEmail("new@domain.com")
                }
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(4)
                sessions[0].assertUserInfo("customId", "customUserName", "custom@domain.com")
                sessions[1].assertUserInfo(null, null, null)
                sessions[2].assertUserInfo("newId", "newUserName", "new@domain.com")
                sessions[3].assertUserInfo("newId", "newUserName", "new@domain.com")
            }
        )
    }

    private fun Envelope<SessionPartPayload>.assertUserInfo(
        userId: String?,
        userName: String?,
        email: String?,
    ) {
        val ref = checkNotNull(metadata)
        assertEquals(userId, ref.userId)
        assertEquals(userName, ref.username)
        assertEquals(email, ref.email)
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
