package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class LogRecordExporterTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Suppress("deprecation")
    @Test
    fun `SDK can receive a LogRecordExporter`() {
        val fakeLogRecordExporter = FakeLogRecordExporter()

        testRule.runTest(
            preSdkStartAction = {
                embrace.addLogRecordExporter(fakeLogRecordExporter)
            },
            testCaseAction = {
                recordSession {
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
                assertEquals("test message", fakeLogRecordExporter.exportedLogs?.first()?.body?.asString())
            }
        )
    }
}
