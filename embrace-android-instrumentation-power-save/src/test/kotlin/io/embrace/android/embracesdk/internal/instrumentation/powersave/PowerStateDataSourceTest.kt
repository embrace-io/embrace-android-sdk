package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.content.Intent
import android.os.PowerManager
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDeclaration
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.PowerState.PowerMode
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PowerStateDataSourceTest {

    private lateinit var application: FakeApplication
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var updates: MutableList<StateUpdate>
    private lateinit var declarations: MutableList<StateDeclaration>
    private var powerSaveMode = false

    @Before
    fun setUp() {
        application = FakeApplication()
        powerSaveMode = false
    }

    @Test
    fun `a journaled power state is unknown when capture starts, then normal or low as soon as the mode is read`() {
        val dataSource = dataSource(journaled = true)
        dataSource.onDataCaptureEnabled()

        // its default as capture is enabled, and the mode as soon as it is read, though power saving has never been on
        assertEquals(listOf<Any>(PowerMode.UNKNOWN, PowerMode.NORMAL), updates.map(StateUpdate::value))
        assertEquals(listOf("unknown", "normal"), updates.map { it.value.toString() })
        // it is declared once, before its first value, with the limit every state has and no attribute keys
        with(declarations.single()) {
            assertEquals("power", state)
            assertEquals(StateDeclaration.DEFAULT_MAX_CHANGES_IN_PART, maxChangesInPart)
            assertEquals(emptySet<String>(), attributeKeys)
        }

        changePowerSaveMode(true)
        changePowerSaveMode(false)
        assertEquals(
            listOf<Any>(PowerMode.UNKNOWN, PowerMode.NORMAL, PowerMode.LOW, PowerMode.NORMAL),
            updates.map(StateUpdate::value),
        )
        assertTrue(args.destination.createdStateTokens.isEmpty())
    }

    @Test
    fun `a journaled power state that starts with power saving on is unknown and then low`() {
        powerSaveMode = true
        dataSource(journaled = true).onDataCaptureEnabled()

        assertEquals(listOf<Any>(PowerMode.UNKNOWN, PowerMode.LOW), updates.map(StateUpdate::value))
    }

    @Test
    fun `a power state with a span of its own is not captured until power saving is first on`() {
        val dataSource = dataSource(journaled = false)
        dataSource.onDataCaptureEnabled()
        // the mode is read as normal, and changes to normal again
        changePowerSaveMode(false)

        assertFalse(dataSource.isActive())
        assertEquals(PowerMode.UNKNOWN, dataSource.getCurrentStateValue())
        assertTrue(args.destination.createdStateTokens.isEmpty())

        // the first time power saving is on starts the span, and from then on the mode going back to normal is captured too
        changePowerSaveMode(true)
        assertTrue(dataSource.isActive())
        changePowerSaveMode(false)
        val token = args.destination.createdStateTokens.single()
        assertEquals(listOf<Any>(PowerMode.LOW, PowerMode.NORMAL), token.transitions.map { it.second })
        // nothing is declared or reported for a journal
        assertTrue(updates.isEmpty())
        assertTrue(declarations.isEmpty())
    }

    private fun dataSource(journaled: Boolean): PowerStateDataSource {
        args = FakeInstrumentationArgs(
            application = application,
            configService = FakeConfigService(
                config = EmbraceConfig(
                    persistence = { PersistenceConfig(multiFileEnabled = { journaled }) },
                    autoDataCapture = { AutoDataCaptureConfig(sharedStateSpanEnabled = { journaled }) },
                ),
            ),
            sessionPartIdSupplier = { "part-id" },
        )
        updates = mutableListOf()
        declarations = mutableListOf()
        args.eventBus.addHandler(StateDeclaration.KEY) { declaration ->
            // nothing has been reported of a state when it is declared
            assertTrue(updates.isEmpty())
            declarations.add(declaration)
        }
        args.eventBus.addHandler(StateUpdate.KEY) { updates.add(it) }
        return PowerStateDataSource(args) { PowerSaveModeSource { powerSaveMode } }
    }

    /**
     * Sets power save mode, and tells the receiver the data source registered that it changed.
     */
    private fun changePowerSaveMode(on: Boolean) {
        powerSaveMode = on
        val intent = object : Intent() {
            override fun getAction(): String = PowerManager.ACTION_POWER_SAVE_MODE_CHANGED
        }
        application.receivers.single().onReceive(application, intent)
    }
}
