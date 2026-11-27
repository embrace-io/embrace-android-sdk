package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.config.FakeAppStateLocalConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.AppStateConfig
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveConfig
import io.embrace.android.embracesdk.internal.config.resolved.unreadBucket
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
        assertEquals(false, FakeConfigService().isStateAssociationEnabled())
    }

    @Test
    fun `state association is on only when both steps are on`() {
        assertEquals(
            true,
            service(stateCapture = true, appState = true, multiFile = true, association = true).isStateAssociationEnabled(),
        )
        assertEquals(
            false,
            service(stateCapture = true, appState = true, multiFile = true, association = false).isStateAssociationEnabled(),
        )
    }

    @Test
    fun `the second step on with the first off is off`() {
        assertEquals(
            false,
            service(stateCapture = true, appState = false, multiFile = true, association = true).isStateAssociationEnabled(),
        )
    }

    @Test
    fun `the second step is off when the first is off for want of state capture or a place to keep changes`() {
        assertEquals(
            false,
            service(stateCapture = false, appState = true, multiFile = true, association = true).isStateAssociationEnabled(),
        )
        assertEquals(
            false,
            service(stateCapture = true, appState = true, multiFile = false, association = true).isStateAssociationEnabled(),
        )
    }

    @Test
    fun `each key resolves from its local key, and a remote field overrides it`() {
        val local = FakeAppStateLocalConfig(enabled = true, associationEnabled = false, exportMode = "verbose")
        val fromLocal = resolveConfig(InstrumentedConfigImpl, null, unreadBucket, local).appState
        assertEquals(true, fromLocal.enabled)
        assertEquals(false, fromLocal.associationEnabled)
        assertEquals("verbose", fromLocal.exportMode)

        val remote =
            RemoteConfig(pctSharedStateSpanEnabled = 0f, pctStateAssociationEnabled = 100f, stateExportMode = "association")
        val fromRemote = resolveConfig(InstrumentedConfigImpl, remote, unreadBucket, local).appState
        assertEquals(false, fromRemote.enabled)
        assertEquals(true, fromRemote.associationEnabled)
        assertEquals("association", fromRemote.exportMode)
    }

    private fun service(
        stateCapture: Boolean,
        appState: Boolean,
        multiFile: Boolean,
        onlyOtel: Boolean = false,
        association: Boolean = false,
    ) = FakeConfigService(
        config = EmbraceConfig(
            autoDataCapture = { AutoDataCaptureConfig(stateCaptureEnabled = { stateCapture }) },
            appState = { AppStateConfig(enabled = { appState }, associationEnabled = { association }) },
            persistence = { PersistenceConfig(multiFileEnabled = { multiFile }) },
        ),
        onlyUsingOtelExporters = onlyOtel,
    )
}
