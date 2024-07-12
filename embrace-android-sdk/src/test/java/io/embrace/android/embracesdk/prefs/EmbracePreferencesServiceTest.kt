@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.prefs

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class EmbracePreferencesServiceTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var service: EmbracePreferencesService
    private lateinit var fakeClock: FakeClock

    private val executorService = BackgroundWorker(BlockableExecutorService())
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        fakeClock = FakeClock()
        service = EmbracePreferencesService(
            executorService,
            lazy { prefs },
            fakeClock,
            EmbraceSerializer()
        )
    }

    /**
     * Asserts that the startup state preference is updated when creating a PreferenceService.
     */
    @Test
    @Throws(InterruptedException::class)
    fun testStartupStatePersistence() {
        // move through two states: startup in progress + startup complete.
        assertEquals(
            "startup_entered",
            service.sdkStartupStatus
        )

        service.applicationStartupComplete()

        // assert the entered startup and completed startup preferences were set
        assertEquals(
            "startup_completed",
            service.sdkStartupStatus
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
    fun `test install date is saved`() {
        assertNull(service.installDate)

        val installDate = 20221229L
        service.installDate = installDate
        assertEquals(installDate, service.installDate)

        service.installDate = -1
        assertNull(service.installDate)
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
    fun `test sdk disabled is saved`() {
        assertFalse(service.sdkDisabled)
        service.sdkDisabled = true
        assertTrue(service.sdkDisabled)
    }

    @Test
    fun `test user payer is saved`() {
        assertFalse(service.userPayer)
        service.userPayer = true
        assertTrue(service.userPayer)
    }

    @Test
    fun `test user identifier is saved`() {
        assertNull(service.userIdentifier)

        val userIdentifier = "userId"
        service.userIdentifier = userIdentifier
        assertEquals(userIdentifier, service.userIdentifier)

        service.userIdentifier = null
        assertNull(service.userIdentifier)
    }

    @Test
    fun `test user email is saved`() {
        assertNull(service.userEmailAddress)

        val email = "example@embrace.io"
        service.userEmailAddress = email
        assertEquals(email, service.userEmailAddress)
    }

    @Test
    fun `test user personas is saved`() {
        assertNull(service.userPersonas)

        val list = setOf("persona1", "persona2")
        service.userPersonas = list
        assertEquals(list, service.userPersonas)
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
    fun `test deprecated custom personas`() {
        assertNull(service.customPersonas)
    }

    @Test
    fun `test username is saved`() {
        assertNull(service.username)

        val username = "username"
        service.username = username
        assertEquals(username, service.username)
    }

    @Test
    fun `test last config fetch date is saved`() {
        assertNull(service.lastConfigFetchDate)
        service.lastConfigFetchDate = 1234L
        assertEquals(1234L, service.lastConfigFetchDate)
    }

    @Test
    fun `test user message needs retry is saved`() {
        assertFalse(service.userMessageNeedsRetry)
        service.userMessageNeedsRetry = true
        assertTrue(service.userMessageNeedsRetry)
    }

    @Test
    fun `test session number is saved`() {
        assertEquals(1, service.incrementAndGetSessionNumber())
        assertEquals(2, service.incrementAndGetSessionNumber())
        assertEquals(3, service.incrementAndGetSessionNumber())
        assertEquals(4, service.incrementAndGetSessionNumber())

        // bg activity uses independent key
        assertEquals(1, service.incrementAndGetBackgroundActivityNumber())
    }

    @Test
    fun `test bg activity number is saved`() {
        assertEquals(1, service.incrementAndGetBackgroundActivityNumber())
        assertEquals(2, service.incrementAndGetBackgroundActivityNumber())
        assertEquals(3, service.incrementAndGetBackgroundActivityNumber())
        assertEquals(4, service.incrementAndGetBackgroundActivityNumber())

        // session uses independent key
        assertEquals(1, service.incrementAndGetSessionNumber())
    }

    @Test
    fun `test crash number is saved`() {
        assertEquals(1, service.incrementAndGetCrashNumber())
        assertEquals(2, service.incrementAndGetCrashNumber())
        assertEquals(3, service.incrementAndGetCrashNumber())
        assertEquals(4, service.incrementAndGetCrashNumber())
    }

    @Test
    fun `test native crash number is saved`() {
        assertEquals(1, service.incrementAndGetNativeCrashNumber())
        assertEquals(2, service.incrementAndGetNativeCrashNumber())
        assertEquals(3, service.incrementAndGetNativeCrashNumber())
        assertEquals(4, service.incrementAndGetNativeCrashNumber())
    }

    @Test
    fun `test incrementAndGet returns -1 on an exception`() {
        service = EmbracePreferencesService(
            executorService,
            lazy { FakeSharedPreferences(throwExceptionOnGet = true) },
            fakeClock,
            EmbraceSerializer()
        )
        assertEquals(-1, service.incrementAndGetSessionNumber())
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

    @Test
    fun `test java script patch number is saved`() {
        assertNull(service.javaScriptPatchNumber)

        val patchNumber = "1234"
        service.javaScriptPatchNumber = patchNumber
        assertEquals(patchNumber, service.javaScriptPatchNumber)
    }

    @Test
    fun `test react native embrace sdk version is saved`() {
        assertNull(service.reactNativeVersionNumber)

        val version = "1.1.1"
        service.rnSdkVersion = version
        assertEquals(version, service.rnSdkVersion)
    }

    @Test
    fun `test react native version is saved`() {
        assertNull(service.reactNativeVersionNumber)

        val version = "1.1.1"
        service.reactNativeVersionNumber = version
        assertEquals(version, service.reactNativeVersionNumber)
    }

    @Test
    fun `test unity version is saved`() {
        assertNull(service.unityVersionNumber)

        val version = "1.1.1"
        service.unityVersionNumber = version
        assertEquals(version, service.unityVersionNumber)
    }

    @Test
    fun `test unity build number is saved`() {
        assertNull(service.unityBuildIdNumber)

        val buildNumber = "10"
        service.unityBuildIdNumber = buildNumber
        assertEquals(buildNumber, service.unityBuildIdNumber)
    }

    @Test
    fun `test unity sdk version is saved`() {
        assertNull(service.unitySdkVersionNumber)

        val version = "1.1.1"
        service.unitySdkVersionNumber = version
        assertEquals(version, service.unitySdkVersionNumber)
    }

    @Test
    fun `test is jail broken is saved`() {
        assertNull(service.jailbroken)
        service.jailbroken = true
        assertTrue(checkNotNull(service.jailbroken))
    }

    @Test
    fun `test screen resolution is saved`() {
        assertNull(service.screenResolution)
        val resolution = "1000x2000"
        service.screenResolution = resolution
        assertEquals(resolution, service.screenResolution)
    }

    @Test
    fun `test dart sdk version is saved`() {
        assertNull(service.dartSdkVersion)

        val version = "2.1.2"
        service.dartSdkVersion = version
        assertEquals(version, service.dartSdkVersion)
    }

    @Test
    fun `test flutter sdk version is saved`() {
        assertNull(service.embraceFlutterSdkVersion)

        val version = "3.1.2"
        service.embraceFlutterSdkVersion = version
        assertEquals(version, service.embraceFlutterSdkVersion)
    }

    @Test
    fun `test background activity enabled is saved`() {
        assertFalse(service.backgroundActivityEnabled)

        val expected = true
        service.backgroundActivityEnabled = true
        assertEquals(expected, service.backgroundActivityEnabled)
    }

    @Test
    fun `test is users first day`() {
        assertFalse(service.isUsersFirstDay())

        service.installDate = 0L
        fakeClock.setCurrentTime(PreferencesService.DAY_IN_MS + 1)
        assertFalse(service.isUsersFirstDay())

        fakeClock.setCurrentTime(PreferencesService.DAY_IN_MS - 1)
        assertTrue(service.isUsersFirstDay())
    }
}
