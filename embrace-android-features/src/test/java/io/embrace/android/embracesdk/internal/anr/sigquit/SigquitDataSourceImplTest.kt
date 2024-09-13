package io.embrace.android.embracesdk.internal.anr.sigquit

import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.behavior.FakeAnrBehavior
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SigquitDataSourceImplTest {

    private lateinit var dataSource: SigquitDataSourceImpl
    private lateinit var sessionSpan: FakeCurrentSessionSpan

    @Before
    fun setUp() {
        sessionSpan = FakeCurrentSessionSpan()
        dataSource = SigquitDataSourceImpl(
            SharedObjectLoader(FakeEmbLogger()),
            AnrThreadIdDelegate(),
            FakeAnrBehavior(),
            FakeEmbLogger(),
            sessionSpan
        )
    }

    @Test
    fun `test google anr not saved`() {
        assertEquals(0, sessionSpan.addedEvents.size)
        dataSource.saveSigquit(100)
    }

    @Test
    fun `test save google anr`() {
        dataSource = SigquitDataSourceImpl(
            SharedObjectLoader(FakeEmbLogger()),
            AnrThreadIdDelegate(),
            FakeAnrBehavior(googleAnrCaptureEnabled = true),
            FakeEmbLogger(),
            sessionSpan
        )
        dataSource.saveSigquit(200)
        assertEquals(1, sessionSpan.addedEvents.size)
        val event = sessionSpan.addedEvents.single()
        assertEquals(SchemaType.Sigquit, event.schemaType)
        assertEquals(200, event.spanStartTimeMs)
    }
}
