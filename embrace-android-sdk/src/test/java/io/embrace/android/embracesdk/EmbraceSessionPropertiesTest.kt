@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

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
    private lateinit var logger: InternalEmbraceLogger
    private lateinit var configService: ConfigService
    private lateinit var config: RemoteConfig

    @Before
    fun setUp() {
        val executorService = Executors.newSingleThreadExecutor()
        context = ApplicationProvider.getApplicationContext()
        logger = InternalEmbraceLogger()
        val prefs = lazy { PreferenceManager.getDefaultSharedPreferences(context) }
        preferencesService =
            EmbracePreferencesService(executorService, prefs, fakeClock, EmbraceSerializer())

        config = RemoteConfig()
        configService = FakeConfigService(
            sessionBehavior = fakeSessionBehavior {
                config
            }
        )
        sessionProperties = EmbraceSessionProperties(
            preferencesService,
            configService,
            logger
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
        val sessionProperties2 = EmbraceSessionProperties(preferencesService, configService, logger)
        assertTrue(sessionProperties2.get().isEmpty())
    }

    @Test
    fun addSessionPropertyInvalidKey() {
        assertFalse(sessionProperties.add("", VALUE_VALID, false))
        assertTrue(sessionProperties.get().isEmpty())
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
        val sessionProperties2 = EmbraceSessionProperties(preferencesService, configService, logger)
        assertEquals(1, sessionProperties2.get().size.toLong())
        assertEquals(VALUE_VALID, sessionProperties2.get()[KEY_VALID])

        // /change property to be not permanent
        assertTrue(sessionProperties.add(KEY_VALID, VALUE_VALID, false))

        // permanent property should no longer have been persisted
        val sessionProperties3 = EmbraceSessionProperties(preferencesService, configService, logger)
        assertTrue(sessionProperties3.get().isEmpty())
    }

    @Test
    fun addSessionPropertyKeyTooLong() {
        val longKey = "a".repeat(129)
        assertTrue(sessionProperties.add(longKey, VALUE_VALID, false))
        assertEquals(1, sessionProperties.get().size.toLong())
        val key = "a".repeat(125) + "..."
        assertEquals(VALUE_VALID, sessionProperties.get()[key])
    }

    @Test
    fun addSessionPropertyValueTooLong() {
        val longValue = "a".repeat(1025)
        assertTrue(sessionProperties.add(KEY_VALID, longValue, false))
        assertEquals(1, sessionProperties.get().size.toLong())
        val value = "a".repeat(1021) + "..."
        assertEquals(value, sessionProperties.get()[KEY_VALID])
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
        private val property: String
    ) : Runnable {
        override fun run() {
            try {
                startSignal.await()
                assertTrue(properties.add(property, VALUE_VALID, false))
                doneSignal.countDown()
            } catch (ex: InterruptedException) {
                fail("worker thread died")
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
            sessionProperties.add("propNew", VALUE_VALID, true)
        )
        val otherValue = "other"
        assertTrue(
            "should be able to update key when properties are full",
            sessionProperties.add("prop0", otherValue, true)
        )
        assertEquals(
            "property was updated", otherValue,
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
        config = RemoteConfig(maxSessionProperties = MAX_SESSION_PROPERTIES_FROM_CONFIG)
        var isPermanent = true
        for (i in 0 until MAX_SESSION_PROPERTIES_FROM_CONFIG) {
            assertTrue(sessionProperties.add("prop$i", VALUE_VALID, isPermanent))
            // flip between permanent and temporary
            isPermanent = !isPermanent
        }
        assertFalse(
            "should not be able to add new key when limit is hit",
            sessionProperties.add("propNew", VALUE_VALID, true)
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
        val sessionProperties2 = EmbraceSessionProperties(preferencesService, configService, logger)
        assertEquals(1, sessionProperties2.get().size.toLong())
        assertTrue(sessionProperties.remove(KEY_VALID))
        assertTrue(sessionProperties.get().isEmpty())

        // permanent property should have been removed
        val sessionProperties3 = EmbraceSessionProperties(preferencesService, configService, logger)
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

private const val MAX_SESSION_PROPERTIES_FROM_CONFIG = 5
private const val MAX_SESSION_PROPERTIES_DEFAULT = 10
