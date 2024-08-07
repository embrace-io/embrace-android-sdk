package io.embrace.android.embracesdk.internal.capture.session

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.internal.arch.schema.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SessionPropertiesDataSourceTest {
    private lateinit var dataSource: SessionPropertiesDataSource
    private lateinit var fakeCurrentSessionSpan: FakeCurrentSessionSpan

    @Before
    fun setUp() {
        fakeCurrentSessionSpan = FakeCurrentSessionSpan()
        dataSource = SessionPropertiesDataSource(
            FakeConfigService().sessionBehavior,
            fakeCurrentSessionSpan,
            EmbLoggerImpl(),
        )
    }

    @Test
    fun `add and remove session property`() {
        assertTrue(dataSource.addProperty("blah", "value"))
        assertEquals("value", fakeCurrentSessionSpan.getAttribute("blah".toSessionPropertyAttributeName()))
        assertTrue(dataSource.removeProperty("blah"))
        assertEquals(0, fakeCurrentSessionSpan.attributeCount())
    }
}
