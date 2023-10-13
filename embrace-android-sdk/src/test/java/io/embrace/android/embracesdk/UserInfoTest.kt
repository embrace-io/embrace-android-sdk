package io.embrace.android.embracesdk

import com.google.gson.Gson
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.payload.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class UserInfoTest {

    private val info = UserInfo(
        userId = "123",
        email = "fake@example.com",
        username = "joebloggs",
        personas = setOf("first_day"),
    )

    @Test
    fun testSerialization() {
        val data = ResourceReader.readResourceAsText("user_info_expected.json")
            .filter { !it.isWhitespace() }
        val observed = Gson().toJson(info)
        assertEquals(data, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("user_info_expected.json")
        val obj = Gson().fromJson(json, UserInfo::class.java)
        assertEquals("123", obj.userId)
        assertEquals("fake@example.com", obj.email)
        assertEquals("joebloggs", obj.username)
        assertEquals(setOf("first_day"), obj.personas)
    }

    @Test
    fun testEmptyObject() {
        val info = Gson().fromJson("{}", UserInfo::class.java)
        assertNotNull(info)
    }

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
     * Construct UserInfo object with nulls then set values
     */
    @Test
    fun testNullsInCtor() {
        val info = UserInfo(null, null, null, null)
        info.userId = "5"
        info.username = "root"
        info.email = "faker22@example.com"
        info.personas = setOf("payer")
        assertEquals("5", info.userId)
        assertEquals("faker22@example.com", info.email)
        assertEquals("root", info.username)
        assertEquals(setOf("payer"), info.personas)
    }

    /**
     * Construct UserInfo from an empty PreferenceService
     */
    @Test
    fun testOfStoredDefault() {
        val service = FakePreferenceService()
        val info = UserInfo.ofStored(service)
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
        val info = UserInfo.ofStored(service)
        assertEquals("123", info.userId)
        assertEquals("testing@example.com", info.email)
        assertEquals("fogglesmash54", info.username)
        val expected: Set<String> = HashSet(listOf("payer"))
        assertEquals(expected, info.personas)
    }
}
