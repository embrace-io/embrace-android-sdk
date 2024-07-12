package io.embrace.android.embracesdk.features

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.hasEventOfType
import io.embrace.android.embracesdk.recordSession
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
            val message = checkNotNull(harness.recordSession {
                ctx.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            })
            assertTrue(message.findSessionSpan().hasEventOfType(EmbType.Performance.MemoryWarning))
        }
    }
}
