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
internal class PersonaFeaturesTest(
    private val otelSdkMode: OtelSdkMode,
) {
    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    @Test
    fun `personas found in metadata`() {
        testRule.runTest(
            setupAction = {
                getStore().editAndCommit {
                    putStringSet("io.embrace.userpersonas", setOf("preloaded"))
                }
            },
            testCaseAction = {
                embrace.addUserPersona("payer")
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

    private fun Envelope<SessionPartPayload>.assertPersonaExists(persona: String) = assertPersona(true, this, persona)

    private fun Envelope<SessionPartPayload>.assertPersonaDoesNotExist(persona: String) =
        assertPersona(false, this, persona)

    private fun assertPersona(exists: Boolean, session: Envelope<SessionPartPayload>, persona: String) {
        val personas = checkNotNull(session.metadata).personas
        assertEquals(exists, personas?.find { it == persona } != null)
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
