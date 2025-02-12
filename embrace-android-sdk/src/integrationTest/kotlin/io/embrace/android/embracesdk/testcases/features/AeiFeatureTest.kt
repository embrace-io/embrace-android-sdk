@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases.features

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.config.remote.AppExitInfoConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

@Config(sdk = [Build.VERSION_CODES.R], shadows = [ShadowActivityManager::class])
@RunWith(AndroidJUnit4::class)
internal class AeiFeatureTest {

    private data class TestAeiData(
        val reason: Int,
        val status: Int,
        val description: String,
        val trace: String? = null,
        val timestamp: Long = 15000000000L,
        val pid: Int = 6952,
        val pss: Long = 1509123409L,
        val rss: Long = 1123409L,
        val importance: Int = 100,
        val sessionId: String? = "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d",
        val sessionIdError: String? = "",
        val traceStatus: String? = null,
    ) {

        fun toAeiObject() = mockk<ApplicationExitInfo>(relaxed = true) {
            every { timestamp } returns this@TestAeiData.timestamp
            every { pid } returns this@TestAeiData.pid
            every { processStateSummary } returns this@TestAeiData.sessionId?.toByteArray()
            every { importance } returns this@TestAeiData.importance
            every { pss } returns this@TestAeiData.pss
            every { rss } returns this@TestAeiData.rss
            every { reason } returns this@TestAeiData.reason
            every { status } returns this@TestAeiData.status
            every { description } returns this@TestAeiData.description
            every { traceInputStream } returns this@TestAeiData.trace?.byteInputStream()
        }
    }

    @Rule
    @JvmField
    val testRule = SdkIntegrationTestRule()

    private val jvmCrash = TestAeiData(
        ApplicationExitInfo.REASON_CRASH,
        1,
        "jvmCrash",
        "someJvmCrashDetails"
    )

    private val nativeCrash = TestAeiData(
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        6, // SIGABRT
        "ndkCrash",
        "someNdkCrashDetails"
    )

    private val anr = TestAeiData(
        ApplicationExitInfo.REASON_ANR,
        0, // SIGABRT
        "ndkCrash",
        "someNdkCrashDetails"
    )

    private val other = TestAeiData(
        ApplicationExitInfo.REASON_OTHER,
        0,
        "other",
    )

    @Test
    fun `jvm crash`() {
        testRule.runTest(
            setupAction = {
                setupFakeAeiData(listOf(jvmCrash.toAeiObject()))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                log.assertContainsAeiData(jvmCrash)
            }
        )
    }

    @Test
    fun `native crash`() {
        testRule.runTest(
            setupAction = {
                setupFakeAeiData(listOf(nativeCrash.toAeiObject()))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                log.assertContainsAeiData(nativeCrash)
            }
        )
    }

    @Test
    fun `anr exit`() {
        testRule.runTest(
            setupAction = {
                setupFakeAeiData(listOf(anr.toAeiObject()))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                log.assertContainsAeiData(anr)
            }
        )
    }

    @Test
    fun `other exit`() { // only exits with traces are sent
        testRule.runTest(
            setupAction = {
                setupFakeAeiData(listOf(other.toAeiObject(), anr.toAeiObject()))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                log.assertContainsAeiData(anr)
            }
        )
    }

    @Test
    fun `aei limit exceeded`() {
        val timestamps = 0..50L
        val aeis = timestamps.map { anr.copy(timestamp = it) }.map(TestAeiData::toAeiObject)
        val expectedSize = 32

        testRule.runTest(
            setupAction = {
                setupFakeAeiData(aeis)
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val envelopes = getLogEnvelopes(expectedSize)
                assertEquals(expectedSize, envelopes.size)
                val logs = envelopes.mapNotNull { it.data.logs?.singleOrNull() }
                assertEquals(32, logs.size)
            }
        )
    }

    @Test
    fun `empty aei list`() {
        testRule.runTest(
            setupAction = {
                setupFakeAeiData(emptyList())
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertTrue(getLogEnvelopes(0).isEmpty())
            }
        )
    }

    @Test
    fun `delivered aei objects are not reattempted`() {
        val firstObj = anr.copy(timestamp = 17000000000L)
        val secondObj = anr.copy(timestamp = 18000000000L)
        val input = listOf(firstObj, secondObj)

        testRule.runTest(
            setupAction = {
                setupFakeAeiData(input.map(TestAeiData::toAeiObject))
                assertTrue(retrieveAeiHistory().isEmpty())
                alterAeiHistory(setOf(firstObj.generateAeiId()))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                log.assertContainsAeiData(secondObj)

                val history = retrieveAeiHistory()
                assertEquals(
                    input.map { it.generateAeiId() },
                    history.toList()
                )
            }
        )
    }

    @Test
    fun `empty session ID in processStateSummary`() {
        val obj = anr.copy(sessionId = "")
        testRule.runTest(
            setupAction = {
                setupFakeAeiData(listOf(obj.toAeiObject()))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                log.assertContainsAeiData(obj)
            }
        )
    }

    @Test
    fun `invalid session ID in processStateSummary`() {
        val obj = anr.copy(sessionId = "invalid", sessionIdError = "invalid session ID: invalid")
        testRule.runTest(
            setupAction = {
                setupFakeAeiData(listOf(obj.toAeiObject()))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                log.assertContainsAeiData(obj)
            }
        )
    }

    @Test
    fun `trace exceeds limit`() {
        val limit = 1000
        val obj = anr.copy(trace = "a".repeat(10000))
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                appExitInfoConfig = AppExitInfoConfig(appExitInfoTracesLimit = limit)
            ),
            setupAction = {
                setupFakeAeiData(listOf(obj.toAeiObject()))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                val expected = obj.copy(traceStatus = "Trace was too large, sending truncated trace", trace = "a".repeat(limit))
                log.assertContainsAeiData(expected)
            }
        )
    }

    private fun Log.assertContainsAeiData(
        expected: TestAeiData,
    ) {
        with(expected) {
            attributes?.assertMatches(
                mapOf<String, Any?>(
                    "timestamp" to timestamp,
                    "aei_session_id" to sessionId,
                    "session_id_error" to sessionIdError,
                    "trace_status" to traceStatus,
                    "process_importance" to importance,
                    "pss" to pss,
                    "rss" to rss,
                    "exit_status" to status,
                    "description" to description,
                    "reason" to reason,
                    "emb.type" to "sys.exit"
                )
            )
            assertEquals(trace, body)
        }
    }

    private fun setupFakeAeiData(data: List<ApplicationExitInfo>) {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val shadowActivityManager: ShadowActivityManager = Shadows.shadowOf(am)
        data.forEach(shadowActivityManager::addApplicationExitInfo)
    }

    @Suppress("DEPRECATION")
    private fun retrieveAeiHistory(): Set<String> {
        return PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
            .getStringSet("io.embrace.aeiHashCode", null) ?: emptySet()
    }

    @Suppress("DEPRECATION")
    private fun alterAeiHistory(history: Set<String>) {
        PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
            .edit()
            .putStringSet("io.embrace.aeiHashCode", history)
            .commit()
    }

    private fun TestAeiData.generateAeiId() = "${timestamp}_${pid}"
}
