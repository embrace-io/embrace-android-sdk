package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that the persona regex filters personas correctly.
 */
internal class EmbraceUserServiceTest {

    private val extraPersonas = setOf(
        "payer",
        "first_day"
    )

    private val userPersonas = setOf(
        "persona",
    )

    private val customPersonas = setOf(
        "PERSONA1",
        "1persona2",
        "a",
        "7",
        "Persona_2",
        "1Persona_3_"
    )

    private lateinit var service: EmbraceUserService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var logger: InternalEmbraceLogger

    @Before
    fun setUp() {
        logger = InternalEmbraceLogger()
        preferencesService = FakePreferenceService()
    }

    @Test
    fun testUserInfoNotLoaded() {
        mockNoUserInfo()
        assertNotNull(service.getUserInfo())
        service.getUserInfo().verifyNoUserInfo()
    }

    @Test
    fun testUserInfoLoaded() {
        mockUserInfo()
        assertNotNull(service.getUserInfo())
        service.getUserInfo().verifyExpectedUserInfo()
    }

    @Test
    fun testUserInfoSessionCopy() {
        mockUserInfo()
        assertNotSame(service.getUserInfo(), service.getUserInfo())
    }

    @Test
    fun testUserIdentifier() {
        mockUserInfo()

        with(service) {
            assertEquals("f0a923498c", getUserInfo().userId)
            setUserIdentifier("abc")
            assertEquals("abc", getUserInfo().userId)
            service.clearUserIdentifier()
            assertNull(getUserInfo().userId)
        }
    }

    @Test
    fun testUsername() {
        mockUserInfo()

        with(service) {
            assertEquals("Mr Test", getUserInfo().username)
            setUsername("Joe")
            assertEquals("Joe", getUserInfo().username)
            service.clearUsername()
            assertNull(getUserInfo().username)
        }
    }

    @Test
    fun testUserEmail() {
        mockUserInfo()

        with(service) {
            assertEquals("test@example.com", getUserInfo().email)
            setUserEmail("foo@test.com")
            assertEquals("foo@test.com", getUserInfo().email)
            service.clearUserEmail()
            assertNull(getUserInfo().email)
        }
    }

    @Test
    fun testUserAsPayer() {
        mockUserInfo()

        with(service) {
            assertTrue(checkNotNull(getUserInfo().personas).contains("payer"))
            clearUserAsPayer()
            assertFalse(checkNotNull(getUserInfo().personas).contains("payer"))
            setUserAsPayer()
            assertTrue(checkNotNull(getUserInfo().personas).contains("payer"))
        }
    }

    @Test
    fun testClearAllUserInfo() {
        mockUserInfo()

        with(service) {
            getUserInfo().verifyExpectedUserInfo()
            service.clearAllUserInfo()
            assertNull(getUserInfo().email)
            assertNull(getUserInfo().userId)
            assertNull(getUserInfo().username)
            assertEquals(extraPersonas, getUserInfo().personas)
        }
    }

    @Test
    fun testInvalidPersonaLogMsg() {
        mockUserInfo()
        val persona = "!@£$$%*("
        service.addUserPersona(persona)
        val personas = checkNotNull(service.getUserInfo().personas)
        assertFalse(personas.contains(persona))
    }

    @Test
    fun testMaxPersonaLogMsg() {
        mockNoUserInfo()

        repeat(11) { k ->
            service.addUserPersona("Persona_$k")
        }
        val personas = checkNotNull(service.getUserInfo().personas)
        assertTrue(personas.contains("Persona_1"))
        assertTrue(personas.contains("Persona_9"))
        assertFalse(personas.contains("Persona_10"))
    }

    private fun mockUserInfo() {
        preferencesService.userEmailAddress = "test@example.com"
        preferencesService.userIdentifier = "f0a923498c"
        preferencesService.username = "Mr Test"
        preferencesService.userPersonas = userPersonas
        @Suppress("DEPRECATION")
        preferencesService.customPersonas = customPersonas
        preferencesService.userPayer = true
        preferencesService.firstDay = true

        // load user info
        service = EmbraceUserService(preferencesService, logger)
    }

    private fun mockNoUserInfo() {
        preferencesService.userEmailAddress = null
        preferencesService.userIdentifier = null
        preferencesService.username = null
        preferencesService.userPersonas = null
        @Suppress("DEPRECATION")
        preferencesService.customPersonas = null
        preferencesService.userPayer = false
        preferencesService.firstDay = false

        // load user info
        service = EmbraceUserService(preferencesService, logger)
    }

    private fun UserInfo.verifyExpectedUserInfo() {
        assertEquals("test@example.com", email)
        assertEquals("f0a923498c", userId)
        assertEquals("Mr Test", username)
        val expectedPersonas = userPersonas.plus(customPersonas).plus(extraPersonas)
        assertEquals(expectedPersonas, personas)
    }

    private fun UserInfo.verifyNoUserInfo() {
        assertNull(email)
        assertNull(userId)
        assertNull(username)
        assertEquals(emptySet<String>(), personas)
    }
}
