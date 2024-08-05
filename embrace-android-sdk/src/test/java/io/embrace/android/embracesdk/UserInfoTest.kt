package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.internal.capture.user.getStoredUserInfo
import io.embrace.android.embracesdk.internal.payload.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class UserInfoTest {

    /**
     * Construct a default builder
     */
    @Test
    fun testBuilderDefault() {
        val info = UserInfo()
        assertNull(info.userId)
        assertNull(info.email)
        assertNull(info.username)
        assertNull(info.personas)
    }

    /**
     * Construct a builder and set all its possible values
     */
    @Test
    fun testBuilderWithValues() {
        val info = UserInfo(
            userId = "123",
            email = "fake@example.com",
            username = "Mr F. Ake",
            personas = setOf("first_day")
        )
        assertEquals("123", info.userId)
        assertEquals("fake@example.com", info.email)
        assertEquals("Mr F. Ake", info.username)
        assertEquals(setOf("first_day"), info.personas)
    }

    /**
     * Construct UserInfo from an empty PreferenceService
     */
    @Test
    fun testOfStoredDefault() {
        val service = FakePreferenceService()
        val info = service.getStoredUserInfo()
        assertNull(info.userId)
        assertNull(info.email)
        assertNull(info.username)
        assertEquals(emptySet<Any>(), info.personas)
    }

    /**
     * Construct UserInfo from an empty PreferenceService
     */
    @Test
    fun testOfStoredWithValues() {
        val service = FakePreferenceService()
        service.userIdentifier = "123"
        service.userEmailAddress = "testing@example.com"
        service.username = "fogglesmash54"
        service.userPersonas = setOf("payer")
        service.userPayer = true
        val info = service.getStoredUserInfo()
        assertEquals("123", info.userId)
        assertEquals("testing@example.com", info.email)
        assertEquals("fogglesmash54", info.username)
        val expected: Set<String> = HashSet(listOf("payer"))
        assertEquals(expected, info.personas)
    }
}
