package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.Thread.sleep
import org.junit.Assert.*

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class LogRecordExporterTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            IntegrationTestRule.newHarness(startImmediately = false)
        }
    )

    @Test
    fun `SDK can receive a LogRecordExporter`() {
        with(testRule) {

            val fakeLogRecordExporter = FakeLogRecordExporter()
            embrace.addLogRecordExporter(fakeLogRecordExporter)
            embrace.start(harness.overriddenCoreModule.context)

            harness.recordSession {
                embrace.logMessage("test message", Severity.INFO)

                sleep(3000)
            }
            assertTrue((fakeLogRecordExporter.exportedLogs?.size ?: 0) > 0)
            assertEquals("test message", fakeLogRecordExporter.exportedLogs?.first()?.body?.asString())
        }
    }

    @Test
    fun `a LogRecordExporter added after initialization won't be used`() {
        with(testRule) {

            val fake = FakeInternalErrorService()
            harness.overriddenInitModule.logger.apply {
                internalErrorService = fake
            }

            val fakeLogRecordExporter = FakeLogRecordExporter()
            embrace.start(harness.overriddenCoreModule.context)
            embrace.addLogRecordExporter(fakeLogRecordExporter)

            harness.recordSession {
                embrace.logMessage("test message", Severity.INFO)

                sleep(3000)
            }
            assertTrue((fakeLogRecordExporter.exportedLogs?.size ?: 0) == 0)
        }
    }
}