package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.content.Context
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class PowerSaveModeReceiverTest {

    private val context = FlagRecordingContext()

    @Test
    fun `receiver is registered as not exported from API 33`() {
        register(versionAtLeast = true)
        assertEquals(Context.RECEIVER_NOT_EXPORTED, context.flags.single())
    }

    @Test
    fun `receiver is registered without flags below API 33`() {
        register(versionAtLeast = false)
        assertNull(context.flags.single())
    }

    private fun register(versionAtLeast: Boolean) {
        PowerSaveModeReceiver(
            FakeErrorHandler(),
            { PowerSaveModeSource { false } },
            {},
            { versionAtLeast },
        ).register(context, fakeBackgroundWorker())
    }
}
