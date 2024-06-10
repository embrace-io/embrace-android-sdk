package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakeSessionMessage
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.gating.SessionSanitizerFacade
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class SessionSanitizerFacadeTest {

    private val base = fakeSessionMessage()

    private val sessionMessage = base.copy(
        session = base.session.copy(
            endTime = null,
            terminationTime = 100L
        ),
        metadata = EnvelopeMetadata(
            email = "example@embrace.com",
            personas = setOf("personas")
        ),
        resource = EnvelopeResource(
            diskTotalCapacity = 100
        )
    )

    private val enabledComponents = setOf(
        SessionGatingKeys.BREADCRUMBS_TAPS,
        SessionGatingKeys.BREADCRUMBS_VIEWS,
        SessionGatingKeys.BREADCRUMBS_CUSTOM_VIEWS,
        SessionGatingKeys.BREADCRUMBS_WEB_VIEWS,
        SessionGatingKeys.BREADCRUMBS_CUSTOM,
        SessionGatingKeys.USER_PERSONAS,
        SessionGatingKeys.SESSION_PROPERTIES,
        SessionGatingKeys.SESSION_ORIENTATIONS,
        SessionGatingKeys.SESSION_USER_TERMINATION,
        SessionGatingKeys.SESSION_MOMENTS,
        SessionGatingKeys.LOGS_INFO,
        SessionGatingKeys.LOGS_WARN,
        SessionGatingKeys.STARTUP_MOMENT,
        SessionGatingKeys.PERFORMANCE_NETWORK,
        SessionGatingKeys.PERFORMANCE_ANR,
        SessionGatingKeys.PERFORMANCE_CURRENT_DISK_USAGE,
        SessionGatingKeys.PERFORMANCE_CPU,
        SessionGatingKeys.PERFORMANCE_CONNECTIVITY
    )

    @Test
    fun `test if it keeps all event message components`() {
        val sanitizedMessage =
            SessionSanitizerFacade(sessionMessage, enabledComponents).getSanitizedMessage()

        assertNotNull(sanitizedMessage.metadata?.personas)

        assertNotNull(sanitizedMessage.session.terminationTime)
        assertNotNull(sanitizedMessage.resource?.diskTotalCapacity)
    }

    @Test
    fun `test if it sanitizes event message components`() {
        // uses an empty set for enabled components
        val sanitizedMessage =
            SessionSanitizerFacade(sessionMessage, setOf()).getSanitizedMessage()

        assertNull(sanitizedMessage.metadata?.personas)

        assertNull(sanitizedMessage.session.terminationTime)
        assertNull(sanitizedMessage.resource?.diskTotalCapacity)
    }
}
