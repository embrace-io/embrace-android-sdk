package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.recordSession
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
        with(testRule) {
            val preferenceService = harness.overriddenAndroidServicesModule.preferencesService.apply {
                userIdentifier = "customId"
                username = "customUserName"
                userEmailAddress = "custom@domain.com"
            }

            startSdk(harness.overriddenCoreModule.context)
            with(checkNotNull(harness.recordSession { })) {
                assertUserInfo(preferenceService, "customId", "customUserName", "custom@domain.com")
            }

            val session = harness.recordSession {
                embrace.clearUserIdentifier()
                embrace.clearUsername()
                embrace.clearUserEmail()
            }

            with(checkNotNull(session)) {
                assertUserInfo(preferenceService, null, null, null)
            }

            embrace.setUserIdentifier("newId")
            embrace.setUsername("newUserName")
            embrace.setUserEmail("new@domain.com")

            with(checkNotNull(harness.recordSession { })) {
                assertUserInfo(preferenceService, "newId", "newUserName", "new@domain.com")
            }
        }
    }

    private fun Envelope<SessionPayload>.assertUserInfo(
        preferencesService: PreferencesService,
        userId: String?,
        userName: String?,
        email: String?
    ) {
        val ref = checkNotNull(metadata)
        assertEquals(userId, ref.userId)
        assertEquals(userId, preferencesService.userIdentifier)
        assertEquals(userName, ref.username)
        assertEquals(userName, preferencesService.username)
        assertEquals(email, ref.email)
        assertEquals(email, preferencesService.userEmailAddress)
    }
}
