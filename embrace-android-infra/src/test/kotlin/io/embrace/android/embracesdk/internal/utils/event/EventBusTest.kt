package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

internal class EventBusTest {

    private sealed interface AppEvent

    private interface Loggable

    private open class BaseEvent : AppEvent

    private class ForegroundEvent : BaseEvent(), Loggable

    private class UnrelatedEvent

    // reached by two paths
    private interface Root

    private interface Left : Root

    private interface Right : Root

    private class DiamondEvent : Left, Right

    private class RightEvent : Right

    private class ScreenState(val name: String) : StickyEvent

    // a family that refines; Navigation is deliberately not a StickyEvent
    private interface Navigation {
        val activityName: String
    }

    private open class NavigationEvent(override val activityName: String) : Navigation, StickyEvent

    private class FragmentNav(val fragmentName: String, activityName: String) : NavigationEvent(activityName)

    // a family that alternates
    private sealed interface ConnectionState : StickyEvent

    private class Connected(val ssid: String) : ConnectionState

    private object Disconnected : ConnectionState

    // two unrelated families, so neither declarer is senior
    private interface FirstFamily : StickyEvent {
        val name: String
    }

    private interface SecondFamily : StickyEvent

    private class TwoFamilyEvent(override val name: String) : FirstFamily, SecondFamily

    private class FirstFamilyOnlyEvent(override val name: String) : FirstFamily

    // a redundant redeclaration, so the declarers are not in seniority order
    private interface SeniorFamily : StickyEvent {
        val name: String
    }

    private interface JuniorFamily : SeniorFamily, StickyEvent

    private class SeniorOnlyEvent(override val name: String) : SeniorFamily

    private class RedeclaredEvent(override val name: String) : SeniorFamily, JuniorFamily

    private val logger = FakeInternalLogger(throwOnInternalError = false)
    private val bus = EventBus(logger)

    @Test
    fun `handler receives events of its own type`() {
        val received = mutableListOf<ForegroundEvent>()
        bus.addHandler<ForegroundEvent> { received.add(it) }

        val event = ForegroundEvent()
        bus.emit(event)

        assertEquals(listOf(event), received)
    }

    @Test
    fun `handler receives events of a subtype`() {
        val received = mutableListOf<BaseEvent>()
        bus.addHandler<BaseEvent> { received.add(it) }

        val event = ForegroundEvent()
        bus.emit(event)

        assertEquals(listOf<BaseEvent>(event), received)
    }

    @Test
    fun `handler registered against a sealed interface receives events of an implementing type`() {
        val received = mutableListOf<AppEvent>()
        bus.addHandler<AppEvent> { received.add(it) }

        val event = ForegroundEvent()
        bus.emit(event)

        assertEquals(listOf<AppEvent>(event), received)
    }

    @Test
    fun `handler registered against an interface implemented directly receives events`() {
        val received = mutableListOf<Loggable>()
        bus.addHandler<Loggable> { received.add(it) }

        val event = ForegroundEvent()
        bus.emit(event)

        assertEquals(listOf<Loggable>(event), received)
    }

    @Test
    fun `handlers are invoked most specific type first`() {
        val order = mutableListOf<String>()
        bus.addHandler<AppEvent> { order.add("interface") }
        bus.addHandler<BaseEvent> { order.add("superclass") }
        bus.addHandler<ForegroundEvent> { order.add("concrete") }

        bus.emit(ForegroundEvent())

        assertEquals(listOf("concrete", "superclass", "interface"), order)
    }

    @Test
    fun `handler registered against a type reachable by multiple paths is invoked once`() {
        // ForegroundEvent implements Loggable directly, and reaches AppEvent via BaseEvent.
        val appEventCount = AtomicInteger()
        bus.addHandler<AppEvent> { appEventCount.incrementAndGet() }
        // Register the same type again via BaseEvent's hierarchy to ensure the walk dedupes types, not just handlers.
        bus.addHandler<BaseEvent> { }

        bus.emit(ForegroundEvent())

        assertEquals(1, appEventCount.get())
    }

    @Test
    fun `interfaces declared on a type are invoked before its superclass`() {
        val order = mutableListOf<String>()
        bus.addHandler<AppEvent> { order.add("AppEvent") }
        bus.addHandler<BaseEvent> { order.add("BaseEvent") }
        bus.addHandler<Loggable> { order.add("Loggable") }
        bus.addHandler<ForegroundEvent> { order.add("ForegroundEvent") }

        bus.emit(ForegroundEvent())

        assertEquals(listOf("ForegroundEvent", "Loggable", "BaseEvent", "AppEvent"), order)
    }

    @Test
    fun `a type reachable by two paths is invoked at its first position, not its last`() {
        val order = mutableListOf<String>()
        bus.addHandler<Root> { order.add("Root") }
        bus.addHandler<Left> { order.add("Left") }
        bus.addHandler<Right> { order.add("Right") }

        bus.emit(DiamondEvent())

        assertEquals(listOf("Left", "Root", "Right"), order)
    }

    @Test
    fun `resolving a subtype leaves a supertype's own hierarchy complete`() {
        val received = mutableListOf<String>()
        bus.addHandler<Root> { received.add("Root") }

        // DiamondEvent reaches Root through Left, so Right must not be left without it
        bus.emit(DiamondEvent())
        received.clear()
        bus.emit(RightEvent())

        assertEquals("Right lost the Root reached by a subtype's walk", listOf("Root"), received)
    }

    @Test
    fun `handler registered against Any never receives events`() {
        val received = AtomicInteger()
        bus.addHandler<Any> { received.incrementAndGet() }

        bus.emit(ForegroundEvent())
        bus.emit(UnrelatedEvent())

        assertEquals(0, received.get())
    }

    @Test
    fun `handler does not receive events of unrelated types`() {
        val received = AtomicInteger()
        bus.addHandler<ForegroundEvent> { received.incrementAndGet() }

        bus.emit(UnrelatedEvent())

        assertEquals(0, received.get())
    }

    @Test
    fun `emitting an event with no handlers does nothing`() {
        bus.addHandler<UnrelatedEvent> { }

        bus.emit(ForegroundEvent())
    }

    @Test
    fun `adding the same handler twice only registers it once`() {
        val received = AtomicInteger()
        val handler = EventHandler<ForegroundEvent> { received.incrementAndGet() }

        bus.addHandler(ForegroundEvent::class.java, handler)
        bus.addHandler(ForegroundEvent::class.java, handler)
        bus.emit(ForegroundEvent())

        assertEquals(1, received.get())
    }

    @Test
    fun `removed handler stops receiving events`() {
        val received = AtomicInteger()
        val handler = EventHandler<BaseEvent> { received.incrementAndGet() }
        bus.addHandler(BaseEvent::class.java, handler)

        bus.emit(ForegroundEvent())
        bus.removeHandler(BaseEvent::class.java, handler)
        bus.emit(ForegroundEvent())

        assertEquals("handler invoked after removal", 1, received.get())
    }

    @Test
    fun `removing an unregistered handler is a no-op`() {
        val received = AtomicInteger()
        bus.addHandler<ForegroundEvent> { received.incrementAndGet() }

        bus.removeHandler(ForegroundEvent::class.java, EventHandler { })
        bus.removeHandler(UnrelatedEvent::class.java, EventHandler { })
        bus.emit(ForegroundEvent())

        assertEquals(1, received.get())
    }

    @Test
    fun `handler added after an event type has been dispatched receives subsequent events`() {
        val first = AtomicInteger()
        val second = AtomicInteger()
        bus.addHandler<ForegroundEvent> { first.incrementAndGet() }

        // Dispatch once so the type hierarchy is resolved and cached, then invalidate it by registering against a supertype.
        bus.emit(ForegroundEvent())
        bus.addHandler<AppEvent> { second.incrementAndGet() }
        bus.emit(ForegroundEvent())

        assertEquals(2, first.get())
        assertEquals("handler added after the first dispatch was not picked up", 1, second.get())
    }

    @Test
    fun `a handler that throws during dispatch does not stop the handlers after it`() {
        val received = AtomicInteger()
        bus.addHandler<ForegroundEvent> { error("boom") }
        bus.addHandler<ForegroundEvent> { received.incrementAndGet() }

        bus.emit(ForegroundEvent())

        assertEquals("the handler after the throwing one was not invoked", 1, received.get())
        assertEquals(listOf(InternalErrorType.EventBusHandlerFail.toString()), logger.internalErrorMessages.map { it.msg })
    }

    @Test
    fun `a handler that throws on every event is reported once`() {
        bus.addHandler<ForegroundEvent> { error("boom") }

        repeat(3) { bus.emit(ForegroundEvent()) }

        assertEquals(1, logger.internalErrorMessages.size)
    }

    @Test
    fun `a handler that threw is reported again once removed and re-registered`() {
        val handler = EventHandler<ForegroundEvent> { error("boom") }

        bus.addHandler(ForegroundEvent::class.java, handler)
        bus.emit(ForegroundEvent())
        bus.removeHandler(ForegroundEvent::class.java, handler)
        bus.addHandler(ForegroundEvent::class.java, handler)
        bus.emit(ForegroundEvent())

        assertEquals(2, logger.internalErrorMessages.size)
    }

    @Test
    fun `handler registered during an emit does not disturb the in-flight dispatch`() {
        val added = AtomicInteger()
        bus.addHandler<ForegroundEvent> {
            bus.addHandler<ForegroundEvent> { added.incrementAndGet() }
        }

        bus.emit(ForegroundEvent())
        assertEquals("handler registered mid-emit was invoked for the in-flight event", 0, added.get())

        bus.emit(ForegroundEvent())
        assertEquals(1, added.get())
    }

    @Test
    fun `a handler registered before a sticky emit receives it once, via normal dispatch`() {
        val received = mutableListOf<String>()
        bus.addHandler<ScreenState> { received.add(it.name) }

        bus.emit(ScreenState("home"))

        assertEquals(listOf("home"), received)
    }

    @Test
    fun `a handler registered after a sticky emit is immediately replayed the most recent event`() {
        bus.emit(ScreenState("home"))

        val received = mutableListOf<String>()
        bus.addHandler<ScreenState> { received.add(it.name) }

        assertEquals(listOf("home"), received)
    }

    @Test
    fun `a late handler is replayed only the most recent sticky event, not every prior one`() {
        bus.emit(ScreenState("home"))
        bus.emit(ScreenState("details"))

        val received = mutableListOf<String>()
        bus.addHandler<ScreenState> { received.add(it.name) }

        assertEquals(listOf("details"), received)
    }

    @Test
    fun `registering the same handler twice for a sticky type does not replay twice`() {
        bus.emit(ScreenState("home"))

        val received = mutableListOf<String>()
        val handler = EventHandler<ScreenState> { received.add(it.name) }
        bus.addHandler(ScreenState::class.java, handler)
        bus.addHandler(ScreenState::class.java, handler)

        assertEquals(listOf("home"), received)
    }

    @Test
    fun `a non-sticky type is not replayed to a handler registered after emit`() {
        bus.emit(UnrelatedEvent())

        val received = AtomicInteger()
        bus.addHandler<UnrelatedEvent> { received.incrementAndGet() }

        assertEquals(0, received.get())
    }

    @Test
    fun `a handler that throws during sticky replay is reported once, like a live dispatch failure`() {
        bus.emit(ScreenState("home"))

        bus.addHandler<ScreenState> { error("boom") }

        assertEquals(listOf(InternalErrorType.EventBusHandlerFail.toString()), logger.internalErrorMessages.map { it.msg })
    }

    @Test
    fun `a handler registered against an unmarked supertype of a sticky type is still replayed`() {
        bus.emit(NavigationEvent("main"))

        val received = mutableListOf<String>()
        bus.addHandler<Navigation> { received.add(it.activityName) }

        assertEquals(listOf("main"), received)
    }

    @Test
    fun `a handler registered against Any is not replayed a sticky event`() {
        bus.emit(ScreenState("home"))

        val received = AtomicInteger()
        bus.addHandler<Any> { received.incrementAndGet() }

        assertEquals(0, received.get())
    }

    @Test
    fun `a subclass emit supersedes the retained superclass value`() {
        bus.emit(NavigationEvent("main"))
        bus.emit(FragmentNav("details", "main"))

        val received = mutableListOf<String>()
        bus.addHandler<NavigationEvent> { received.add(it.javaClass.simpleName) }

        assertEquals(listOf("FragmentNav"), received)
    }

    @Test
    fun `a superclass emit supersedes the retained subclass value`() {
        bus.emit(FragmentNav("details", "main"))
        bus.emit(NavigationEvent("settings"))

        val onNavigation = mutableListOf<String>()
        val onFragment = mutableListOf<String>()
        bus.addHandler<NavigationEvent> { onNavigation.add(it.activityName) }
        bus.addHandler<FragmentNav> { onFragment.add(it.fragmentName) }

        assertEquals(listOf("settings"), onNavigation)
        assertEquals("a fragment we have navigated away from was replayed", emptyList<String>(), onFragment)
    }

    @Test
    fun `alternative values of one sealed family share a single retained slot`() {
        bus.emit(Connected("home-wifi"))
        bus.emit(Disconnected)

        val received = mutableListOf<String>()
        bus.addHandler<ConnectionState> { received.add(it.javaClass.simpleName) }

        assertEquals(listOf("Disconnected"), received)
    }

    @Test
    fun `a handler on one value of a family is not replayed while another value is current`() {
        bus.emit(Connected("home-wifi"))
        bus.emit(Disconnected)

        val received = mutableListOf<String>()
        bus.addHandler<Connected> { received.add(it.ssid) }

        assertEquals(emptyList<String>(), received)
    }

    @Test
    fun `a class reaching StickyEvent through two unrelated families is retained against itself`() {
        bus.emit(FirstFamilyOnlyEvent("first"))
        bus.emit(TwoFamilyEvent("two"))

        val received = mutableListOf<String>()
        bus.addHandler<FirstFamily> { received.add(it.name) }

        // TwoFamilyEvent keys against itself, so it cannot supersede the FirstFamily slot
        assertEquals(setOf("first", "two"), received.toSet())
    }

    @Test
    fun `the most senior declarer wins when a subtype redundantly redeclares StickyEvent`() {
        bus.emit(SeniorOnlyEvent("first"))
        bus.emit(RedeclaredEvent("second"))

        val received = mutableListOf<String>()
        bus.addHandler<SeniorFamily> { received.add(it.name) }

        // both key against SeniorFamily, so the second supersedes the first
        assertEquals(listOf("second"), received)
    }
}
