package io.embrace.android.embracesdk.internal.capture.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.behavior.FakeSessionBehavior
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

private const val MAX_SESSION_PROPERTIES_FROM_CONFIG = 5
private const val MAX_SESSION_PROPERTIES_DEFAULT = 10

@RunWith(AndroidJUnit4::class)
internal class EmbraceSessionPropertiesTest {

    companion object {
        private val fakeClock = FakeClock()
        private const val KEY_VALID = "abc"
        private const val VALUE_VALID = "def"
    }

    private lateinit var preferencesService: PreferencesService
    private lateinit var sessionProperties: EmbraceSessionProperties
    private lateinit var context: Context
    private lateinit var configService: FakeConfigService
    private lateinit var destination: FakeTelemetryDestination

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesService =
            EmbracePreferencesService(FakeKeyValueStore(), fakeClock)

        configService = FakeConfigService(
            sessionBehavior = FakeSessionBehavior(MAX_SESSION_PROPERTIES_DEFAULT)
        )
        destination = FakeTelemetryDestination()
        sessionProperties = EmbraceSessionProperties(
            preferencesService,
            configService,
            destination
        )
    }

    @Test
    fun `Add session property with no error when maxSessionProperties is absent`() {
        assertTrue(sessionProperties.add(KEY_VALID, VALUE_VALID, false))
        assertEquals(1, sessionProperties.get().size.toLong())
    }

    @Test
    fun addSessionProperty() {
        assertTrue(sessionProperties.add(KEY_VALID, VALUE_VALID, false))
        assertEquals(1, sessionProperties.get().size.toLong())
        assertEquals(VALUE_VALID, sessionProperties.get()[KEY_VALID])

        // temporary property should not have been persisted
        val sessionProperties2 = EmbraceSessionProperties(preferencesService, configService, destination)
        assertTrue(sessionProperties2.get().isEmpty())
    }

    @Test
    fun addSessionPropertyInvalidValue() {
        assertTrue(sessionProperties.get().isEmpty())
    }

    @Test
    fun addSessionPropertyPermanent() {
        assertTrue(sessionProperties.add(KEY_VALID, VALUE_VALID, true))
        assertEquals(1, sessionProperties.get().size.toLong())
        assertEquals(VALUE_VALID, sessionProperties.get()[KEY_VALID])

        // permanent property should have been persisted
        val sessionProperties2 = EmbraceSessionProperties(preferencesService, configService, destination)
        assertEquals(1, sessionProperties2.get().size.toLong())
        assertEquals(VALUE_VALID, sessionProperties2.get()[KEY_VALID])

        // /change property to be not permanent
        assertTrue(sessionProperties.add(KEY_VALID, VALUE_VALID, false))

        // permanent property should no longer have been persisted
        val sessionProperties3 = EmbraceSessionProperties(preferencesService, configService, destination)
        assertTrue(sessionProperties3.get().isEmpty())
    }

    @Test
    @Throws(InterruptedException::class)
    fun addSessionPropertyFromMultipleThreads() {
        val expected: MutableMap<String, String> = HashMap()
        val properties = ArrayList<String>()
        var key: String
        for (i in 0 until MAX_SESSION_PROPERTIES_DEFAULT) {
            key = "prop$i"
            properties.add(key)
            expected[key] = VALUE_VALID
        }
        val startSignal = CountDownLatch(1)
        val doneSignal = CountDownLatch(properties.size)
        for (property in properties) {
            // start workers that will all add a fragment each
            Thread(AddPropertyWorker(startSignal, doneSignal, sessionProperties, property)).start()
        }
        startSignal.countDown()
        // wait for all the workers to finish
        doneSignal.await()
        assertEquals(properties.size.toLong(), sessionProperties.get().size.toLong())
        assertEquals(expected, sessionProperties.get())
    }

    internal class AddPropertyWorker(
        private val startSignal: CountDownLatch,
        private val doneSignal: CountDownLatch,
        private val properties: EmbraceSessionProperties,
        private val property: String,
    ) : Runnable {
        override fun run() {
            try {
                startSignal.await()
                assertTrue(properties.add(property, VALUE_VALID, false))
                doneSignal.countDown()
            } catch (ex: InterruptedException) {
                Assert.fail("worker thread died")
            }
        }
    }

    @Test
    fun addPropertyTooManyWithDefaultMax() {
        var isPermanent = true
        for (i in 0 until MAX_SESSION_PROPERTIES_DEFAULT) {
            assertTrue(sessionProperties.add("prop$i", VALUE_VALID, isPermanent))
            // flip between permanent and temporary
            isPermanent = !isPermanent
        }
        assertFalse(
            "should not be able to add new key when limit is hit",
            sessionProperties.add("propPermNew", VALUE_VALID, true)
        )
        assertFalse(
            "should not be able to add new key when limit is hit",
            sessionProperties.add("propTempNew", VALUE_VALID, false)
        )
        val otherValue = "other"
        assertTrue(
            "should be able to update key when properties are full",
            sessionProperties.add("prop0", otherValue, true)
        )
        assertEquals(
            "property was updated",
            otherValue,
            sessionProperties.get()["prop0"]
        )
        assertTrue(sessionProperties.remove("prop0"))
        assertTrue(
            "can add key once one was deleted",
            sessionProperties.add("prop11", VALUE_VALID, isPermanent)
        )
    }

    @Test
    fun addPropertyTooManyWithRemoteConfigMax() {
        configService.sessionBehavior = FakeSessionBehavior(maxSessionProperties = MAX_SESSION_PROPERTIES_FROM_CONFIG)
        var isPermanent = true
        for (i in 0 until MAX_SESSION_PROPERTIES_FROM_CONFIG) {
            assertTrue(sessionProperties.add("prop$i", VALUE_VALID, isPermanent))
            // flip between permanent and temporary
            isPermanent = !isPermanent
        }
        assertFalse(
            "should not be able to add new key when limit is hit",
            sessionProperties.add("propPermNew", VALUE_VALID, true)
        )
        assertFalse(
            "should not be able to add new key when limit is hit",
            sessionProperties.add("propTempNew", VALUE_VALID, false)
        )
        val otherValue = "other"
        assertTrue(
            "should be able to update key when properties are full",
            sessionProperties.add("prop0", otherValue, true)
        )
        assertEquals(
            "property was updated",
            otherValue,
            sessionProperties.get()["prop0"]
        )
        assertTrue(sessionProperties.remove("prop0"))
        assertTrue(
            "can add key once one was deleted",
            sessionProperties.add("prop11", VALUE_VALID, isPermanent)
        )
    }

    @Test
    fun removeSessionProperty() {
        assertTrue(sessionProperties.add(KEY_VALID, VALUE_VALID, false))
        assertTrue(sessionProperties.remove(KEY_VALID))
        assertTrue(sessionProperties.get().isEmpty())
    }

    @Test
    fun removeSessionPropertyPermanent() {
        assertTrue(sessionProperties.add(KEY_VALID, VALUE_VALID, true))

        // permanent property should have been persisted
        val sessionProperties2 = EmbraceSessionProperties(preferencesService, configService, destination)
        assertEquals(1, sessionProperties2.get().size.toLong())
        assertTrue(sessionProperties.remove(KEY_VALID))
        assertTrue(sessionProperties.get().isEmpty())

        // permanent property should have been removed
        val sessionProperties3 = EmbraceSessionProperties(preferencesService, configService, destination)
        assertTrue(sessionProperties3.get().isEmpty())
    }

    @Test
    fun removeSessionPropertyInvalidKey() {
        assertFalse(sessionProperties.remove(""))
    }

    @Test
    fun removeSessionPropertyDoesNotExist() {
        assertFalse(sessionProperties.remove(KEY_VALID))
    }

    @Test
    fun removeSessionPropertyLongKey() {
        val longKey = "a".repeat(129)
        assertTrue(sessionProperties.add(longKey, VALUE_VALID, false))
        assertTrue(sessionProperties.remove(longKey))
        assertTrue(sessionProperties.get().isEmpty())
    }
}
