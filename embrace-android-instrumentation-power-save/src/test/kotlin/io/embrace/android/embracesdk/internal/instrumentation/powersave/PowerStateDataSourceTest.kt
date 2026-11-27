package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.appStateEnabledConfigService
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.PowerState.PowerMode
import io.embrace.android.embracesdk.internal.state.StateDeclaration
import io.embrace.android.embracesdk.internal.state.StateEvents
import io.embrace.android.embracesdk.internal.state.StateUpdate
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import sun.misc.Unsafe

/**
 * The [PowerManager] these tests give the data source is the platform stub, which this module's unit tests run against with default
 * return values, so it always reports that power save mode is off. That is what the tests here need: the power state at the start
 * of a process whose device was never in power save mode.
 */
internal class PowerStateDataSourceTest {

    private val declarations = mutableListOf<StateDeclaration>()
    private val updates = mutableListOf<StateUpdate>()
    private lateinit var application: FakeApplication

    @Before
    fun setUp() {
        application = FakeApplication()
    }

    @Test
    fun `with the app state model on the power state declares unknown and reports normal at start`() {
        val source = PowerStateDataSource(argsWith(appStateModel = true))

        source.onDataCaptureEnabled()

        assertEquals(listOf("power"), declarations.map { it.definition.name })
        assertEquals(listOf("${PowerMode.UNKNOWN}"), declarations.map { it.initialValue })
        assertEquals(listOf("${PowerMode.NORMAL}"), updates.map { it.value })
        assertEquals(listOf(false), updates.map { it.system })
        assertEquals(PowerMode.NORMAL, source.getCurrentStateValue())
    }

    @Test
    fun `with the app state model on a power save broadcast that changes nothing is not reported again`() {
        PowerStateDataSource(argsWith(appStateModel = true)).onDataCaptureEnabled()

        broadcastPowerSaveModeChange()

        assertEquals(listOf("${PowerMode.NORMAL}"), updates.map { it.value })
    }

    @Test
    fun `with the app state model off the power state stays lazy and unknown`() {
        val args = argsWith(appStateModel = false)
        val source = PowerStateDataSource(args)

        source.onDataCaptureEnabled()
        broadcastPowerSaveModeChange()

        assertEquals(PowerMode.UNKNOWN, source.getCurrentStateValue())
        assertEquals(emptyList<Any>(), declarations)
        assertEquals(emptyList<Any>(), updates)
        assertEquals(emptyList<Any>(), args.destination.createdStateTokens)
    }

    private fun broadcastPowerSaveModeChange() {
        val intent = object : Intent() {
            override fun getAction(): String = PowerManager.ACTION_POWER_SAVE_MODE_CHANGED
        }
        application.receivers.single().onReceive(application, intent)
    }

    private fun argsWith(appStateModel: Boolean): FakeInstrumentationArgs {
        val powerManager = unsafe().allocateInstance(PowerManager::class.java)
        val args = FakeInstrumentationArgs(
            application = application,
            configService = if (appStateModel) appStateEnabledConfigService() else FakeConfigService(),
            systemServiceSupplier = { name -> powerManager.takeIf { name == Context.POWER_SERVICE } },
            sessionPartIdSupplier = { "part-id" },
        )
        args.eventBus.addHandler(StateEvents.DECLARED) { declarations.add(it) }
        args.eventBus.addHandler(StateEvents.UPDATED) { updates.add(it) }
        return args
    }

    private fun unsafe(): Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply {
        isAccessible = true
    }.get(null) as Unsafe
}
