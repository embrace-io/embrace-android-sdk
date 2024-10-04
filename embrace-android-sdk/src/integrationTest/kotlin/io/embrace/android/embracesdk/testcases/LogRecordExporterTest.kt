package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.Thread.sleep

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class LogRecordExporterTest {

    private lateinit var fakeLogRecordExporter: FakeLogRecordExporter

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Before
    fun setup() {
        fakeLogRecordExporter = FakeLogRecordExporter()
    }

    @Test
    fun `SDK can receive a LogRecordExporter`() {
        testRule.runTest(
            preSdkStartAction = {
                embrace.addLogRecordExporter(fakeLogRecordExporter)
            },
            testCaseAction = {
                recordSession {
                    embrace.logMessage("test message", Severity.INFO)
                    // TODO: get rid of this
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
        testRule.runTest(
            setupAction = {
                overriddenInitModule.logger.apply {
                    internalErrorService = fake
                }
            },
            testCaseAction = {
                embrace.addLogRecordExporter(fakeLogRecordExporter)
                recordSession {
                    embrace.logMessage("test message", Severity.INFO)
                    // TODO: get rid of this
                    sleep(3000)
                }
            },
            assertAction = {
                assertTrue((fakeLogRecordExporter.exportedLogs?.size ?: 0) == 0)
            }
        )
    }
}
