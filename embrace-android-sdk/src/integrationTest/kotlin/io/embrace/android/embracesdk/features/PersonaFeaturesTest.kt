package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.config.remote.OTelRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.fakeOTelBehavior
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.UserInfo
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class PersonaFeaturesTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(startImmediately = false)
    }

    @Test
    fun `personas found in metadata`() {
        with(testRule) {
            harness.overriddenAndroidServicesModule.preferencesService.userPersonas = setOf("preloaded")
            startSdk(context = harness.overriddenCoreModule.context)
            embrace.setUserAsPayer()
            with(checkNotNull(harness.recordSession { embrace.addUserPersona("test") })) {
                assertPersonaExists("preloaded")
                assertPersonaExists("test")
                assertPersonaExists(UserInfo.PERSONA_PAYER)
            }
            embrace.clearUserPersona("test")
            with(checkNotNull(harness.recordSession { })) {
                assertPersonaExists("preloaded")
                assertPersonaDoesNotExist("test")
                assertPersonaExists(UserInfo.PERSONA_PAYER)
            }
            embrace.clearUserAsPayer()
            with(checkNotNull(harness.recordSession { })) {
                assertPersonaExists("preloaded")
                assertPersonaDoesNotExist("test")
                assertPersonaDoesNotExist(UserInfo.PERSONA_PAYER)
            }
            embrace.clearAllUserPersonas()
            with(checkNotNull(harness.recordSession { })) {
                assertPersonaDoesNotExist("preloaded")
                assertPersonaDoesNotExist("test")
                assertPersonaDoesNotExist(UserInfo.PERSONA_PAYER)
            }
        }
    }

    private fun SessionMessage.assertPersonaExists(persona: String) = assertPersona(true, this, persona)

    private fun SessionMessage.assertPersonaDoesNotExist(persona: String) = assertPersona(false, this, persona)

    private fun assertPersona(exists: Boolean, session: SessionMessage, persona: String) {
        val personas = checkNotNull(session.metadata).personas
        assertEquals(exists, personas?.find { it == persona } != null)
        assertEquals(
            exists,
            testRule.harness.overriddenAndroidServicesModule.preferencesService.userPersonas?.find { it == persona } != null
        )
    }
}
