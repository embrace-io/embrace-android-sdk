package io.embrace.android.embracesdk.capture.webview

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class WebViewDataSourceTest {

    private lateinit var clock: FakeClock
    private lateinit var writer: FakeCurrentSessionSpan
    private lateinit var dataSource: WebViewDataSource

    @Before
    fun setUp() {
        clock = FakeClock()
        writer = FakeCurrentSessionSpan()
        dataSource = WebViewDataSource(
            FakeConfigService().webViewVitalsBehavior,
            writer,
            InternalEmbraceLogger(),
            EmbraceSerializer()
        )
    }

    @Test
    fun `calling loadDataIntoSession with an empty list, doesn't add any event`() {
        dataSource.loadDataIntoSession(emptyList())
        assertEquals(0, writer.addedEvents.size)
    }
}
