package io.embrace.android.embracesdk.testcases.features

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertStateSpan
import io.embrace.android.embracesdk.assertions.assertStateTransition
import io.embrace.android.embracesdk.assertions.getNavigationStateSpan
import io.embrace.android.embracesdk.assertions.getScreenStateSpan
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@RunWith(AndroidJUnit4::class)
internal class ScreenStateFeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `screen data source records no data if state feature flag is disabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(pctStateCaptureEnabledV2 = 0.0f),
            testCaseAction = {
                recordSession {
                    embrace.screenLoaded("home")
                }
            },
            assertAction = {
                assertNull(getSingleSessionEnvelope().getScreenStateSpan())
            },
        )
    }

    @Test
    fun `no screen state span is created if screenLoaded is never invoked`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {}
            },
            assertAction = {
                assertNull(getSingleSessionEnvelope().getScreenStateSpan())
            },
        )
    }

    @Test
    fun `screenLoaded emits transitions on a span that starts with Uninitialized`() {
        var firstTransitionTime: Long = 0
        var secondTransitionTime: Long = 0
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    firstTransitionTime = clock.now()
                    embrace.screenLoaded("home")
                    clock.tick(1000)
                    secondTransitionTime = clock.now()
                    embrace.screenLoaded("checkout")
                }
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getScreenStateSpan())
                stateSpan.assertStateSpan(
                    initialValue = "Uninitialized",
                    transitionTimesMs = listOf(firstTransitionTime, secondTransitionTime),
                    newStateValues = listOf("home", "checkout"),
                )
            },
        )
    }

    @Test
    fun `screen state value carries forward to the next session`() {
        var session1FirstTransition: Long = 0
        var session1SecondTransition: Long = 0
        var session2TransitionTime: Long = 0
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    session1FirstTransition = clock.now()
                    embrace.screenLoaded("home")
                    clock.tick(1000)
                    session1SecondTransition = clock.now()
                    embrace.screenLoaded("checkout")
                }
                recordSession {
                    clock.tick(1000)
                    session2TransitionTime = clock.now()
                    embrace.screenLoaded("confirmation")
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                checkNotNull(sessions[0].getScreenStateSpan()).assertStateSpan(
                    initialValue = "Uninitialized",
                    transitionTimesMs = listOf(session1FirstTransition, session1SecondTransition),
                    newStateValues = listOf("home", "checkout"),
                )

                checkNotNull(sessions[1].getScreenStateSpan()).assertStateSpan(
                    initialValue = "checkout",
                    transitionTimesMs = listOf(session2TransitionTime),
                    newStateValues = listOf("confirmation"),
                )
            },
        )
    }

    @Test
    fun `screenLoaded attributes recorded on the transition event`() {
        var transitionTime: Long = 0
        val customAttributes = mapOf("cart_size" to "3", "promo" to "FALL")
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    transitionTime = clock.now()
                    embrace.screenLoaded("checkout", customAttributes)
                }
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getScreenStateSpan())
                checkNotNull(stateSpan.events).single().assertStateTransition(
                    timestampMs = transitionTime,
                    newStateValue = "checkout",
                    transitionAttributes = customAttributes,
                )
            },
        )
    }

    @Test
    fun `repeated screenLoaded with same screen is deduplicated`() {
        var firstTransition: Long = 0
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    firstTransition = clock.now()
                    embrace.screenLoaded("home")
                    clock.tick(1000)
                    embrace.screenLoaded("home")
                }
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getScreenStateSpan())
                stateSpan.assertStateSpan(
                    initialValue = "Uninitialized",
                    transitionTimesMs = listOf(firstTransition),
                    newStateValues = listOf("home"),
                )
            },
        )
    }

    @Test
    fun `screen state coexists with automatic navigation state in the same session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.screenLoaded("home")
                    clock.tick(10_000L)
                    embrace.screenLoaded("cart")
                    clock.tick(10_000L)
                    embrace.screenLoaded("checkout")
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                val screenStateSpan = checkNotNull(envelope.getScreenStateSpan())
                val navigationStateSpan = checkNotNull(envelope.getNavigationStateSpan())

                assertEquals(2, navigationStateSpan.events?.size)
                assertEquals(3, screenStateSpan.events?.size)
            },
        )
    }
}
