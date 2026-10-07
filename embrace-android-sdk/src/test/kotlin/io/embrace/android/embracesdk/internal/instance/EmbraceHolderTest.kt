package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EmbraceSdk
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.mockk.mockk
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceHolderTest {

    private val context = mockk<Context>()
    private val calls = mutableListOf<String>()
    private lateinit var instance: FakeInstance
    private lateinit var holder: EmbraceHolder
    private var factoryCalls = 0

    @Before
    fun setUp() {
        instance = FakeInstance(calls)
        holder = createHolder { instance }
    }

    @Test
    fun `constructing the public object does no SDK work`() {
        assertFalse(Embrace.isStarted)
        Embrace.logInfo("dropped")
        Embrace.setUserIdentifier("buffered")
    }

    @Test
    fun `start makes the started instance current`() {
        holder.start(context)
        assertSame(instance, holder.current)
        assertTrue(holder.isStarted)
    }

    @Test
    fun `repeated start keeps the existing instance`() {
        holder.start(context)
        holder.start(context)
        assertSame(instance, holder.current)
        assertEquals(1, factoryCalls)
        assertEquals(1, calls.count { it == "start" })
    }

    @Test
    fun `calls that must survive start are replayed around start, and others are dropped`() {
        holder.applicationInitStart()
        holder.addSpanExporter(mockk<SpanExporter>())
        holder.setUserIdentifier("user")
        holder.addUserSessionProperty("key", "value", PropertyScope.PERMANENT)
        holder.logInfo("dropped")
        holder.start(context)

        assertEquals(
            listOf(
                "applicationInitStart(1000)",
                "addSpanExporter",
                "start",
                "setUserIdentifier(user)",
                "addUserSessionProperty(key)",
            ),
            calls,
        )
    }

    @Test
    fun `buffered experiments keep the time they were tracked at`() {
        holder.trackExperiment("exp")
        holder.start(context)
        assertEquals(listOf("trackExperiments([1000])", "start"), calls)
    }

    @Test
    fun `calls made while the instance is starting are routed by phase`() {
        instance = FakeInstance(calls) {
            holder.addSpanExporter(mockk<SpanExporter>())
            holder.setUserIdentifier("during-start")
        }
        holder.start(context)
        assertEquals(listOf("start", "addSpanExporter", "setUserIdentifier(during-start)"), calls)
    }

    @Test
    fun `a call that resolved the pre-start instance before the swap reaches the started instance`() {
        val captured: EmbraceSdk = holder.current
        assertFalse(captured.isStarted)
        holder.start(context)
        captured.logInfo("after")
        assertTrue(captured.isStarted)
        assertEquals("logInfo(after)", calls.last())
    }

    @Test
    fun `a disabled SDK installs the no-op instance`() {
        instance = FakeInstance(calls, startSucceeds = false)
        holder.setUserIdentifier("user")
        holder.start(context)

        assertSame(DisabledEmbrace, holder.current)
        assertFalse(holder.isStarted)
        holder.logInfo("ignored")
        assertEquals(listOf("start"), calls)
    }

    @Test
    fun `a factory that throws installs the no-op instance`() {
        val warnings = mutableListOf<String>()
        holder = EmbraceHolder(factory = { error("boom") }, warn = { msg, _ -> warnings += msg })
        holder.start(context)
        assertSame(DisabledEmbrace, holder.current)
        assertEquals(listOf("Failed to start the Embrace SDK"), warnings)
    }

    @Test
    fun `disable swaps in the no-op instance`() {
        holder.start(context)
        holder.disable()
        assertSame(DisabledEmbrace, holder.current)
        assertEquals("disable", calls.last())
    }

    private fun createHolder(factory: () -> StartableEmbrace) = EmbraceHolder(
        factory = {
            factoryCalls++
            factory()
        },
        warn = { _, _ -> },
        now = { 1000 },
    )

    private class FakeInstance(
        private val calls: MutableList<String>,
        private val startSucceeds: Boolean = true,
        private val duringStart: () -> Unit = {},
    ) : ForwardingEmbraceSdk(), StartableEmbrace {

        private var started = false

        override fun delegate(): EmbraceSdk = DisabledEmbrace

        override fun start(context: Context, onStarted: () -> Unit) {
            calls += "start"
            duringStart()
            if (startSucceeds) {
                started = true
                onStarted()
            }
        }

        override val isStarted: Boolean get() = started

        override fun applicationInitStart(timeMs: Long) {
            calls += "applicationInitStart($timeMs)"
        }

        override fun addSpanExporter(spanExporter: SpanExporter) {
            calls += "addSpanExporter"
        }

        override fun setUserIdentifier(userId: String?) {
            calls += "setUserIdentifier($userId)"
        }

        override fun addUserSessionProperty(key: String, value: String, scope: PropertyScope): Boolean {
            calls += "addUserSessionProperty($key)"
            return true
        }

        override fun trackExperiments(experiments: List<TrackedExperiment>) {
            calls += "trackExperiments(${experiments.map { it.startedAt }})"
        }

        override fun logInfo(message: String) {
            calls += "logInfo($message)"
        }

        override fun disable() {
            calls += "disable"
        }
    }
}
