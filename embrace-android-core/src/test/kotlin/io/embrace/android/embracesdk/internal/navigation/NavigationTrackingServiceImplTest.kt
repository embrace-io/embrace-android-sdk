package io.embrace.android.embracesdk.internal.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

internal class NavigationTrackingServiceImplTest {

    private val service = NavigationTrackingServiceImpl()

    @Test
    fun `attributes from every source are written to the sink`() {
        service.addScreenAttributesSource { sink -> sink("a", "1") }
        service.addScreenAttributesSource { sink -> sink("b", "2") }

        assertEquals(mapOf("a" to "1", "b" to "2"), collect())
    }

    @Test
    fun `a failing source does not stop the other sources`() {
        service.addScreenAttributesSource { error("boom") }
        service.addScreenAttributesSource { sink -> sink("b", "2") }

        assertEquals(mapOf("b" to "2"), collect())
    }

    private fun collect(): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        service.collectScreenAttributes(attributes::set)
        return attributes
    }
}
