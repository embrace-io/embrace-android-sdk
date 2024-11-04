package io.embrace.android.embracesdk.testcases.features

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

@Config(sdk = [Build.VERSION_CODES.R], shadows = [ShadowActivityManager::class])
@RunWith(AndroidJUnit4::class)
internal class V1DeliveryFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    private val behavior = FakeAutoDataCaptureBehavior(v2StorageEnabled = false)

    @Suppress("DEPRECATION")
    @Test
    fun `v1 session delivery`() {
        testRule.runTest(
            setupAction = {
                useMockWebServer = false
                overriddenConfigService.autoDataCaptureBehavior = behavior
            },
            testCaseAction = {
                recordSession {
                    embrace.setUserIdentifier("foo")
                }
            },
            assertAction = {
                val session = getSessionEnvelopesV1(1).single()
                assertEquals("foo", session.metadata?.userId)
            }
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `v1 background activity delivery`() {
        testRule.runTest(
            setupAction = {
                useMockWebServer = false
                overriddenConfigService.autoDataCaptureBehavior = behavior
            },
            testCaseAction = {
                embrace.setUserIdentifier("foo")
                recordSession()
            },
            assertAction = {
                val ba = getSessionEnvelopesV1(1, ApplicationState.BACKGROUND).single()
                assertEquals("foo", ba.metadata?.userId)
            }
        )
    }

    @Test
    fun `v1 crash delivery`() {
        testRule.runTest(
            setupAction = {
                useMockWebServer = false
                overriddenConfigService.autoDataCaptureBehavior = behavior
            },
            testCaseAction = {
                recordSession {
                    simulateJvmUncaughtException(IllegalStateException("Whoops!"))
                }
            },
            assertAction = {
                val log = getLogEnvelopesV1(1, false).getLastLog()
                checkNotNull(log.attributes)
            }
        )
    }

    @Test
    fun `v1 log delivery`() {
        testRule.runTest(
            setupAction = {
                useMockWebServer = false
                overriddenConfigService.autoDataCaptureBehavior = behavior
                setupFakeAeiData()
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val log = getLogEnvelopesV1(1).getLastLog()

                // assert AEI fields populated
                log.attributes?.assertMatches {
                    "timestamp" to 15000000000L
                    "aei_session_id" to "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d"
                    "process_importance" to 125
                    "pss" to 1509123409L
                    "rss" to 1123409L
                    "exit_status" to 1
                    "description" to "testDescription"
                    "reason" to 4
                    "emb.type" to "sys.exit"
                }
                assertEquals("testInputStream", log.body)
            }
        )
    }

    private fun setupFakeAeiData() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val shadowActivityManager: ShadowActivityManager = Shadows.shadowOf(am)

        shadowActivityManager.addApplicationExitInfo(mockk<ApplicationExitInfo>(relaxed = true) {
            every { timestamp } returns 15000000000L
            every { pid } returns 6952
            every { processStateSummary } returns "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d".toByteArray()
            every { importance } returns 125
            every { pss } returns 1509123409L
            every { reason } returns 4
            every { rss } returns 1123409L
            every { status } returns 1
            every { description } returns "testDescription"
            every { traceInputStream } returns "testInputStream".byteInputStream()
        })
    }
}
