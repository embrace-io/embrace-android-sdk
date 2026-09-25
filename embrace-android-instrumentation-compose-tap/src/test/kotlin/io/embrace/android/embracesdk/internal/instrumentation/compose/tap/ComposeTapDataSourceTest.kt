package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.ui.TapSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ComposeTapDataSourceTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var source: ComposeTapDataSource
    private val taps = mutableListOf<TapSignal>()

    @Before
    fun setUp() {
        val application: Application = ApplicationProvider.getApplicationContext()
        args = FakeInstrumentationArgs(application)
        args.eventBus.addHandler<TapSignal> { taps.add(it) }
        source = ComposeTapDataSource(args)
    }

    @Test
    fun `a compose tap is emitted as a tap signal and not reported here`() {
        source.logComposeTap(Pair(126f, 309f), "my-button-id")

        assertEquals(listOf(TapSignal("my-button-id", 126f, 309f)), taps)
        assertTrue(args.destination.addedEvents.isEmpty())
    }

    @Test
    fun `a tap emitted with no handler registered is dropped`() {
        val isolated = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())

        ComposeTapDataSource(isolated).logComposeTap(Pair(1f, 2f), "my-button-id")

        assertTrue(isolated.destination.addedEvents.isEmpty())
        assertTrue(isolated.logger.errorMessages.isEmpty())
    }
}
