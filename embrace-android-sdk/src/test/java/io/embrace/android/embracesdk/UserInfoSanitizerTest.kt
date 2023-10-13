package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.gating.SessionGatingKeys.USER_PERSONAS
import io.embrace.android.embracesdk.gating.UserInfoSanitizer
import io.embrace.android.embracesdk.payload.UserInfo
import org.junit.Assert
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

        Assert.assertNotNull(result.personas)
        Assert.assertNotNull(result.email)
    }

    @Test
    fun `test if it sanitizes session properties`() {
        // enabled components doesn't contain USER_PERSONAS
        val components = setOf<String>()

        val result = UserInfoSanitizer(userInfo, components).sanitize()

        Assert.assertNotNull(result.email)
        Assert.assertNull(result.personas)
    }
}
