package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.instrumentation.view.taps.TapDataSource
import io.embrace.android.embracesdk.internal.instrumentation.view.taps.tapDataSource
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class ClickBytecodeEntrypointTest {

    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs()
        tapDataSource = TapDataSource(args)
    }

    @After
    fun tearDown() {
        tapDataSource = null
    }

    @Test
    fun `null view on click is ignored`() {
        OnClickBytecodeEntrypoint.onClick(null)
        assertTrue(args.destination.addedEvents.isEmpty())
    }

    @Test
    fun `null view on long click is ignored`() {
        OnLongClickBytecodeEntrypoint.onLongClick(null)
        assertTrue(args.destination.addedEvents.isEmpty())
    }
}
