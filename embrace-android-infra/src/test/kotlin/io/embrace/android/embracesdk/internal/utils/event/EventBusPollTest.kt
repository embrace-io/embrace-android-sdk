package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

internal class EventBusPollTest {

    private class Reading(val name: String)

    // a query written by the caller, shared by every handler that understands it
    private class GranularityQuery(val hz: Int) : PollQuery

    // queries written by handlers for themselves, as their next query
    private class CountQuery(val count: Int) : PollQuery

    private class OffsetQuery(val offset: Long) : PollQuery

    private val logger = FakeInternalLogger(throwOnInternalError = false)
    private val bus = EventBus(logger)

    private val readingKey = PollKey<Reading>()

    private val otherReadingKey = PollKey<Reading>()

    private fun reading(name: String): PollHandler<Reading> = PollHandler { PollResult(Reading(name)) }

    /**
     * The names of the readings [poll] gathers, sorted as the order handlers respond in is not part of the contract.
     */
    private fun readingsFor(poll: BusPoll<Reading>): List<String?> =
        bus.gather(poll).map { it.result?.name }.sortedBy { it }

    /**
     * Creates keys until one has an id beyond any capacity an array could have been sized to while holding [key].
     */
    private fun <K : BusKey> keyBeyondCapacityFor(key: BusKey, create: () -> K): K =
        generateSequence(create).first { it.id >= INITIAL_CAPACITY && it.id > key.id * 2 }

    @Test
    fun `polling a key with no handlers gathers no results`() {
        bus.addPollHandler(otherReadingKey, reading("memory"))

        assertEquals(emptyList<PollResult<Reading>>(), bus.gather(BusPoll(readingKey)))
    }

    @Test
    fun `every poll handler registered against a key contributes a result`() {
        bus.addPollHandler(readingKey, reading("memory"))
        bus.addPollHandler(readingKey, reading("disk"))
        bus.addPollHandler(readingKey, reading("cpu"))

        assertEquals(listOf("cpu", "disk", "memory"), readingsFor(BusPoll(readingKey)))
    }

    @Test
    fun `a poll handler is not polled against another key of the same type`() {
        bus.addPollHandler(readingKey, reading("memory"))

        assertEquals(emptyList<String?>(), readingsFor(BusPoll(otherReadingKey)))
    }

    @Test
    fun `adding the same poll handler twice only registers it once`() {
        val handler = reading("memory")

        bus.addPollHandler(readingKey, handler)
        bus.addPollHandler(readingKey, handler)

        assertEquals(listOf("memory"), readingsFor(BusPoll(readingKey)))
    }

    @Test
    fun `a removed poll handler stops contributing results while the others remain`() {
        val memory = reading("memory")
        bus.addPollHandler(readingKey, memory)
        bus.addPollHandler(readingKey, reading("disk"))

        bus.removePollHandler(readingKey, memory)

        assertEquals(listOf("disk"), readingsFor(BusPoll(readingKey)))
    }

    @Test
    fun `removing an unregistered poll handler is a no-op`() {
        bus.addPollHandler(readingKey, reading("memory"))

        bus.removePollHandler(readingKey, reading("memory"))
        bus.removePollHandler(otherReadingKey, reading("memory"))

        assertEquals(listOf("memory"), readingsFor(BusPoll(readingKey)))
    }

    @Test
    fun `poll handlers survive the table growing to hold later keys`() {
        bus.addPollHandler(readingKey, reading("memory"))
        bus.addPollHandler(keyBeyondCapacityFor(readingKey) { PollKey<Reading>() }, reading("disk"))

        assertEquals("poll handler lost when the table grew", listOf("memory"), readingsFor(BusPoll(readingKey)))
    }

    @Test
    fun `a poll handler that throws contributes no result and does not stop the others`() {
        bus.addPollHandler(readingKey) { error("boom") }
        bus.addPollHandler(readingKey, reading("memory"))
        bus.addPollHandler(readingKey) { error("boom") }

        assertEquals(listOf("memory"), readingsFor(BusPoll(readingKey)))
        assertEquals(2, logger.internalErrorMessages.size)
    }

    @Test
    fun `a poll handler that throws on every poll is reported once`() {
        bus.addPollHandler(readingKey) { error("boom") }

        repeat(3) { bus.gather(BusPoll(readingKey)) }

        assertEquals(1, logger.internalErrorMessages.size)
    }

    @Test
    fun `a poll handler that threw is reported again once removed and re-registered`() {
        val handler = PollHandler<Reading> { error("boom") }

        bus.addPollHandler(readingKey, handler)
        bus.gather(BusPoll(readingKey))
        bus.removePollHandler(readingKey, handler)
        bus.addPollHandler(readingKey, handler)
        bus.gather(BusPoll(readingKey))

        assertEquals(2, logger.internalErrorMessages.size)
    }

    @Test
    fun `poll handler registered during a poll does not disturb the in-flight poll`() {
        bus.addPollHandler(readingKey) {
            bus.addPollHandler(readingKey, reading("disk"))
            PollResult(Reading("memory"))
        }

        assertEquals(
            "poll handler registered mid-poll was polled for the in-flight poll",
            listOf("memory"),
            readingsFor(BusPoll(readingKey)),
        )
        assertEquals(listOf("disk", "memory"), readingsFor(BusPoll(readingKey)))
    }

    @Test
    fun `a result without a value is still gathered, carrying its next query`() {
        val next = CountQuery(1)
        bus.addPollHandler(readingKey) { PollResult(null, next) }

        val results = bus.gather(BusPoll(readingKey))

        assertEquals(listOf<Reading?>(null), results.map { it.result })
        assertEquals(listOf<PollQuery>(next), results.nextQueries())
    }

    @Test
    fun `each poll handler finds only its own query among those a poll carries`() {
        val counts = mutableListOf<Int?>()
        val offsets = mutableListOf<Long?>()
        bus.addPollHandler(readingKey) { poll ->
            counts.add(poll.query<CountQuery>()?.count)
            PollResult(Reading("count"))
        }
        bus.addPollHandler(readingKey) { poll ->
            offsets.add(poll.query<OffsetQuery>()?.offset)
            PollResult(Reading("offset"))
        }

        bus.gather(BusPoll(readingKey, listOf(OffsetQuery(42), CountQuery(7))))

        assertEquals(listOf<Int?>(7), counts)
        assertEquals(listOf<Long?>(42), offsets)
    }

    @Test
    fun `a caller's query is read by every poll handler that understands it`() {
        val seen = mutableListOf<Int?>()
        bus.addPollHandler(readingKey) { poll ->
            seen.add(poll.query<GranularityQuery>()?.hz)
            PollResult(Reading("cpu"))
        }
        bus.addPollHandler(readingKey) { poll ->
            seen.add(poll.query<GranularityQuery>()?.hz)
            PollResult(Reading("memory"))
        }

        bus.gather(BusPoll(readingKey, listOf(GranularityQuery(4))))

        assertEquals(listOf<Int?>(4, 4), seen)
    }

    @Test
    fun `next queries gathered by one poll are carried into the following poll alongside the caller's own`() {
        val counts = mutableListOf<Int?>()
        val granularities = mutableListOf<Int?>()
        bus.addPollHandler(readingKey) { poll ->
            val previous = poll.query<CountQuery>()?.count
            counts.add(previous)
            granularities.add(poll.query<GranularityQuery>()?.hz)
            PollResult(Reading("count"), CountQuery((previous ?: 0) + 1))
        }
        bus.addPollHandler(readingKey, reading("stateless"))

        val granularity = GranularityQuery(4)
        var poll = BusPoll(readingKey, listOf(granularity))
        repeat(3) {
            poll = BusPoll(readingKey, listOf(granularity) + bus.gather(poll).nextQueries())
        }

        assertEquals(listOf(null, 1, 2), counts)
        assertEquals(listOf<Int?>(4, 4, 4), granularities)
    }

    @Test
    fun `event and poll handlers registered side by side each receive only their own dispatch`() {
        val eventKey = EventKey<String>()
        val otherEventKey = EventKey<String>()
        val received = AtomicInteger()
        val handler = EventHandler<String> { received.incrementAndGet() }
        bus.addHandler(eventKey, handler)
        bus.addPollHandler(readingKey, reading("memory"))
        bus.addHandler(otherEventKey) { received.incrementAndGet() }
        bus.addPollHandler(otherReadingKey, reading("disk"))
        bus.removeHandler(eventKey, handler)

        bus.emit(otherEventKey, "event")

        assertEquals(1, received.get())
        assertEquals(listOf("memory"), readingsFor(BusPoll(readingKey)))
        assertEquals(listOf("disk"), readingsFor(BusPoll(otherReadingKey)))
    }
}
