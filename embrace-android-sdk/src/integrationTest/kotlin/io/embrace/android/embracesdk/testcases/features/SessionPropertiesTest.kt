package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.fakes.createBackgroundActivityBehavior
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SessionPropertiesTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `session properties additions and removal works at all stages app state transition`() {
        testRule.runTest(
            setupAction = {
                setupPermanentProperties()
            },
            testCaseAction = {
                addAndRemoveProperties()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(3)
                val bas = getSessionEnvelopes(3, ApplicationState.BACKGROUND)

                bas[0].findSessionSpan().assertPropertyExistence(
                    exist = listOf(EXISTING_KEY_2, EXISTING_KEY_3, PERM_KEY, TEMP_KEY),
                    missing = listOf(EXISTING_KEY_1)
                )
                sessions[0].findSessionSpan().assertPropertyExistence(
                    exist = listOf(EXISTING_KEY_3, PERM_KEY, PERM_KEY_2, TEMP_KEY_2),
                    missing = listOf(EXISTING_KEY_1, EXISTING_KEY_2)
                )
                bas[1].findSessionSpan().assertPropertyExistence(
                    exist = listOf(EXISTING_KEY_3, PERM_KEY_2),
                    missing = listOf(EXISTING_KEY_1, EXISTING_KEY_2, PERM_KEY, PERM_KEY_3, TEMP_KEY_3)
                )
                sessions[1].findSessionSpan().assertPropertyExistence(
                    exist = listOf(EXISTING_KEY_3, PERM_KEY_2),
                    missing = listOf(EXISTING_KEY_1, EXISTING_KEY_2, PERM_KEY, PERM_KEY_3, TEMP_KEY_3)
                )
                bas[2].findSessionSpan().assertPropertyExistence(
                    exist = listOf(EXISTING_KEY_3, PERM_KEY_2),
                    missing = listOf(EXISTING_KEY_1, EXISTING_KEY_2, PERM_KEY, PERM_KEY_3, TEMP_KEY_3)
                )
                sessions[2].findSessionSpan().assertPropertyExistence(
                    exist = listOf(EXISTING_KEY_3, PERM_KEY_2),
                    missing = listOf(EXISTING_KEY_1, EXISTING_KEY_2, PERM_KEY, PERM_KEY_3, TEMP_KEY_3)
                )
            }
        )
    }

    @Test
    fun `session properties work with background activity disabled`() {
        testRule.runTest(
            setupAction = {
                overriddenConfigService.backgroundActivityBehavior = createBackgroundActivityBehavior { BackgroundActivityRemoteConfig(threshold = 0f) }
                setupPermanentProperties()
            },
            testCaseAction = {
                addAndRemoveProperties()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(3)
                sessions[0].findSessionSpan().assertPropertyExistence(
                    exist = listOf(EXISTING_KEY_3, PERM_KEY, PERM_KEY_2, TEMP_KEY, TEMP_KEY_2),
                    missing = listOf(EXISTING_KEY_1, EXISTING_KEY_2)
                )
                sessions[1].findSessionSpan().assertPropertyExistence(
                    exist = listOf(EXISTING_KEY_3, PERM_KEY_2),
                    missing = listOf(
                        EXISTING_KEY_1, EXISTING_KEY_2, PERM_KEY, PERM_KEY_3, TEMP_KEY, TEMP_KEY_2, TEMP_KEY_3
                    )
                )
                sessions[2].findSessionSpan().assertPropertyExistence(
                    exist = listOf(EXISTING_KEY_3, PERM_KEY_2),
                    missing = listOf(
                        EXISTING_KEY_1, EXISTING_KEY_2, PERM_KEY, PERM_KEY_3, TEMP_KEY, TEMP_KEY_2, TEMP_KEY_3
                    )
                )
            }
        )
    }

    @Test
    fun `session properties are persisted in cached payloads`() {
        testRule.runTest(
            setupAction = {
                setupPermanentProperties()
            },
            testCaseAction = {
                addAndRemoveProperties()
            },
            assertAction = {
                // TODO: rewrite this after v2 delivery layer changes merged
            }
        )
    }

    @Test
    fun `session properties are persisted in cached payloads when bg activities are disabled`() {
        testRule.runTest(
            setupAction = {
                overriddenConfigService.backgroundActivityBehavior = createBackgroundActivityBehavior { BackgroundActivityRemoteConfig(threshold = 0f) }
                setupPermanentProperties()
            },
            testCaseAction = {
                addAndRemoveProperties()
            },
            assertAction = {
                // TODO: rewrite this after v2 delivery layer changes merged
            }
        )
    }

    private fun EmbraceSetupInterface.setupPermanentProperties() {
        overriddenAndroidServicesModule.preferencesService.permanentSessionProperties =
            mapOf(
                EXISTING_KEY_1 to VALUE,
                EXISTING_KEY_2 to VALUE,
                EXISTING_KEY_3 to VALUE,
            )
    }

    private fun EmbraceActionInterface.addAndRemoveProperties() {
        embrace.removeSessionProperty(EXISTING_KEY_1)
        embrace.addPermanentProperty(PERM_KEY)
        embrace.addTemporaryProperty(TEMP_KEY)
        recordSession {
            embrace.addPermanentProperty(PERM_KEY_2)
            embrace.addTemporaryProperty(TEMP_KEY_2)
            embrace.removeSessionProperty(EXISTING_KEY_2)
        }
        embrace.removeSessionProperty(PERM_KEY)
        embrace.addPermanentProperty(PERM_KEY_3)
        embrace.addTemporaryProperty(TEMP_KEY_3)
        embrace.removeSessionProperty(PERM_KEY_3)
        embrace.removeSessionProperty(TEMP_KEY_3)
        recordSession()
        recordSession()
    }

    private fun Embrace.addPermanentProperty(key: String) {
        addSessionProperty(key, VALUE, true)
    }

    private fun Embrace.addTemporaryProperty(key: String) {
        addSessionProperty(key, VALUE, false)
    }

    private fun Span.assertPropertyExistence(exist: List<String> = emptyList(), missing: List<String> = emptyList()) {
        exist.forEach {
            assertNotNull("Missing session property with key: $it", getSessionProperty(it))
        }
        missing.forEach {
            assertNull("Unexpected session property with key: $it", getSessionProperty(it))
        }
    }

    private companion object {
        const val EXISTING_KEY_1 = "existing"
        const val EXISTING_KEY_2 = "existing2"
        const val EXISTING_KEY_3 = "existing3"
        const val PERM_KEY = "perm"
        const val PERM_KEY_2 = "perm2"
        const val PERM_KEY_3 = "perm3"
        const val TEMP_KEY = "temp"
        const val TEMP_KEY_2 = "temp2"
        const val TEMP_KEY_3 = "temp3"
        const val VALUE = "value"
    }
}
