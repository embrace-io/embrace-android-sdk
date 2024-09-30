package io.embrace.android.embracesdk.features

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.findEventsOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.getSingleSession
import io.embrace.android.embracesdk.hasEventOfType
import io.embrace.android.embracesdk.recordSession
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
        with(testRule) {
            harness.recordSession {
                ctx.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            }
            val message = harness.getSingleSession()
            assertTrue(message.findSessionSpan().hasEventOfType(EmbType.Performance.MemoryWarning))
        }
    }

    @Test
    fun `memory warning limits`() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        with(testRule) {
            harness.recordSession {
                repeat(150) {
                    ctx.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                }
            }
            val message = harness.getSingleSession()
            val events = message.findSessionSpan().findEventsOfType(EmbType.Performance.MemoryWarning)
            assertEquals(10, events.size)
        }
    }
}
