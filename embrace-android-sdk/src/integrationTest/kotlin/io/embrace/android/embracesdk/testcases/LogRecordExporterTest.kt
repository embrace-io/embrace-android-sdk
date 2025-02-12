package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            assertAction = {
                assertTrue(
                    returnIfConditionMet(
                        desiredValueSupplier = { true },
                        dataProvider = { fakeLogRecordExporter.exportedLogs?.size },
                        condition = { data ->
                            data == 1
                        },
                    )
                )
                with(checkNotNull(fakeLogRecordExporter.exportedLogs?.first())) {
                    assertEquals("test message", body.asString())
                    assertEquals(logTimestampNanos, timestampEpochNanos)
                    assertEquals(logTimestampNanos, observedTimestampEpochNanos)
                }
            }
        )
    }
}
