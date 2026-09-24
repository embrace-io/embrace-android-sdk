package io.embrace.android.embracesdk.internal.telemetry

import io.embrace.android.embracesdk.concurrency.runActionsConcurrently
import io.embrace.android.embracesdk.concurrency.runConcurrently
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.semconv.EmbTelemetryAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

internal class EmbraceTelemetryServiceTest {

    private lateinit var embraceTelemetryService: TelemetryService

    @Before
    fun setUp() {
        embraceTelemetryService = EmbraceTelemetryService(systemInfo = SystemInfo())
    }

    @Test
    fun `onPublicApiCalled with a new name`() {
        // Given a method is not in the map
        assertEquals(null, embraceTelemetryService.getAndClearTelemetryAttributes()["emb.usage.test_public_api"])

        // When the method is added
        embraceTelemetryService.onPublicApiCalled("test_public_api")

        // Then the method is in the map
        assertEquals("1", embraceTelemetryService.getAndClearTelemetryAttributes()["emb.usage.test_public_api"])
    }

    @Test
    fun `onPublicApiCalled with an existing name`() {
        // Given a method is in the map
        embraceTelemetryService.onPublicApiCalled("public_api")

        // When the same method is called again
        embraceTelemetryService.onPublicApiCalled("public_api")

        // Then the method is counted twice
        assertEquals("2", embraceTelemetryService.getAndClearTelemetryAttributes()["emb.usage.public_api"])
    }

    @Test
    fun `getTelemetryAttributes clears maps`() {
        // Given a method is in the map
        embraceTelemetryService.onPublicApiCalled("a_method")
        embraceTelemetryService.logStorageTelemetry(mapOf(EmbTelemetryAttributes.EMB_STORAGE_USED to "12"))

        // After getting telemetry attributes
        embraceTelemetryService.getAndClearTelemetryAttributes()
        val attributes = embraceTelemetryService.getAndClearTelemetryAttributes()

        // That method isn't in the map anymore
        assertEquals(null, attributes.getOrDefault("emb.usage.a_method", null))
        assertEquals(null, attributes.getOrDefault(EmbTelemetryAttributes.EMB_STORAGE_USED, null))
    }

    @Test
    fun `logStorageTelemetry adds usage to the telemetry attributes`() {
        // Given storage telemetry is added
        embraceTelemetryService.logStorageTelemetry(mapOf(EmbTelemetryAttributes.EMB_STORAGE_USED to "1231"))

        // When getting telemetry attributes
        val telemetryAttributes = embraceTelemetryService.getAndClearTelemetryAttributes()

        // Then storage telemetry is in the map
        assertEquals("1231", telemetryAttributes[EmbTelemetryAttributes.EMB_STORAGE_USED])
    }

    @Test
    fun `getTelemetryAttributes clears the storage map`() {
        // Given a method is in the map
        embraceTelemetryService.logStorageTelemetry(mapOf(EmbTelemetryAttributes.EMB_STORAGE_USED to "1"))

        // When getting telemetry attributes
        embraceTelemetryService.getAndClearTelemetryAttributes()

        // That method isn't in the map anymore
        assertEquals(
            null,
            embraceTelemetryService.getAndClearTelemetryAttributes().getOrDefault("emb.storage.a_file", null),
        )
    }

    @Test
    fun `getTelemetryAttributes adds app attributes`() {
        // When getting telemetry attributes
        val telemetryAttributes = embraceTelemetryService.getAndClearTelemetryAttributes()

        // Then the app attributes are in the map
        assertEquals("true", telemetryAttributes[EmbTelemetryAttributes.EMB_OKHTTP3])
        assertTrue(telemetryAttributes.containsKey(EmbTelemetryAttributes.EMB_OKHTTP3_ON_CLASSPATH))
        assertTrue(telemetryAttributes.containsKey(EmbTelemetryAttributes.EMB_IS_EMULATOR))
        assertTrue(telemetryAttributes.containsKey(EmbTelemetryAttributes.EMB_KOTLIN_ON_CLASSPATH))
    }

    @Test
    fun `usage, storage and app attributes are added correctly`() {
        // Given some usage and storage attributes are added
        embraceTelemetryService.onPublicApiCalled("a_method")
        embraceTelemetryService.logStorageTelemetry(mapOf(EmbTelemetryAttributes.EMB_STORAGE_USED to "12"))

        // When getting telemetry attributes
        val telemetryAttributes = embraceTelemetryService.getAndClearTelemetryAttributes()

        // Then the usage, storage, and app attributes are in the map
        assertEquals("1", telemetryAttributes["emb.usage.a_method"])
        assertEquals("12", telemetryAttributes[EmbTelemetryAttributes.EMB_STORAGE_USED])
        assertTrue(telemetryAttributes.containsKey(EmbTelemetryAttributes.EMB_OKHTTP3))
        assertTrue(telemetryAttributes.containsKey(EmbTelemetryAttributes.EMB_OKHTTP3_ON_CLASSPATH))
    }

    @Test
    fun `trackAppliedLimit with a new limit`() {
        // Given no limits have been logged
        assertEquals(
            null,
            embraceTelemetryService.getAndClearTelemetryAttributes()["emb.private.applied_limit.error_log.truncate_attributes"],
        )

        // When a limit is logged
        embraceTelemetryService.trackAppliedLimit("error_log", AppliedLimitType.TRUNCATE_ATTRIBUTES)

        // Then the limit is in the map
        assertEquals(
            "1",
            embraceTelemetryService.getAndClearTelemetryAttributes()["emb.private.applied_limit.error_log.truncate_attributes"],
        )
    }

    @Test
    fun `trackAppliedLimit increments existing limit counter`() {
        // Given a limit is already logged
        embraceTelemetryService.trackAppliedLimit("breadcrumb", AppliedLimitType.TRUNCATE_STRING)

        // When the same limit is logged again
        embraceTelemetryService.trackAppliedLimit("breadcrumb", AppliedLimitType.TRUNCATE_STRING)
        embraceTelemetryService.trackAppliedLimit("breadcrumb", AppliedLimitType.TRUNCATE_STRING)

        // Then the counter is incremented
        assertEquals(
            "3",
            embraceTelemetryService.getAndClearTelemetryAttributes()["emb.private.applied_limit.breadcrumb.truncate_string"],
        )
    }

    @Test
    fun `trackAppliedLimit tracks multiple different limits`() {
        // Given multiple different limits are logged
        embraceTelemetryService.trackAppliedLimit("error_log", AppliedLimitType.TRUNCATE_ATTRIBUTES)
        embraceTelemetryService.trackAppliedLimit("breadcrumb", AppliedLimitType.TRUNCATE_STRING)
        embraceTelemetryService.trackAppliedLimit("span", AppliedLimitType.DROP)
        embraceTelemetryService.trackAppliedLimit("error_log", AppliedLimitType.TRUNCATE_ATTRIBUTES)

        // When getting telemetry attributes
        val telemetryAttributes = embraceTelemetryService.getAndClearTelemetryAttributes()

        // Then all limits are tracked separately
        assertEquals("2", telemetryAttributes["emb.private.applied_limit.error_log.truncate_attributes"])
        assertEquals("1", telemetryAttributes["emb.private.applied_limit.breadcrumb.truncate_string"])
        assertEquals("1", telemetryAttributes["emb.private.applied_limit.span.drop"])
    }

    @Test
    fun `getTelemetryAttributes clears applied limits map`() {
        // Given a limit is logged
        embraceTelemetryService.trackAppliedLimit("span", AppliedLimitType.DROP)

        // After getting telemetry attributes
        embraceTelemetryService.getAndClearTelemetryAttributes()
        val attributes = embraceTelemetryService.getAndClearTelemetryAttributes()

        // That limit isn't in the map anymore
        assertEquals(null, attributes.getOrDefault("emb.private.applied_limit.span.drop", null))
    }

    @Test
    fun `usage, storage, applied limits and app attributes are added correctly`() {
        // Given usage, storage, and applied limit attributes are added
        embraceTelemetryService.onPublicApiCalled("a_method")
        embraceTelemetryService.logStorageTelemetry(mapOf(EmbTelemetryAttributes.EMB_STORAGE_USED to "12"))
        embraceTelemetryService.trackAppliedLimit("error_log", AppliedLimitType.TRUNCATE_ATTRIBUTES)
        embraceTelemetryService.trackAppliedLimit("breadcrumb", AppliedLimitType.DROP)

        // When getting telemetry attributes
        val telemetryAttributes = embraceTelemetryService.getAndClearTelemetryAttributes()

        // Then all attributes are in the map
        assertEquals("1", telemetryAttributes["emb.usage.a_method"])
        assertEquals("12", telemetryAttributes[EmbTelemetryAttributes.EMB_STORAGE_USED])
        assertEquals("1", telemetryAttributes["emb.private.applied_limit.error_log.truncate_attributes"])
        assertEquals("1", telemetryAttributes["emb.private.applied_limit.breadcrumb.drop"])
        assertTrue(telemetryAttributes.containsKey(EmbTelemetryAttributes.EMB_OKHTTP3))
        assertTrue(telemetryAttributes.containsKey(EmbTelemetryAttributes.EMB_OKHTTP3_ON_CLASSPATH))
    }

    @Test
    fun `concurrent onPublicApiCalled counts every call`() {
        val threadCount = 8
        val callsPerThread = 500

        runConcurrently(threadCount) {
            repeat(callsPerThread) {
                embraceTelemetryService.onPublicApiCalled("first_api")
                embraceTelemetryService.onPublicApiCalled("second_api")
            }
        }

        val telemetryAttributes = embraceTelemetryService.getAndClearTelemetryAttributes()
        val expectedCount = (threadCount * callsPerThread).toString()
        assertEquals(expectedCount, telemetryAttributes["emb.usage.first_api"])
        assertEquals(expectedCount, telemetryAttributes["emb.usage.second_api"])
    }

    @Test
    fun `concurrent trackAppliedLimit racing getAndClearTelemetryAttributes loses no increments`() {
        val writerCount = 4
        val callsPerWriter = 2_000
        val writersFinished = AtomicInteger(0)
        var drained = 0

        val writers = List(writerCount) {
            {
                try {
                    repeat(callsPerWriter) {
                        embraceTelemetryService.trackAppliedLimit("span", AppliedLimitType.DROP)
                    }
                } finally {
                    writersFinished.incrementAndGet()
                }
            }
        }
        // drains continuously while the writers run, so increments keep landing mid-drain
        val drainer: () -> Unit = {
            while (writersFinished.get() < writerCount) {
                drained += drainSpanDropCount()
            }
        }
        runActionsConcurrently(writers + drainer)
        drained += drainSpanDropCount()

        assertEquals(writerCount * callsPerWriter, drained)
    }

    @Test
    fun `concurrent logStorageTelemetry racing getAndClearTelemetryAttributes loses no entries`() {
        val rounds = 10
        val writerCount = 4
        val entriesPerWriter = 500
        val expectedKeys = (0 until writerCount).flatMap { writerIndex ->
            (0 until entriesPerWriter).map { entry -> storageKey(writerIndex, entry) }
        }.toSet()

        // the window where an entry can be lost is narrow, so repeat to hit it reliably. Each round ends fully drained,
        // so the same service can be reused.
        repeat(rounds) {
            val writersFinished = AtomicInteger(0)
            val drainedKeys = mutableSetOf<String>()

            val writers = List(writerCount) { writerIndex ->
                {
                    try {
                        repeat(entriesPerWriter) { entry ->
                            embraceTelemetryService.logStorageTelemetry(mapOf(storageKey(writerIndex, entry) to "1"))
                        }
                    } finally {
                        writersFinished.incrementAndGet()
                    }
                }
            }
            // drains continuously while the writers run, so entries keep landing mid-drain
            val drainer: () -> Unit = {
                while (writersFinished.get() < writerCount) {
                    drainedKeys += embraceTelemetryService.drainStorageKeys()
                }
            }
            runActionsConcurrently(writers + drainer)
            drainedKeys += embraceTelemetryService.drainStorageKeys()

            assertEquals(emptySet<String>(), expectedKeys - drainedKeys)
        }
    }

    private fun storageKey(writerIndex: Int, entry: Int) = "storage.$writerIndex.$entry"

    private fun TelemetryService.drainStorageKeys(): Set<String> =
        getAndClearTelemetryAttributes().keys.filter { it.startsWith("storage.") }.toSet()

    private fun drainSpanDropCount(): Int =
        embraceTelemetryService.getAndClearTelemetryAttributes()["emb.private.applied_limit.span.drop"]?.toInt() ?: 0
}
