package io.embrace.android.embracesdk.internal.event.gating

import io.embrace.android.embracesdk.internal.gating.SessionGatingKeys.USER_PERSONAS
import io.embrace.android.embracesdk.internal.gating.UserInfoSanitizer
import io.embrace.android.embracesdk.internal.payload.UserInfo
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class UserInfoSanitizerTest {

    private val userInfo = UserInfo(
        personas = setOf("personas"),
        email = "example@embrace.com"
    )

    @Test
    fun `test if it keeps session properties`() {
        val components = setOf(USER_PERSONAS)

        val result = UserInfoSanitizer(userInfo, components).sanitize()

        assertNotNull(result.personas)
        assertNotNull(result.email)
    }

    @Test
    fun `test if it sanitizes session properties`() {
        // enabled components doesn't contain USER_PERSONAS
        val components = setOf<String>()

        val result = UserInfoSanitizer(userInfo, components).sanitize()

        assertNotNull(result.email)
        assertNull(result.personas)
    }
}
