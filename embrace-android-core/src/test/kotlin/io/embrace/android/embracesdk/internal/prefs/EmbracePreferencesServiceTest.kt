@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.prefs

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class EmbracePreferencesServiceTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var service: EmbracePreferencesService
    private lateinit var fakeClock: FakeClock

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        fakeClock = FakeClock()
        service = EmbracePreferencesService(
            SharedPrefsStore(prefs, TestPlatformSerializer()),
        )
    }

    @Test
    fun `test app version is saved`() {
        assertNull(service.appVersion)

        val appVersion = "1.1"
        service.appVersion = appVersion
        assertEquals(appVersion, service.appVersion)
    }

    @Test
    fun `test OS version is saved`() {
        assertNull(service.osVersion)

        val osVersion = "12.1"
        service.osVersion = osVersion
        assertEquals(osVersion, service.osVersion)
    }

    @Test
    fun `test device identifier is saved`() {
        val deviceIdentifier = "android"
        service.deviceIdentifier = deviceIdentifier
        assertEquals(deviceIdentifier, service.deviceIdentifier)
    }

    @Test
    fun `test device identifier is created`() {
        assertNotNull(service.deviceIdentifier)
    }

    @Test
    fun `test permanent session properties are saved`() {
        assertNull(service.permanentSessionProperties)

        val map = mapOf("property1" to "1", "property2" to "2")
        service.permanentSessionProperties = map
        assertEquals(map, service.permanentSessionProperties)

        service.permanentSessionProperties = null
        assertNull(service.permanentSessionProperties)
    }

    @Test
    fun `test last config fetch date is saved`() {
        assertNull(service.lastConfigFetchDate)
        service.lastConfigFetchDate = 1234L
        assertEquals(1234L, service.lastConfigFetchDate)
    }

    @Test
    fun `test java script bundle url is saved`() {
        assertNull(service.javaScriptBundleURL)

        val url = "http://url.com"
        service.javaScriptBundleURL = url
        assertEquals(url, service.javaScriptBundleURL)
    }

    @Test
    fun `test java script bundle id is saved`() {
        assertNull(service.javaScriptBundleId)

        val id = "0d48510589c0426b43f01a5fa060a333"
        service.javaScriptBundleId = id
        assertEquals(id, service.javaScriptBundleId)
    }
}
