package io.embrace.android.embracesdk.internal.api

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.InternalInterfaceApi
import io.embrace.android.embracesdk.internal.NoopInternalInterfaceApi
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.UnityInternalInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal class ForwardingInternalInterfaceTest {

    private val firstCalls = mutableListOf<String>()
    private val secondCalls = mutableListOf<String>()
    private lateinit var first: InternalInterfaceApi
    private lateinit var second: InternalInterfaceApi

    @Before
    fun setUp() {
        first = RecordingInternalInterfaceApi(firstCalls)
        second = RecordingInternalInterfaceApi(secondCalls)
        EmbraceInternalApi.internalInterfaceApi = first
    }

    @After
    fun tearDown() {
        EmbraceInternalApi.internalInterfaceApi = NoopInternalInterfaceApi
    }

    @Test
    fun `held internal interface follows the target`() {
        assertFollowsTarget(EmbraceInternalApi.internalInterface, EmbraceInternalInterface::class.java)
    }

    @Test
    fun `held react native interface follows the target`() {
        assertFollowsTarget(EmbraceInternalApi.reactNativeInternalInterface, ReactNativeInternalInterface::class.java)
    }

    @Test
    fun `held unity interface follows the target`() {
        assertFollowsTarget(EmbraceInternalApi.unityInternalInterface, UnityInternalInterface::class.java)
    }

    @Test
    fun `held flutter interface follows the target`() {
        assertFollowsTarget(EmbraceInternalApi.flutterInternalInterface, FlutterInternalInterface::class.java)
    }

    /**
     * Invokes every method of [type] on [held] before and after the target is swapped, asserting each call reaches
     * whichever target is current at the time.
     */
    private fun assertFollowsTarget(held: Any, type: Class<*>) {
        val expected = type.methods.map { "${label(it.declaringClass)}:${it.name}" }.sorted()

        type.methods.forEach { it.invoke(held, *args(it)) }
        assertEquals(expected, firstCalls.sorted())
        assertEquals(emptyList<String>(), secondCalls)

        firstCalls.clear()
        EmbraceInternalApi.internalInterfaceApi = second
        type.methods.forEach { it.invoke(held, *args(it)) }
        assertEquals(emptyList<String>(), firstCalls)
        assertEquals(expected, secondCalls.sorted())
    }

    private fun args(method: Method): Array<Any> = method.parameterTypes.map {
        if (it == Context::class.java) Application() else "value"
    }.toTypedArray()

    private class RecordingInternalInterfaceApi(private val calls: MutableList<String>) : InternalInterfaceApi {
        override val internalInterface: EmbraceInternalInterface = recorder()
        override val reactNativeInternalInterface: ReactNativeInternalInterface = recorder()
        override val unityInternalInterface: UnityInternalInterface = recorder()
        override val flutterInternalInterface: FlutterInternalInterface = recorder()

        /**
         * Records each call against the interface it was made on, so calls routed to the wrong interface are caught.
         */
        private inline fun <reified T> recorder(): T = Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, method, _ ->
            calls += "${label(T::class.java)}:${method.name}"
            if (method.returnType == Boolean::class.javaPrimitiveType) false else null
        } as T
    }

    private companion object {
        fun label(type: Class<*>): String = type.simpleName
    }
}
