package io.embrace.gradle.plugin.instrumentation

import androidx.lifecycle.ViewModel
import io.embrace.android.gradle.plugin.instrumentation.VisitorFactoryImpl
import io.embrace.android.gradle.plugin.instrumentation.json.readBytecodeInstrumentationFeatures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Instruments the real ViewModel class with the view_model_leak_detection feature, with its hook redirected to
 * [ConstructorHookRecorder], and verifies each of its public constructors calls the hook exactly once.
 */
class ViewModelInstrumentationTest {

    private val feature = readBytecodeInstrumentationFeatures().single { it.name == "view_model_leak_detection" }.let {
        it.copy(
            insertionParams = it.insertionParams.copy(
                owner = "io/embrace/gradle/plugin/instrumentation/ConstructorHookRecorder",
                name = "onConstructed",
                descriptor = "(Ljava/lang/Object;)V",
            ),
        )
    }
    private val factory = VisitorFactoryImpl(ASM_API_VERSION, FakeBytecodeInstrumentationParams())

    private val loader = InstrumentedClassLoader(
        setOf(ViewModel::class.java.name, FixtureViewModel::class.java.name),
    ) { nextVisitor, className ->
        factory.createClassVisitor(feature, FakeClassContext(FakeClassData(className)), nextVisitor)
    }
    private val fixture = loader.loadClass(FixtureViewModel::class.java.name)

    @Before
    fun setUp() {
        ConstructorHookRecorder.clear()
    }

    @Test
    fun `no-arg constructor`() {
        assertConstructedOnce(fixture.getConstructor().newInstance())
    }

    @Test
    fun `coroutine scope constructor`() {
        assertConstructedOnce(fixture.getConstructor(CoroutineScope::class.java).newInstance(SCOPE))
    }

    @Test
    fun `closeables constructor`() {
        assertConstructedOnce(fixture.getConstructor(AutoCloseable::class.java).newInstance(AutoCloseable {}))
    }

    @Test
    fun `coroutine scope and closeables constructor`() {
        val ctor = fixture.getConstructor(CoroutineScope::class.java, AutoCloseable::class.java)
        assertConstructedOnce(ctor.newInstance(SCOPE, AutoCloseable {}))
    }

    private fun assertConstructedOnce(instance: Any) {
        assertEquals(1, ConstructorHookRecorder.constructed.size)
        assertSame(instance, ConstructorHookRecorder.constructed.single())
    }

    private companion object {
        val SCOPE = CoroutineScope(Dispatchers.Unconfined)
    }
}
