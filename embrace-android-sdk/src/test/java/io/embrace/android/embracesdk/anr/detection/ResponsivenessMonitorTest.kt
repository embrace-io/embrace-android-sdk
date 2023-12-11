package io.embrace.android.embracesdk.anr.detection

import io.embrace.android.embracesdk.anr.detection.ResponsivenessMonitor.Companion.defaultOutlierLimit
import io.embrace.android.embracesdk.anr.detection.ResponsivenessMonitor.Companion.defaultOutlierThreshold
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.payload.ResponsivenessOutlier
import io.embrace.android.embracesdk.payload.ResponsivenessSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class ResponsivenessMonitorTest {

    private val startTime: Long = System.currentTimeMillis()
    private lateinit var clock: FakeClock
    private lateinit var responsivenessMonitor: ResponsivenessMonitor

    @Before
    fun setup() {
        clock = FakeClock(currentTime = startTime)
        responsivenessMonitor = ResponsivenessMonitor(clock = clock, name = "test")
    }

    @Test
    fun `check empty snapshot state`() {
        checkEmptyState(responsivenessMonitor.snapshot())
    }

    @Test
    fun `check reset`() {
        responsivenessMonitor.ping()
        testGaps.forEach {
            clock.tick(it)
            responsivenessMonitor.ping()
        }
        clock.setCurrentTime(clock.now() - 1)
        responsivenessMonitor.ping()
        responsivenessMonitor.reset()
        checkEmptyState(responsivenessMonitor.snapshot())
    }

    @Test
    fun `test basic case`() {
        responsivenessMonitor.ping()
        clock.tick()
        responsivenessMonitor.ping()
        noOutlierGaps.forEach {
            clock.tick(it)
            responsivenessMonitor.ping()
        }
        val outlierPings = mutableListOf<Long>()
        outlierPings.add(clock.now())
        clock.tick(defaultOutlierThreshold + 1)
        responsivenessMonitor.ping()
        outlierPings.add(clock.now())
        clock.tick(1001L)
        responsivenessMonitor.ping()
        outlierPings.add(clock.now())
        clock.tick(2001L)
        responsivenessMonitor.ping()
        outlierPings.add(clock.now())
        with(responsivenessMonitor.snapshot()) {
            assertEquals("test", name)
            assertEquals(startTime, firstPing)
            assertEquals(outlierPings.last(), lastPing)
            gaps.forEach {
                assertEquals("Bucket ${it.key} - ", if (it.key == ResponsivenessMonitor.Bucket.B1.name) 2L else 1L, gaps[it.key])
            }
            assertEquals(3, outliers.size)
            assertEquals(4, outlierPings.size)
            val outlierPingsIterator = outlierPings.iterator()
            var lastPingTime = outlierPingsIterator.next()
            outliers.forEach { outlier ->
                val currentPingTime = outlierPingsIterator.next()
                assertEquals(lastPingTime, outlier.startMs)
                assertEquals(currentPingTime, outlier.endMs)
                assertEquals(ResponsivenessOutlier(lastPingTime, currentPingTime), outlier)
                lastPingTime = currentPingTime
            }
        }
    }

    @Test
    fun `test bucket boundaries`() {
        responsivenessMonitor.ping()
        ResponsivenessMonitor.Bucket.values().forEach {
            clock.tick(it.max)
            responsivenessMonitor.ping()
        }
        with(responsivenessMonitor.snapshot()) {
            assertEquals("test", name)
            assertEquals(startTime, firstPing)
            assertEquals(clock.now(), lastPing)
            gaps.forEach {
                assertEquals("Bucket ${it.key} - ", if (it.key == ResponsivenessMonitor.Bucket.B1.name) 0L else 1L, gaps[it.key])
            }
            assertEquals(3, outliers.size)
            assertEquals(1, errors)
        }
    }

    @Test
    fun `test errors`() {
        responsivenessMonitor.ping()
        // negative gap
        clock.setCurrentTime(0L)
        responsivenessMonitor.ping()
        // max value gap
        clock.setCurrentTime(Long.MAX_VALUE)
        responsivenessMonitor.ping()
        with(responsivenessMonitor.snapshot()) {
            assertEquals("test", name)
            assertEquals(startTime, firstPing)
            assertEquals(clock.now(), lastPing)
            gaps.forEach {
                assertEquals("Bucket ${it.key} - ", 0L, gaps[it.key])
            }
            assertEquals(0, outliers.size)
            assertEquals(2, errors)
        }
    }

    @Test
    fun `test no difference gaps`() {
        responsivenessMonitor.ping()
        responsivenessMonitor.ping()
        responsivenessMonitor.ping()
        with(responsivenessMonitor.snapshot()) {
            assertEquals("test", name)
            assertEquals(startTime, firstPing)
            assertEquals(clock.now(), lastPing)
            gaps.forEach {
                assertEquals("Bucket ${it.key} - ", if (it.key == ResponsivenessMonitor.Bucket.B1.name) 2L else 0L, gaps[it.key])
            }
            assertEquals(0, outliers.size)
            assertEquals(0, errors)
        }
    }

    @Test
    fun `test outlier limit`() {
        responsivenessMonitor.ping()
        repeat(defaultOutlierLimit) {
            clock.tick(defaultOutlierThreshold + 1)
            responsivenessMonitor.ping()
        }

        with(responsivenessMonitor.snapshot()) {
            assertEquals("test", name)
            assertEquals(startTime, firstPing)
            assertEquals(clock.now(), lastPing)
            gaps.forEach {
                assertEquals("Bucket ${it.key} - ", if (it.key == ResponsivenessMonitor.Bucket.B4.name) 100L else 0L, gaps[it.key])
            }
            assertEquals(100, outliers.size)
            assertEquals(0, errors)
        }

        clock.tick(defaultOutlierThreshold + 1)
        responsivenessMonitor.ping()

        with(responsivenessMonitor.snapshot()) {
            assertEquals(startTime, firstPing)
            assertEquals(clock.now(), lastPing)
            gaps.forEach {
                assertEquals("Bucket ${it.key} - ", if (it.key == ResponsivenessMonitor.Bucket.B4.name) 101L else 0L, gaps[it.key])
            }
            assertEquals(100, outliers.size)
            assertEquals(0, errors)
        }
    }

    @Test
    fun `test snapshotting`() {
        responsivenessMonitor.ping()
        clock.tick()
        responsivenessMonitor.ping()
        noOutlierGaps.forEach {
            clock.tick(it)
            responsivenessMonitor.ping()
        }
        clock.tick(5000)
        responsivenessMonitor.ping()
        val snapshot1 = responsivenessMonitor.snapshot()
        val snapshot2 = responsivenessMonitor.snapshot()
        assertNotSame(snapshot1, snapshot2)
        assertEquals(snapshot1, snapshot2)
    }

    @Test
    fun `natural sort order of buckets is sorted by the max value`() {
        var lastValue = 0L
        ResponsivenessMonitor.Bucket.values().forEach {
            assertTrue("Bucket ${it.name} has a smaller max value that expected", it.max > lastValue)
            lastValue = it.max
        }
    }

    private fun checkEmptyState(snapshot: ResponsivenessSnapshot) {
        with(snapshot) {
            assertEquals("test", name)
            assertEquals(-1L, firstPing)
            assertEquals(-1L, lastPing)
            assertEquals(6, gaps.size)
            gaps.forEach {
                assertEquals(0L, gaps[it.key])
            }
            assertEquals(0, outliers.size)
            assertEquals(0, errors)
        }
    }

    companion object {
        private val testGaps = ResponsivenessMonitor.Bucket.values().dropLast(1).map { it.max - 1 }
        private val noOutlierGaps = testGaps.filter { it <= defaultOutlierThreshold }
    }
}
