package io.embrace.android.embracesdk.internal.session

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.embrace.android.embracesdk.fakes.FakeApplication
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityCreated
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleRelay
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.utils.event.EventKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class ActivityLifecycleRelayTest {

    private lateinit var application: FakeApplication
    private lateinit var relay: ActivityLifecycleRelay
    private lateinit var received: MutableList<Pair<EventKey<*>, Any>>
    private val activity = Activity()
    private val bundle = Bundle()

    @Before
    fun setUp() {
        application = FakeApplication()
        val eventBus = EventBus(FakeErrorHandler())
        relay = ActivityLifecycleRelay(application, eventBus, FakeErrorHandler())
        received = mutableListOf()
        activityKeyedCallbacks().forEach { (key, _) ->
            eventBus.addHandler(key) { received.add(key to it) }
        }
        eventBus.addHandler(ActivityLifecycle.CREATED) { received.add(ActivityLifecycle.CREATED to it) }
    }

    @Test
    fun `each callback delivers the activity on exactly its own key`() {
        activityKeyedCallbacks().forEach { (key, callback) ->
            received.clear()
            callback(activity)
            assertEquals(listOf<Pair<EventKey<*>, Any>>(key to activity), received)
        }
    }

    @Test
    fun `created carries the saved instance state`() {
        relay.onActivityCreated(activity, null)
        relay.onActivityCreated(activity, bundle)

        assertEquals(2, received.size)
        val (first, second) = received.map { (key, event) ->
            assertSame(ActivityLifecycle.CREATED, key)
            event as ActivityCreated
        }
        assertSame(activity, first.activity)
        assertNull(first.savedInstanceState)
        assertSame(activity, second.activity)
        assertSame(bundle, second.savedInstanceState)
    }

    @Test
    fun `register and close add and remove the relay`() {
        relay.register()
        assertSame(relay, application.lifecycleCallbacks.single())

        relay.close()
        assertTrue(application.lifecycleCallbacks.isEmpty())
    }

    @Test
    fun `a failed registration is tracked`() {
        val failure = IllegalStateException("register failed")
        val throwingApplication = object : Application() {
            override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) = throw failure
        }
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        ActivityLifecycleRelay(throwingApplication, EventBus(errorHandler), errorHandler).register()

        val error = errorHandler.sdkErrorMessages.single()
        assertEquals(SdkErrorType.RegistrationFail.toString(), error.msg)
        assertSame(failure, error.throwable)
    }

    /**
     * Every callback but onActivityCreated, paired with the key it is relayed on.
     */
    private fun activityKeyedCallbacks(): List<Pair<EventKey<Activity>, (Activity) -> Unit>> = listOf(
        ActivityLifecycle.PRE_CREATED to { a: Activity -> relay.onActivityPreCreated(a, bundle) },
        ActivityLifecycle.POST_CREATED to { a: Activity -> relay.onActivityPostCreated(a, bundle) },
        ActivityLifecycle.PRE_STARTED to { a: Activity -> relay.onActivityPreStarted(a) },
        ActivityLifecycle.STARTED to { a: Activity -> relay.onActivityStarted(a) },
        ActivityLifecycle.POST_STARTED to { a: Activity -> relay.onActivityPostStarted(a) },
        ActivityLifecycle.PRE_RESUMED to { a: Activity -> relay.onActivityPreResumed(a) },
        ActivityLifecycle.RESUMED to { a: Activity -> relay.onActivityResumed(a) },
        ActivityLifecycle.POST_RESUMED to { a: Activity -> relay.onActivityPostResumed(a) },
        ActivityLifecycle.PRE_PAUSED to { a: Activity -> relay.onActivityPrePaused(a) },
        ActivityLifecycle.PAUSED to { a: Activity -> relay.onActivityPaused(a) },
        ActivityLifecycle.POST_PAUSED to { a: Activity -> relay.onActivityPostPaused(a) },
        ActivityLifecycle.PRE_STOPPED to { a: Activity -> relay.onActivityPreStopped(a) },
        ActivityLifecycle.STOPPED to { a: Activity -> relay.onActivityStopped(a) },
        ActivityLifecycle.POST_STOPPED to { a: Activity -> relay.onActivityPostStopped(a) },
        ActivityLifecycle.PRE_SAVE_INSTANCE_STATE to { a: Activity -> relay.onActivityPreSaveInstanceState(a, bundle) },
        ActivityLifecycle.SAVE_INSTANCE_STATE to { a: Activity -> relay.onActivitySaveInstanceState(a, bundle) },
        ActivityLifecycle.POST_SAVE_INSTANCE_STATE to { a: Activity -> relay.onActivityPostSaveInstanceState(a, bundle) },
        ActivityLifecycle.PRE_DESTROYED to { a: Activity -> relay.onActivityPreDestroyed(a) },
        ActivityLifecycle.DESTROYED to { a: Activity -> relay.onActivityDestroyed(a) },
        ActivityLifecycle.POST_DESTROYED to { a: Activity -> relay.onActivityPostDestroyed(a) },
    )
}
