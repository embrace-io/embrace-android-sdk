package io.embrace.android.embracesdk.internal.capture.user

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.UserInfo
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

    private lateinit var service: EmbraceUserService
    private lateinit var store: FakeKeyValueStore
    private lateinit var logger: InternalLogger
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        logger = FakeInternalLogger()
        clock = FakeClock()
        store = FakeKeyValueStore()

        setUserInfo(
            id = "f0a923498c",
            email = "test@example.com",
            name = "Mr Test",
            payer = true,
            firstDay = true
        )

        // load user info
        service = EmbraceUserService(store, clock, logger)
    }

    @Test
    fun testUserInfoNotLoaded() {
        setUserInfo(suppliedPersonas = emptySet())
        assertNotNull(service.getUserInfo())
        service.getUserInfo().verifyNoUserInfo()
    }

    @Test
    fun testUserInfoLoaded() {
        assertNotNull(service.getUserInfo())
        service.getUserInfo().verifyExpectedUserInfo()
    }

    @Test
    fun testUserInfoSessionCopy() {
        assertNotSame(service.getUserInfo(), service.getUserInfo())
    }

    @Test
    fun testUserIdentifier() {
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
        with(service) {
            assertTrue(checkNotNull(getUserInfo().personas).contains("payer"))
            clearUserPersona("payer")
            assertFalse(checkNotNull(getUserInfo().personas).contains("payer"))
            addUserPersona("payer")
            assertTrue(checkNotNull(getUserInfo().personas).contains("payer"))
        }
    }

    @Test
    fun testClearAllUserInfo() {
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
        val persona = "!@£$$%*("
        service.addUserPersona(persona)
        val personas = checkNotNull(service.getUserInfo().personas)
        assertFalse(personas.contains(persona))
    }

    @Test
    fun testMaxPersonaLogMsg() {
        setUserInfo()

        repeat(11) { k ->
            service.addUserPersona("Persona_$k")
        }
        val personas = checkNotNull(service.getUserInfo().personas)
        assertEquals(
            listOf(
                "persona",
                "first_day",
                "Persona_0",
                "Persona_1",
                "Persona_2",
                "Persona_3",
                "Persona_4",
                "Persona_5",
                "Persona_6",
                "Persona_7",
            ),
            personas.toList()
        )
    }

    @Test
    fun `test startup complete`() {
        val timestamp = store.values()["io.embrace.installtimestamp"]
        assertEquals(clock.now(), timestamp)
    }

    private fun setUserInfo(
        id: String? = null,
        email: String? = null,
        name: String? = null,
        payer: Boolean? = null,
        firstDay: Boolean? = null,
        suppliedPersonas: Set<String> = userPersonas,
    ) {
        store.edit {
            putString("io.embrace.userid", id)
            putString("io.embrace.useremail", email)
            putString("io.embrace.username", name)
            putBoolean("io.embrace.userispayer", payer)

            val personas = if (firstDay == true) {
                suppliedPersonas.plus("payer")
            } else {
                suppliedPersonas
            }
            putStringSet("io.embrace.userpersonas", personas)
        }
    }

    private fun UserInfo.verifyExpectedUserInfo() {
        assertEquals("test@example.com", email)
        assertEquals("f0a923498c", userId)
        assertEquals("Mr Test", username)
        val expectedPersonas = userPersonas.plus(extraPersonas)
        assertEquals(expectedPersonas, personas)
    }

    private fun UserInfo.verifyNoUserInfo() {
        assertNull(email)
        assertNull(userId)
        assertNull(username)
        assertEquals(setOf("first_day"), personas)
    }
}
