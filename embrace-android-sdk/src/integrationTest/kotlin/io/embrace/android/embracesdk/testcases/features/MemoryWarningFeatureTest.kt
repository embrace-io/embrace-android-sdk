package io.embrace.android.embracesdk.testcases.features

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findEventsOfType
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.hasEventOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class MemoryWarningFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `memory warning`() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    @Suppress("DEPRECATION")
                    ctx.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                assertTrue(message.findSessionSpan().hasEventOfType(EmbType.Performance.MemoryWarning))
            }
        )
    }

    @Test
    fun `memory warning limits`() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    repeat(150) {
                        @Suppress("DEPRECATION")
                        ctx.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                    }
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val events = message.findSessionSpan().findEventsOfType(EmbType.Performance.MemoryWarning)
                assertEquals(10, events.size)
            }
        )
    }
}
