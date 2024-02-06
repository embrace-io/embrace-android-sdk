package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDataSource
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class DataCaptureOrchestratorTest {

    private lateinit var orchestrator: DataCaptureOrchestrator
    private lateinit var dataSource: FakeDataSource
    private var enabled: Boolean = true

    @Before
    fun setUp() {
        dataSource = FakeDataSource()
        orchestrator = DataCaptureOrchestrator(
            listOf(
                DataSourceState<FakeDataSource>(
                    factory = { dataSource },
                    configGate = { enabled },
                    currentEnvelope = null
                )
            )
        )
    }

    @Test
    fun `config changes are propagated`() {
        assertEquals(0, dataSource.registerCount)
        orchestrator.onEnvelopeChange(EnvelopeType.SESSION)
        assertEquals(1, dataSource.registerCount)

        enabled = false
        orchestrator.onConfigChange(FakeConfigService())
        assertEquals(1, dataSource.unregisterCount)
    }

    @Test
    fun `envelope type change is propagated`() {
        assertEquals(0, dataSource.registerCount)
        orchestrator.onEnvelopeChange(EnvelopeType.SESSION)
        assertEquals(1, dataSource.registerCount)
    }
}
