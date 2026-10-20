package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Old API levels are otherwise only covered by simulating the version checks, so code that reads [Build.VERSION.SDK_INT]
 * directly only runs at Robolectric's default SDK. As a safety gate, this runs a session with a span and a log on the oldest
 * platform runtime Robolectric provides.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.M])
internal class OldApiSmokeTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = OtelSdkMode.COMPAT)

    @Test
    fun `a session with a span and a log is delivered`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.startSpan(SPAN_NAME).stop()
                    embrace.logInfo(LOG_MESSAGE)
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                session.findSpanByName(SPAN_NAME)
                assertEquals(Build.VERSION.SDK_INT.toString(), session.resource?.osCode)
                assertEquals(listOf(LOG_MESSAGE), getSingleLogEnvelope().data.logs?.map { it.body })
            },
        )
    }

    private companion object {
        const val SPAN_NAME = "old-api-span"
        const val LOG_MESSAGE = "old-api-log"
    }
}
