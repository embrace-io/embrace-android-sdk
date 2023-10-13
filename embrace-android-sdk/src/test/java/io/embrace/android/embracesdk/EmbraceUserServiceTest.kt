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
        assertNotNull(service.info)
        service.info.verifyNoUserInfo()
    }

    @Test
    fun testUserInfoLoaded() {
        mockUserInfo()
        assertNotNull(service.info)
        service.info.verifyExpectedUserInfo()
    }

    @Test
    fun testUserInfoSessionCopy() {
        mockUserInfo()
        assertNotSame(service.info, service.getUserInfo())
    }

    @Test
    fun testUserIdentifier() {
        mockUserInfo()

        with(service) {
            assertEquals("f0a923498c", info.userId)
            setUserIdentifier("abc")
            assertEquals("abc", info.userId)
            service.clearUserIdentifier()
            assertNull(info.userId)
        }
    }

    @Test
    fun testUsername() {
        mockUserInfo()

        with(service) {
            assertEquals("Mr Test", info.username)
            setUsername("Joe")
            assertEquals("Joe", info.username)
            service.clearUsername()
            assertNull(info.username)
        }
    }

    @Test
    fun testUserEmail() {
        mockUserInfo()

        with(service) {
            assertEquals("test@example.com", info.email)
            setUserEmail("foo@test.com")
            assertEquals("foo@test.com", info.email)
            service.clearUserEmail()
            assertNull(info.email)
        }
    }

    @Test
    fun testUserAsPayer() {
        mockUserInfo()

        with(service) {
            assertTrue(info.personas!!.contains("payer"))
            clearUserAsPayer()
            assertFalse(info.personas!!.contains("payer"))
            setUserAsPayer()
            assertTrue(info.personas!!.contains("payer"))
        }
    }

    @Test
    fun testClearAllUserInfo() {
        mockUserInfo()

        with(service) {
            info.verifyExpectedUserInfo()
            service.clearAllUserInfo()
            assertNull(info.email)
            assertNull(info.userId)
            assertNull(info.username)
            assertEquals(extraPersonas, info.personas)
        }
    }

    @Test
    fun testInvalidPersonaLogMsg() {
        mockUserInfo()
        val persona = "!@£$$%*("
        service.addUserPersona(persona)
        assertFalse(service.info.personas!!.contains(persona))
    }

    @Test
    fun testMaxPersonaLogMsg() {
        mockNoUserInfo()

        repeat(11) { k ->
            service.addUserPersona("Persona_$k")
        }
        val personas = checkNotNull(service.info.personas)
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
