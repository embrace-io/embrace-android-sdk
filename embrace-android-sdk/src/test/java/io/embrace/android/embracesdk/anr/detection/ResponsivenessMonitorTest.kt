package io.embrace.android.embracesdk.anr.detection

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.payload.ResponsivenessOutlier
import io.embrace.android.embracesdk.payload.ResponsivenessSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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
        defaultGaps.forEach {
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
        val outlier1Start = clock.now()
        clock.tick(defaultOutlierThreshold + 1)
        responsivenessMonitor.ping()
        val outlier2Start = clock.now()
        clock.tick(5000)
        responsivenessMonitor.ping()
        val outlier2End = clock.now()
        with(responsivenessMonitor.snapshot()) {
            assertEquals("test", name)
            assertEquals(startTime, firstPing)
            assertEquals(outlier2End, lastPing)
            gaps.forEach {
                assertEquals("Bucket ${it.key} - ", if (it.key == 120L) 2L else 1L, gaps[it.key])
            }
            assertEquals(2, outliers.size)
            assertEquals(outlier1Start, outliers.first().startMs)
            assertEquals(outlier2Start, outliers.first().endMs)
            assertEquals(ResponsivenessOutlier(outlier2Start, outlier2End), outliers.last())
        }
    }

    @Test
    fun `test bucket boundaries`() {
        responsivenessMonitor.ping()
        defaultBuckets.forEach {
            clock.tick(it)
            responsivenessMonitor.ping()
        }
        with(responsivenessMonitor.snapshot()) {
            assertEquals("test", name)
            assertEquals(startTime, firstPing)
            assertEquals(clock.now(), lastPing)
            gaps.forEach {
                assertEquals("Bucket ${it.key} - ", if (it.key == 120L) 0L else 1L, gaps[it.key])
            }
            assertEquals(2, outliers.size)
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
                assertEquals("Bucket ${it.key} - ", if (it.key == 120L) 2L else 0L, gaps[it.key])
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
                assertEquals("Bucket ${it.key} - ", if (it.key == 2000L) 100L else 0L, gaps[it.key])
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
                assertEquals("Bucket ${it.key} - ", if (it.key == 2000L) 101L else 0L, gaps[it.key])
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

    private fun checkEmptyState(snapshot: ResponsivenessSnapshot) {
        with(snapshot) {
            assertEquals("test", name)
            assertEquals(-1L, firstPing)
            assertEquals(-1L, lastPing)
            assertEquals(5, gaps.size)
            gaps.forEach {
                assertEquals(0L, gaps[it.key])
            }
            assertEquals(0, outliers.size)
            assertEquals(0, errors)
        }
    }

    companion object {
        private const val defaultOutlierThreshold = 500L
        private const val defaultOutlierLimit = 100
        private val defaultBuckets = listOf(120L, 250L, 500L, 2000L, Long.MAX_VALUE)
        private val defaultGaps = defaultBuckets.dropLast(1).map { it - 1 }
        private val noOutlierGaps = defaultGaps.filter { it <= 500L }
    }
}
