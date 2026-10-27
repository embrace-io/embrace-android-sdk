package io.embrace.android.embracesdk.internal.navigation

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import org.junit.Assert.assertEquals
import org.junit.Test

internal class NavigationTrackingServiceImplTest {

    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private val service = NavigationTrackingServiceImpl(errorHandler)

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
        assertEquals(SdkErrorType.SessionPartListenerFail.toString(), errorHandler.sdkErrorMessages.single().msg)
    }

    private fun collect(): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        service.collectScreenAttributes(attributes::set)
        return attributes
    }
}
