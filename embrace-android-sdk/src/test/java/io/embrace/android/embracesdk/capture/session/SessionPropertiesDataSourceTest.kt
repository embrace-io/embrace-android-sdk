package io.embrace.android.embracesdk.capture.session

import io.embrace.android.embracesdk.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            InternalEmbraceLogger(),
        )
    }

    @Test
    fun `add and remove custom property`() {
        assertTrue(dataSource.addProperty("blah", "value"))
        assertEquals(SpanAttributeData("blah", "value"), fakeCurrentSessionSpan.addedAttributes.single())
        assertTrue(dataSource.removeProperty("blah"))
        assertFalse(dataSource.removeProperty("blah"))
        assertTrue(fakeCurrentSessionSpan.addedAttributes.isEmpty())
    }
}
