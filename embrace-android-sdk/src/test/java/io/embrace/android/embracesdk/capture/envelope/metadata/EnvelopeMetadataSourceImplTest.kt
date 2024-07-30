package io.embrace.android.embracesdk.capture.envelope.metadata

import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSourceImpl
import io.embrace.android.embracesdk.internal.payload.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class EnvelopeMetadataSourceImplTest {

    @Test
    fun getEnvelopeMetadata() {
        val userService = FakeUserService().apply {
            obj = UserInfo(
                userId = "userId",
                email = "email",
                username = "username",
                personas = setOf("persona1", "persona2")
            )
        }
        val source = EnvelopeMetadataSourceImpl(userService::getUserInfo)
        val metadata = source.getEnvelopeMetadata()
        assertEquals("userId", metadata.userId)
        assertEquals("email", metadata.email)
        assertEquals("username", metadata.username)
        assertEquals(setOf("persona1", "persona2"), metadata.personas)
        assertNotNull(metadata.timezoneDescription)
        assertNotNull(metadata.locale)
    }
}
