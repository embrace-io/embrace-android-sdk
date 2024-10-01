package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import java.lang.Thread.sleep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class LogRecordExporterTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(startImmediately = false)
    }

    @Test
    fun `SDK can receive a LogRecordExporter`() {
        val fakeLogRecordExporter = FakeLogRecordExporter()

        testRule.runTest(
            testCaseAction = {
                embrace.addLogRecordExporter(fakeLogRecordExporter)
                startSdk()

                recordSession {
                    embrace.logMessage("test message", Severity.INFO)
                    sleep(3000)
                }
            },
            assertAction = {
                assertTrue((fakeLogRecordExporter.exportedLogs?.size ?: 0) > 0)
                assertEquals("test message", fakeLogRecordExporter.exportedLogs?.first()?.body?.asString())
            }
        )
    }

    @Test
    fun `a LogRecordExporter added after initialization won't be used`() {
        val fake = FakeInternalErrorService()
        val fakeLogRecordExporter = FakeLogRecordExporter()

        testRule.runTest(
            setupAction = {
                overriddenInitModule.logger.apply {
                    internalErrorService = fake
                }
            },
            testCaseAction = {
                startSdk()
                embrace.addLogRecordExporter(fakeLogRecordExporter)

                recordSession {
                    embrace.logMessage("test message", Severity.INFO)

                    sleep(3000)
                }
            },
            assertAction = {
                assertTrue((fakeLogRecordExporter.exportedLogs?.size ?: 0) == 0)
            }
        )
    }
}