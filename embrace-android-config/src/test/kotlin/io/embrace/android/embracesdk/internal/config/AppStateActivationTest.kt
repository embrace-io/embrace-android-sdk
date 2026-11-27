package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.internal.config.resolved.AppStateConfig
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import org.junit.Assert.assertEquals
import org.junit.Test

internal class AppStateActivationTest {

    @Test
    fun `the app state model is on when all three conditions hold`() {
        assertEquals(true, service(stateCapture = true, appState = true, multiFile = true).isAppStateEnabled())
        assertEquals(
            true,
            service(stateCapture = true, appState = true, multiFile = false, onlyOtel = true).isAppStateEnabled(),
        )
        assertEquals(
            true,
            service(stateCapture = true, appState = true, multiFile = true, onlyOtel = true).isAppStateEnabled(),
        )
    }

    @Test
    fun `the app state model is off without its own switch`() {
        assertEquals(false, service(stateCapture = true, appState = false, multiFile = true).isAppStateEnabled())
        assertEquals(
            false,
            service(stateCapture = true, appState = false, multiFile = false, onlyOtel = true).isAppStateEnabled(),
        )
    }

    @Test
    fun `the app state model is off without state capture`() {
        assertEquals(false, service(stateCapture = false, appState = true, multiFile = true).isAppStateEnabled())
        assertEquals(
            false,
            service(stateCapture = false, appState = true, multiFile = false, onlyOtel = true).isAppStateEnabled(),
        )
    }

    @Test
    fun `the app state model is off with nowhere to keep a part's changes`() {
        assertEquals(false, service(stateCapture = true, appState = true, multiFile = false).isAppStateEnabled())
    }

    @Test
    fun `every combination of the conditions agrees with the rule`() {
        listOf(true, false).forEach { stateCapture ->
            listOf(true, false).forEach { appState ->
                listOf(true, false).forEach { multiFile ->
                    listOf(true, false).forEach { onlyOtel ->
                        val expected = stateCapture && appState && (multiFile || onlyOtel)
                        assertEquals(
                            "stateCapture=$stateCapture appState=$appState multiFile=$multiFile onlyOtel=$onlyOtel",
                            expected,
                            service(stateCapture, appState, multiFile, onlyOtel).isAppStateEnabled(),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the app state model is off by default`() {
        assertEquals(false, FakeConfigService().isAppStateEnabled())
    }

    private fun service(
        stateCapture: Boolean,
        appState: Boolean,
        multiFile: Boolean,
        onlyOtel: Boolean = false,
    ) = FakeConfigService(
        config = EmbraceConfig(
            autoDataCapture = { AutoDataCaptureConfig(stateCaptureEnabled = { stateCapture }) },
            appState = { AppStateConfig(enabled = { appState }) },
            persistence = { PersistenceConfig(multiFileEnabled = { multiFile }) },
        ),
        onlyUsingOtelExporters = onlyOtel,
    )
}
