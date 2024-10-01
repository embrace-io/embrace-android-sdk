package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class UserFeaturesTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(startImmediately = false)
    }

    @Test
    fun `user info setting and clearing`() {
        testRule.runTest(
            setupAction = {
                overriddenAndroidServicesModule.preferencesService.apply {
                    userIdentifier = "customId"
                    username = "customUserName"
                    userEmailAddress = "custom@domain.com"
                }
            },
            testCaseAction = {
                startSdk()
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
                val sessions = getSentSessions(4)
                sessions[0].assertUserInfo("customId", "customUserName", "custom@domain.com")
                sessions[1].assertUserInfo(null, null, null)
                sessions[2].assertUserInfo("newId", "newUserName", "new@domain.com")
                sessions[3].assertUserInfo("newId", "newUserName", "new@domain.com")
            }
        )
    }

    private fun Envelope<SessionPayload>.assertUserInfo(
        userId: String?,
        userName: String?,
        email: String?
    ) {
        val ref = checkNotNull(metadata)
        assertEquals(userId, ref.userId)
        assertEquals(userName, ref.username)
        assertEquals(email, ref.email)
    }
}
