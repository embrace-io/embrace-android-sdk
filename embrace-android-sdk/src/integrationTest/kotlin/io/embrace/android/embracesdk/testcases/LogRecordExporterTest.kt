package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class LogRecordExporterTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Suppress("deprecation")
    @Test
    fun `SDK can receive a LogRecordExporter`() {
        val fakeLogRecordExporter = FakeLogRecordExporter()
        var logTimestampNanos = 0L

        testRule.runTest(
            preSdkStartAction = {
                embrace.addLogRecordExporter(fakeLogRecordExporter)
            },
            testCaseAction = {
                recordSession {
                    logTimestampNanos = clock.nowInNanos()
                    embrace.logMessage("test message", Severity.INFO)
                }
            },
            otelExportAssertion = {
                val log = awaitLogs(1) { it.attributes.get(EmbType.System.Log.key.attributeKey) == EmbType.System.Log.value }
                with(log.single()) {
                    assertEquals("test message", body.asString())
                    assertEquals(logTimestampNanos, timestampEpochNanos)
                    assertEquals(logTimestampNanos, observedTimestampEpochNanos)
                }
            }
        )
    }
}
