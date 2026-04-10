package io.embrace.android.embracesdk.internal.capture.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.behavior.FakeUserSessionBehavior
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_SESSION_PROPERTIES_FROM_CONFIG = 5
private const val MAX_SESSION_PROPERTIES_DEFAULT = 10

@RunWith(AndroidJUnit4::class)
internal class EmbraceUserSessionPropertiesTest {

    companion object {
        private const val KEY_VALID = "abc"
        private const val VALUE_VALID = "def"
    }

    private lateinit var store: FakeKeyValueStore
    private lateinit var props: EmbraceUserSessionProperties
    private lateinit var context: Context
    private lateinit var configService: FakeConfigService
    private lateinit var destination: FakeTelemetryDestination
    private lateinit var telemetryService: FakeTelemetryService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = FakeKeyValueStore()

        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(MAX_SESSION_PROPERTIES_DEFAULT)
        )
        destination = FakeTelemetryDestination()
        telemetryService = FakeTelemetryService()
        props = EmbraceUserSessionProperties(
            store,
            configService,
            destination,
            telemetryService
        )
    }

    @Test
    fun `Add user session property with no error when maxSessionProperties is absent`() {
        assertTrue(props.add(KEY_VALID, VALUE_VALID, PropertyScope.USER_SESSION))
        assertEquals(1, props.get().size.toLong())
    }

    @Test
    fun addProperty() {
        assertTrue(props.add(KEY_VALID, VALUE_VALID, PropertyScope.USER_SESSION))
        assertEquals(1, props.get().size.toLong())
        assertEquals(VALUE_VALID, props.get()[KEY_VALID])

        // user session property should not have been persisted
        val sessionProperties2 = EmbraceUserSessionProperties(store, configService, destination, FakeTelemetryService())
        assertTrue(sessionProperties2.get().isEmpty())
    }

    @Test
    fun addPropertyInvalidValue() {
        assertTrue(props.get().isEmpty())
    }

    @Test
    fun addPropertyPermanent() {
        assertTrue(props.add(KEY_VALID, VALUE_VALID, PropertyScope.PERMANENT))
        assertEquals(1, props.get().size.toLong())
        assertEquals(VALUE_VALID, props.get()[KEY_VALID])

        // permanent property should have been persisted
        val sessionProperties2 = EmbraceUserSessionProperties(store, configService, destination, FakeTelemetryService())
        assertEquals(1, sessionProperties2.get().size.toLong())
        assertEquals(VALUE_VALID, sessionProperties2.get()[KEY_VALID])

        // change property to be not permanent
        assertTrue(props.add(KEY_VALID, VALUE_VALID, PropertyScope.USER_SESSION))

        // permanent property should no longer have been persisted
        val sessionProperties3 = EmbraceUserSessionProperties(store, configService, destination, FakeTelemetryService())
        assertTrue(sessionProperties3.get().isEmpty())
    }

    @Test
    fun `process property survives session clear`() {
        assertTrue(props.add(KEY_VALID, VALUE_VALID, PropertyScope.PROCESS))
        assertEquals(1, props.get().size.toLong())

        props.onNewUserSession()

        assertEquals(1, props.get().size.toLong())
        assertEquals(VALUE_VALID, props.get()[KEY_VALID])
    }

    @Test
    fun `process property is not persisted to disk`() {
        assertTrue(props.add(KEY_VALID, VALUE_VALID, PropertyScope.PROCESS))
        assertEquals(1, props.get().size.toLong())

        // fresh instance with same store — process prop must be absent
        val props2 = EmbraceUserSessionProperties(store, configService, destination, FakeTelemetryService())
        assertTrue(props2.get().isEmpty())
    }

    @Test
    fun `session property is cleared but process property is not`() {
        props.add("sessionKey", "sv", PropertyScope.USER_SESSION)
        props.add("processKey", "pv", PropertyScope.PROCESS)
        assertEquals(2, props.get().size.toLong())

        props.onNewUserSession()

        assertEquals(1, props.get().size.toLong())
        assertEquals("pv", props.get()["processKey"])
        assertFalse(props.get().containsKey("sessionKey"))
    }

    @Test
    fun `addPropsForNewSessionSpan includes permanent and process but not session`() {
        props.add("permKey", "permVal", PropertyScope.PERMANENT)
        props.add("procKey", "procVal", PropertyScope.PROCESS)
        props.add("sessKey", "sessVal", PropertyScope.USER_SESSION)

        destination.attributes.clear()
        props.addPropsForNewSessionSpan()

        val keys = destination.attributes.keys.map { it.removePrefix("emb.properties.") }
        assertTrue("permanent prop missing", keys.contains("permKey"))
        assertTrue("process prop missing", keys.contains("procKey"))
        assertFalse("session prop should not be re-added", keys.contains("sessKey"))
    }

    @Test
    fun `key moves from process scope to session scope`() {
        props.add(KEY_VALID, VALUE_VALID, PropertyScope.PROCESS)

        // move to session scope
        props.add(KEY_VALID, VALUE_VALID, PropertyScope.USER_SESSION)

        // after clear, key should be gone (was moved to session scope)
        props.onNewUserSession()
        assertFalse(props.get().containsKey(KEY_VALID))
    }

    @Test
    fun `key moves from session scope to process scope`() {
        props.add(KEY_VALID, VALUE_VALID, PropertyScope.USER_SESSION)

        // move to process scope
        props.add(KEY_VALID, VALUE_VALID, PropertyScope.PROCESS)

        // after clear, key should still be present (was moved to process scope)
        props.onNewUserSession()
        assertEquals(VALUE_VALID, props.get()[KEY_VALID])
    }

    @Test
    fun `key moves from permanent to process scope removes from disk`() {
        props.add(KEY_VALID, VALUE_VALID, PropertyScope.PERMANENT)

        // verify persisted
        val props2 = EmbraceUserSessionProperties(store, configService, destination, FakeTelemetryService())
        assertEquals(1, props2.get().size.toLong())

        // move to process scope
        props.add(KEY_VALID, VALUE_VALID, PropertyScope.PROCESS)

        // disk store should no longer have the key
        val props3 = EmbraceUserSessionProperties(store, configService, destination, FakeTelemetryService())
        assertTrue(props3.get().isEmpty())
    }

    @Test
    @Throws(InterruptedException::class)
    fun addPropertyFromMultipleThreads() {
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
            Thread(AddPropertyWorker(startSignal, doneSignal, props, property)).start()
        }
        startSignal.countDown()
        // wait for all the workers to finish
        doneSignal.await()
        assertEquals(properties.size.toLong(), props.get().size.toLong())
        assertEquals(expected, props.get())
    }

    internal class AddPropertyWorker(
        private val startSignal: CountDownLatch,
        private val doneSignal: CountDownLatch,
        private val properties: EmbraceUserSessionProperties,
        private val property: String,
    ) : Runnable {
        override fun run() {
            try {
                startSignal.await()
                assertTrue(properties.add(property, VALUE_VALID, PropertyScope.USER_SESSION))
                doneSignal.countDown()
            } catch (ex: InterruptedException) {
                Assert.fail("worker thread died")
            }
        }
    }

    @Test
    fun addPropertyTooManyWithDefaultMax() {
        var scope = PropertyScope.PERMANENT
        for (i in 0 until MAX_SESSION_PROPERTIES_DEFAULT) {
            // cycle through all three scopes for more realistic coverage
            val currentScope = when (i % 3) {
                0 -> PropertyScope.PERMANENT
                1 -> PropertyScope.PROCESS
                else -> PropertyScope.USER_SESSION
            }
            assertTrue(props.add("prop$i", VALUE_VALID, currentScope))
            scope = currentScope
        }
        assertFalse(
            "should not be able to add new key when limit is hit",
            props.add("propPermNew", VALUE_VALID, PropertyScope.PERMANENT)
        )
        assertFalse(
            "should not be able to add new key when limit is hit",
            props.add("propTempNew", VALUE_VALID, PropertyScope.USER_SESSION)
        )

        // Verify telemetry tracked for dropped properties
        assertEquals(2, telemetryService.appliedLimits.size)
        assertEquals("session_property", telemetryService.appliedLimits[0].first)
        assertEquals(AppliedLimitType.DROP, telemetryService.appliedLimits[0].second)
        assertEquals("session_property", telemetryService.appliedLimits[1].first)
        assertEquals(AppliedLimitType.DROP, telemetryService.appliedLimits[1].second)

        val otherValue = "other"
        assertTrue(
            "should be able to update key when properties are full",
            props.add("prop0", otherValue, PropertyScope.PERMANENT)
        )
        assertEquals(
            "property was updated",
            otherValue,
            props.get()["prop0"]
        )
        assertTrue(props.remove("prop0"))
        assertTrue(
            "can add key once one was deleted",
            props.add("prop11", VALUE_VALID, scope)
        )
    }

    @Test
    fun addPropertyTooManyWithRemoteConfigMax() {
        configService.sessionBehavior = FakeUserSessionBehavior(maxUserSessionProperties = MAX_SESSION_PROPERTIES_FROM_CONFIG)
        props = EmbraceUserSessionProperties(store, configService, destination, telemetryService)
        var scope = PropertyScope.PERMANENT
        for (i in 0 until MAX_SESSION_PROPERTIES_FROM_CONFIG) {
            val currentScope = if (i % 2 == 0) PropertyScope.PERMANENT else PropertyScope.USER_SESSION
            assertTrue(props.add("prop$i", VALUE_VALID, currentScope))
            scope = currentScope
        }
        assertFalse(
            "should not be able to add new key when limit is hit",
            props.add("propPermNew", VALUE_VALID, PropertyScope.PERMANENT)
        )
        assertFalse(
            "should not be able to add new key when limit is hit",
            props.add("propTempNew", VALUE_VALID, PropertyScope.USER_SESSION)
        )
        val otherValue = "other"
        assertTrue(
            "should be able to update key when properties are full",
            props.add("prop0", otherValue, PropertyScope.PERMANENT)
        )
        assertEquals(
            "property was updated",
            otherValue,
            props.get()["prop0"]
        )
        assertTrue(props.remove("prop0"))
        assertTrue(
            "can add key once one was deleted",
            props.add("prop11", VALUE_VALID, scope)
        )
    }

    @Test
    fun removeProperty() {
        assertTrue(props.add(KEY_VALID, VALUE_VALID, PropertyScope.USER_SESSION))
        assertTrue(props.remove(KEY_VALID))
        assertTrue(props.get().isEmpty())
    }

    @Test
    fun removePropertyPermanent() {
        assertTrue(props.add(KEY_VALID, VALUE_VALID, PropertyScope.PERMANENT))

        // permanent property should have been persisted
        val sessionProperties2 = EmbraceUserSessionProperties(store, configService, destination, FakeTelemetryService())
        assertEquals(1, sessionProperties2.get().size.toLong())
        assertTrue(props.remove(KEY_VALID))
        assertTrue(props.get().isEmpty())

        // permanent property should have been removed
        val sessionProperties3 = EmbraceUserSessionProperties(store, configService, destination, FakeTelemetryService())
        assertTrue(sessionProperties3.get().isEmpty())
    }

    @Test
    fun removePropertyProcess() {
        assertTrue(props.add(KEY_VALID, VALUE_VALID, PropertyScope.PROCESS))
        assertTrue(props.remove(KEY_VALID))
        assertTrue(props.get().isEmpty())
    }

    @Test
    fun removePropertyInvalidKey() {
        assertFalse(props.remove(""))
    }

    @Test
    fun removePropertyDoesNotExist() {
        assertFalse(props.remove(KEY_VALID))
    }

    @Test
    fun removePropertyLongKey() {
        val longKey = "a".repeat(129)
        assertTrue(props.add(longKey, VALUE_VALID, PropertyScope.USER_SESSION))
        assertTrue(props.remove(longKey))
        assertTrue(props.get().isEmpty())
    }

    @Test
    fun `permanent properties can be accessed concurrently`() {
        // Add permanent and process properties
        repeat(3) { i ->
            props.add("perm$i", "value$i", PropertyScope.PERMANENT)
        }
        repeat(2) { i ->
            props.add("proc$i", "value$i", PropertyScope.PROCESS)
        }

        val iterations = 100
        val errors = AtomicInteger(0)
        val latch = CountDownLatch(2)

        val apiCaller = Thread {
            repeat(iterations) {
                try {
                    props.addPropsForNewSessionSpan()
                } catch (e: ConcurrentModificationException) {
                    errors.incrementAndGet()
                }
            }
            latch.countDown()
        }

        val internalCaller = Thread {
            repeat(iterations) { i ->
                try {
                    props.add("dynamic$i", "val$i", PropertyScope.PERMANENT)
                    props.add("dynProc$i", "val$i", PropertyScope.PROCESS)
                    props.remove("dynamic$i")
                    props.remove("dynProc$i")
                } catch (e: ConcurrentModificationException) {
                    errors.incrementAndGet()
                }
            }
            latch.countDown()
        }

        apiCaller.start()
        internalCaller.start()
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals("ConcurrentModificationException detected", 0, errors.get())
    }
}
