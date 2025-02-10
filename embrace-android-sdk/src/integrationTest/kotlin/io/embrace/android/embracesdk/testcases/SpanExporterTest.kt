package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SpanExporterTest {
    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `SDK can receive a SpanExporter`() {
        val fakeSpanExporter = FakeSpanExporter()
        testRule.runTest(
            preSdkStartAction = {
                embrace.addSpanExporter(fakeSpanExporter)
            },
            testCaseAction = {
                recordSession {
                    embrace.startSpan("test")?.stop()
                }
            },
            assertAction = {
                assertNotNull(getSingleSessionEnvelope())
                val exportedSpans = fakeSpanExporter.exportedSpans.associateBy { it.name }
                assertNotNull(exportedSpans["test"])
                assertNotNull(exportedSpans["emb-session"])
            }
        )
    }

    @Test
    fun `a SpanExporter added after initialization won't be used`() {
        val fakeSpanExporter = FakeSpanExporter()

        testRule.runTest(
            testCaseAction = {
                embrace.addSpanExporter(fakeSpanExporter)
                recordSession {
                    embrace.startSpan("test")?.stop()
                }
            },
            assertAction = {
                assertNotNull(getSingleSessionEnvelope())
                assertTrue(fakeSpanExporter.exportedSpans.size == 0)
            }
        )
    }
}
