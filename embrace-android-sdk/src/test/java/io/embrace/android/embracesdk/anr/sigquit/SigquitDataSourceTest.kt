package io.embrace.android.embracesdk.anr.sigquit

import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SigquitDataSourceTest {

    private lateinit var dataSource: SigquitDataSource
    private lateinit var sessionSpan: FakeCurrentSessionSpan
    private lateinit var config: AnrRemoteConfig

    @Before
    fun setUp() {
        val logger = EmbLoggerImpl()
        sessionSpan = FakeCurrentSessionSpan()
        config = AnrRemoteConfig()
        dataSource = SigquitDataSource(
            SharedObjectLoader(logger),
            AnrThreadIdDelegate(logger),
            fakeAnrBehavior(remoteCfg = { config }),
            logger,
            sessionSpan
        )
    }

    @Test
    fun `test save google anr`() {
        assertEquals(0, sessionSpan.addedEvents.size)
        dataSource.saveSigquit(100)
        config = config.copy(googlePctEnabled = 100)
        dataSource.saveSigquit(200)
        assertEquals(1, sessionSpan.addedEvents.size)
        val event = sessionSpan.addedEvents.single()
        assertEquals(SchemaType.Sigquit, event.schemaType)
        assertEquals(200, event.spanStartTimeMs)
    }
}
