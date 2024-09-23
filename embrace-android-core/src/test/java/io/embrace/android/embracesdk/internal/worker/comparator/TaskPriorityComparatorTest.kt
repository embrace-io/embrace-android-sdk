package io.embrace.android.embracesdk.internal.worker.comparator

import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.ApiRequestUrl
import io.embrace.android.embracesdk.internal.worker.PriorityRunnableFuture
import io.embrace.android.embracesdk.internal.worker.TaskPriority.CRITICAL
import io.embrace.android.embracesdk.internal.worker.TaskPriority.HIGH
import io.embrace.android.embracesdk.internal.worker.TaskPriority.LOW
import io.embrace.android.embracesdk.internal.worker.TaskPriority.NORMAL
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskPriorityComparatorTest {

    @Test
    fun `test api request comparator`() {
        val input = listOf(
            createRequest("1", true),
            createRequest("2", false),
            createRequest("3", true),
            createRequest("4", false)
        )
        val expected = listOf(
            createRequest("3", true),
            createRequest("1", true),
            createRequest("2", false),
            createRequest("4", false)
        )
        val observed = input.sortElements(apiRequestComparator)
        assertEquals(expected.map { it.url.url }, observed.map { it.url.url })
    }

    @Test
    fun `test task priority comparator`() {
        val input = listOf(CRITICAL, NORMAL, LOW, HIGH, NORMAL, CRITICAL, LOW, HIGH)
        val expected = listOf(CRITICAL, CRITICAL, HIGH, HIGH, NORMAL, NORMAL, LOW, LOW)
        val observed = input.sortElements(taskPriorityComparator)
        assertEquals(expected, observed)
    }

    private inline fun <reified T> List<T>.sortElements(comparator: Comparator<Runnable>): List<T> {
        return map(::createRunnable)
            .sortedWith(comparator)
            .map {
                require(it is PriorityRunnableFuture<*>) {
                    "Runnable must be PriorityRunnableFuture"
                }
                it.priorityInfo as T
            }
    }

    private inline fun <reified T> createRunnable(priority: T): Runnable {
        return PriorityRunnableFuture<T>(mockk(relaxed = true), priority as Any)
    }

    private fun createRequest(path: String, isSession: Boolean): ApiRequest {
        val suffix = if (isSession) "spans" else "log"
        return ApiRequest(
            userAgent = "",
            url = ApiRequestUrl(url = "https://example.com/api/$path/$suffix"),
        )
    }
}
