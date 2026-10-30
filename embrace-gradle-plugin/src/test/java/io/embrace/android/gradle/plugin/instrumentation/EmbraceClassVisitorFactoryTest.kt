package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestBytecodeInstrumentationParams
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestClassData
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestClassVisitor
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestVisitorFactoryImpl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbraceClassVisitorFactoryTest {

    @Test
    fun testParamsPassedToIsInstrumentable() {
        val params = TestBytecodeInstrumentationParams(
            classInstrumentationFilter = ClassInstrumentationFilter(listOf("kotlin.Boolean")),
        )
        val factory = TestVisitorFactoryImpl(params = params)
        assertTrue(factory.isInstrumentable(TestClassData(checkNotNull(String::class.qualifiedName))))
        assertFalse(factory.isInstrumentable(TestClassData(checkNotNull(Boolean::class.qualifiedName))))
    }

    @Test
    fun testParamsPassedToCreateClassVisitor() {
        val ctx = createClassContext("okhttp3.OkHttpClient\$Builder")
        val visitor = TestClassVisitor()
        assertNotSame(visitor, createFactory(instrumentOkHttp = true).createClassVisitor(ctx, visitor))
        assertSame(visitor, createFactory(instrumentOkHttp = false).createClassVisitor(ctx, visitor))
    }

    private fun createFactory(instrumentOkHttp: Boolean) = TestVisitorFactoryImpl(
        params = TestBytecodeInstrumentationParams(
            instrumentOkHttp = instrumentOkHttp,
            instrumentOnClick = false,
            instrumentOnLongClick = false,
        ),
    )

    private fun createClassContext(clzName: String): ClassContext = object : ClassContext {
        override val currentClassData = TestClassData(clzName, superClasses = listOf(clzName))
        override fun loadClassData(className: String): ClassData? = error("Unexpected loadClassData($className)")
    }
}
