package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class PersonaFeaturesTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `personas found in metadata`() {
        testRule.runTest(
            setupAction = {
                overriddenAndroidServicesModule.preferencesService.userPersonas = setOf("preloaded")
            },
            testCaseAction = {
                embrace.setUserAsPayer()
                recordSession {
                    embrace.addUserPersona("test")
                }
                recordSession {
                    embrace.clearUserPersona("test")
                }
                recordSession()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(4)

                with(sessions[0]) {
                    assertPersonaExists("preloaded")
                    assertPersonaExists("test")
                    assertPersonaExists("payer")
                }
                with(sessions[1]) {
                    assertPersonaExists("preloaded")
                    assertPersonaDoesNotExist("test")
                    assertPersonaExists("payer")
                }
                with(sessions[2]) {
                    assertPersonaExists("preloaded")
                    assertPersonaDoesNotExist("test")
                    assertPersonaExists("payer")
                }
                with(sessions[3]) {
                    assertPersonaExists("preloaded")
                    assertPersonaDoesNotExist("test")
                    assertPersonaExists("payer")
                }
            }
        )
    }

    private fun Envelope<SessionPayload>.assertPersonaExists(persona: String) = assertPersona(true, this, persona)

    private fun Envelope<SessionPayload>.assertPersonaDoesNotExist(persona: String) =
        assertPersona(false, this, persona)

    private fun assertPersona(exists: Boolean, session: Envelope<SessionPayload>, persona: String) {
        val personas = checkNotNull(session.metadata).personas
        assertEquals(exists, personas?.find { it == persona } != null)
    }
}
