package io.embrace.android.embracesdk.internal.anr.sigquit

import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.createAnrBehavior
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SigquitDataSourceImplTest {

    private lateinit var dataSource: SigquitDataSourceImpl
    private lateinit var sessionSpan: FakeCurrentSessionSpan
    private lateinit var config: AnrRemoteConfig

    @Before
    fun setUp() {
        val logger = EmbLoggerImpl()
        sessionSpan = FakeCurrentSessionSpan()
        config = AnrRemoteConfig()
        dataSource = SigquitDataSourceImpl(
            SharedObjectLoader(logger),
            AnrThreadIdDelegate(),
            createAnrBehavior(remoteCfg = { config }),
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
