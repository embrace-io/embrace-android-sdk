package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
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
            harness.recordSession { embrace.addUserPersona("test") }
            with(harness.getSentSessions(1).last()) {
                assertPersonaExists("preloaded")
                assertPersonaExists("test")
                assertPersonaExists("payer")
            }
            harness.recordSession {
                embrace.clearUserPersona("test")
            }
            with(harness.getSentSessions(2).last()) {
                assertPersonaExists("preloaded")
                assertPersonaDoesNotExist("test")
                assertPersonaExists("payer")
            }
            harness.recordSession()
            with(harness.getSentSessions(3).last()) {
                assertPersonaExists("preloaded")
                assertPersonaDoesNotExist("test")
                assertPersonaExists("payer")
            }
            harness.recordSession()
            with(harness.getSentSessions(4).last()) {
                assertPersonaExists("preloaded")
                assertPersonaDoesNotExist("test")
                assertPersonaExists("payer")
            }
        }
    }

    private fun Envelope<SessionPayload>.assertPersonaExists(persona: String) = assertPersona(true, this, persona)

    private fun Envelope<SessionPayload>.assertPersonaDoesNotExist(persona: String) = assertPersona(false, this, persona)

    private fun assertPersona(exists: Boolean, session: Envelope<SessionPayload>, persona: String) {
        val personas = checkNotNull(session.metadata).personas
        assertEquals(exists, personas?.find { it == persona } != null)
        assertEquals(
            exists,
            testRule.harness.overriddenAndroidServicesModule.preferencesService.userPersonas?.find { it == persona } != null
        )
    }
}
