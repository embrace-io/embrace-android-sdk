@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases.features

import android.app.ApplicationExitInfo
import android.os.Build
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.assertions.getLogsOfType
import io.embrace.android.embracesdk.fakes.TestAeiData
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.setupFakeAeiData
import io.embrace.android.embracesdk.semconv.EmbAndroidAttributes
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.remote.AppExitInfoConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.semconv.EmbAeiAttributes
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

@Config(sdk = [Build.VERSION_CODES.R], shadows = [ShadowActivityManager::class])
@RunWith(AndroidJUnit4::class)
internal class AeiFeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

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
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Exit)
                log.assertContainsAeiData(jvmCrash)
            }
        )
    }

    @Test
    fun `native crash`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true)),
            setupAction = {
                setupFakeAeiData(listOf(nativeCrash.toAeiObject(), nativeCrash.toAeiObject()))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val logs = getLogEnvelopes(2).flatMap { it.getLogsOfType(EmbType.System.Exit) }
                logs[0].assertContainsAeiData(nativeCrash, "1", "1")
                logs[1].assertContainsAeiData(nativeCrash, "2", "2")
            }
        )
    }

    @Test
    fun `native crash disabled`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = false)),
            setupAction = {
                setupFakeAeiData(listOf(nativeCrash.toAeiObject()))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertEquals(0, getLogEnvelopes(0).size)
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
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Exit)
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
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Exit)
                log.assertContainsAeiData(anr)
            }
        )
    }

    @Test
    fun `aei limit exceeded`() {
        val expectedSize = 64

        testRule.runTest(
            setupAction = {
                val timestamps = 0..100L
                val aeis = timestamps.map { anr.copy(timestamp = it + fakeClock.now()) }.map(TestAeiData::toAeiObject)
                setupFakeAeiData(aeis)
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val envelopes = getLogEnvelopes(expectedSize)
                assertEquals(expectedSize, envelopes.size)
                val logs = envelopes.mapNotNull { it.data.logs?.singleOrNull() }
                    .filter { it.attributes?.findAttributeValue("emb.type") == "sys.exit" }
                assertEquals(expectedSize, logs.size)
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
        val obj = anr.copy(processStateSummary = "")
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
        val obj = anr.copy(processStateSummary = "invalid", sessionIdError = "invalid session ID: invalid")
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
        crashNumber: String = "null",
        aeiNumber: String = "null",
    ) {
        with(expected) {
            val sessionPartId = processStateSummary?.parseProcessStateSummary(0)
            val userSessionId = processStateSummary?.parseProcessStateSummary(1)
            attributes?.assertMatches(
                mapOf(
                    EmbAeiAttributes.TIMESTAMP to timestamp,
                    EmbAeiAttributes.AEI_SESSION_PART_ID to sessionPartId,
                    EmbAeiAttributes.AEI_USER_SESSION_ID to userSessionId,
                    EmbAeiAttributes.SESSION_ID_ERROR to sessionIdError,
                    EmbAeiAttributes.TRACE_STATUS to traceStatus,
                    EmbAeiAttributes.PROCESS_IMPORTANCE to importance,
                    EmbAeiAttributes.PSS to pss,
                    EmbAeiAttributes.RSS to rss,
                    EmbAeiAttributes.EXIT_STATUS to status,
                    EmbAeiAttributes.DESCRIPTION to description,
                    EmbAeiAttributes.REASON to reason,
                    "emb.type" to "sys.exit",
                    EmbAndroidAttributes.EMB_ANDROID_CRASH_NUMBER to crashNumber,
                    EmbAndroidAttributes.EMB_ANDROID_AEI_CRASH_NUMBER to aeiNumber,
                )
            )
            assertEquals(trace, body)
        }
    }

    private fun String.parseProcessStateSummary(k: Int): String {
        return runCatching {
            if (isNotEmpty() && length == 65) {
                split("_")[k]
            } else {
                ""
            }
        }.getOrDefault("")
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
