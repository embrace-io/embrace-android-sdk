package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.fakes.system.mockContext
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class DataCaptureOrchestratorTest {

    private lateinit var orchestrator: DataCaptureOrchestrator
    private lateinit var dataSource: FakeDataSource
    private lateinit var configService: FakeConfigService
    private var enabled: Boolean = true

    @Before
    fun setUp() {
        dataSource = FakeDataSource(mockContext())
        configService = FakeConfigService()
        orchestrator = DataCaptureOrchestrator(
            configService,
            EmbLoggerImpl(),
        ).apply {
            add(
                DataSourceState(
                    factory = { dataSource },
                    configGate = { enabled }
                )
            )
        }
    }

    @Test
    fun `config changes are propagated`() {
        assertEquals(0, dataSource.enableDataCaptureCount)
        orchestrator.currentSessionType = SessionType.FOREGROUND
        assertEquals(1, dataSource.enableDataCaptureCount)

        enabled = false
        configService.updateListeners()
        assertEquals(1, dataSource.disableDataCaptureCount)
    }

    @Test
    fun `session type change is propagated`() {
        assertEquals(0, dataSource.enableDataCaptureCount)
        orchestrator.currentSessionType = SessionType.FOREGROUND
        assertEquals(1, dataSource.enableDataCaptureCount)
    }
}
