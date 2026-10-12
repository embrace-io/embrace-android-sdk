package io.embrace.gradle.plugin.instrumentation

import io.embrace.test.fixtures.JavaConstructorTarget
import io.embrace.test.fixtures.KotlinConstructorTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import kotlin.jvm.internal.DefaultConstructorMarker

/**
 * Loads constructor-instrumented fixtures and verifies the hook runs exactly once per constructed object.
 */
class ConstructorVisitorExecutionTest {

    private val loader = InstrumentedClassLoader(
        setOf(
            JavaConstructorTarget::class.java.name,
            JavaConstructorTarget.Inner::class.java.name,
            KotlinConstructorTarget::class.java.name,
        )
    ) { nextVisitor, _ -> createConstructorVisitor(nextVisitor) }
    private val javaTarget = loader.loadClass(JavaConstructorTarget::class.java.name)
    private val kotlinTarget = loader.loadClass(KotlinConstructorTarget::class.java.name)

    @Before
    fun setUp() {
        ConstructorHookRecorder.clear()
    }

    @Test
    fun `java super constructor`() {
        assertConstructedOnce(javaTarget.getConstructor(Any::class.java).newInstance(Any()))
    }

    @Test
    fun `java this delegation`() {
        assertConstructedOnce(javaTarget.getConstructor().newInstance())
    }

    @Test
    fun `java objects created before super`() {
        assertConstructedOnce(javaTarget.getConstructor(Boolean::class.javaPrimitiveType).newInstance(true))
        ConstructorHookRecorder.clear()
        assertConstructedOnce(javaTarget.getConstructor(Boolean::class.javaPrimitiveType).newInstance(false))
    }

    @Test
    fun `java multiple returns`() {
        listOf("", "1", "a").forEach {
            ConstructorHookRecorder.clear()
            assertConstructedOnce(javaTarget.getConstructor(String::class.java).newInstance(it))
        }
    }

    @Test
    fun `java nested construction of the same class`() {
        val outer = javaTarget.getConstructor(Int::class.javaPrimitiveType).newInstance(2)
        val middle = javaTarget.getMethod("getValue").invoke(outer)
        val inner = javaTarget.getMethod("getValue").invoke(middle)
        assertEquals(3, ConstructorHookRecorder.constructed.size)
        assertSame(inner, ConstructorHookRecorder.constructed[0])
        assertSame(middle, ConstructorHookRecorder.constructed[1])
        assertSame(outer, ConstructorHookRecorder.constructed[2])
    }

    @Test
    fun `java throw before super`() {
        val result = runCatching {
            javaTarget.getConstructor(Long::class.javaPrimitiveType).newInstance(0L)
        }
        assertTrue(result.exceptionOrNull() is InvocationTargetException)
        assertTrue(ConstructorHookRecorder.constructed.isEmpty())
    }

    @Test
    fun `java hook observes state assigned after super`() {
        assertConstructedOnce(javaTarget.getConstructor(Char::class.javaPrimitiveType).newInstance('a'))
        assertEquals(listOf("initialized"), ConstructorHookRecorder.states)
    }

    @Test
    fun `java throw after super`() {
        val result = runCatching {
            javaTarget.getConstructor(Char::class.javaPrimitiveType).newInstance('x')
        }
        assertTrue(result.exceptionOrNull() is InvocationTargetException)
        assertTrue(ConstructorHookRecorder.constructed.isEmpty())
    }

    @Test
    fun `java inner class`() {
        val outer = javaTarget.getConstructor().newInstance()
        ConstructorHookRecorder.clear()
        val innerTarget = loader.loadClass(JavaConstructorTarget.Inner::class.java.name)
        assertConstructedOnce(innerTarget.getConstructor(javaTarget).newInstance(outer))
    }

    @Test
    fun `kotlin primary constructor`() {
        val ctor = kotlinTarget.getConstructor(Any::class.java, String::class.java)
        assertConstructedOnce(ctor.newInstance(Any(), "label"))
    }

    @Test
    fun `kotlin default argument constructor`() {
        val ctor = kotlinTarget.getConstructor(
            Any::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            DefaultConstructorMarker::class.java,
        )
        assertConstructedOnce(ctor.newInstance(Any(), null, 2, null))
    }

    @Test
    fun `kotlin secondary constructors`() {
        assertConstructedOnce(kotlinTarget.getConstructor().newInstance())
        ConstructorHookRecorder.clear()
        assertConstructedOnce(kotlinTarget.getConstructor(Int::class.javaPrimitiveType).newInstance(3))
    }

    private fun assertConstructedOnce(instance: Any) {
        assertEquals(1, ConstructorHookRecorder.constructed.size)
        assertSame(instance, ConstructorHookRecorder.constructed.single())
    }
}
