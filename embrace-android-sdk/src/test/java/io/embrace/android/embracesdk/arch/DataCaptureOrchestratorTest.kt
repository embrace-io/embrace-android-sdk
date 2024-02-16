package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.fakes.system.mockContext
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class DataCaptureOrchestratorTest {

    private lateinit var orchestrator: DataCaptureOrchestrator
    private lateinit var dataSource: FakeDataSource
    private var enabled: Boolean = true

    @Before
    fun setUp() {
        dataSource = FakeDataSource(mockContext())
        orchestrator = DataCaptureOrchestrator(
            listOf(
                DataSourceState(
                    factory = { dataSource },
                    configGate = { enabled },
                    currentSessionType = null
                )
            )
        )
    }

    @Test
    fun `config changes are propagated`() {
        assertEquals(0, dataSource.enableDataCaptureCount)
        orchestrator.onSessionTypeChange(SessionType.FOREGROUND)
        assertEquals(1, dataSource.enableDataCaptureCount)

        enabled = false
        orchestrator.onConfigChange(FakeConfigService())
        assertEquals(1, dataSource.disableDataCaptureCount)
    }

    @Test
    fun `session type change is propagated`() {
        assertEquals(0, dataSource.enableDataCaptureCount)
        orchestrator.onSessionTypeChange(SessionType.FOREGROUND)
        assertEquals(1, dataSource.enableDataCaptureCount)
    }
}
