package io.embrace.android.embracesdk.instrumentation.huclite

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeURLStreamHandlerFactory
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.net.URLStreamHandlerFactory

@RunWith(AndroidJUnit4::class)
internal class InstrumentationInitializerTest {
    private lateinit var fieldRef: Field
    private lateinit var instrumentationInitializer: InstrumentationInitializer

    @Before
    fun setup() {
        factoryField = null
        fieldRef = checkNotNull(this::class.java.declaredFields.find { it.name == "factoryField" })
        fieldRef.isAccessible = true
    }

    @Test
    fun `initialization with no previous factory works correctly`() {
        instrumentationInitializer = InstrumentationInitializer(
            streamHandlerFactoryFieldProvider = { fieldRef },
            factoryInstaller = {
                factoryField = it
                URL.setURLStreamHandlerFactory(it)
            }
        )
        instrumentationInitializer.installURLStreamHandlerFactory { t: Throwable -> throw t }
        assertTrue(factoryField is InstrumentedUrlStreamHandlerFactory)

        assertThrows(Error::class.java) {
            URL.setURLStreamHandlerFactory(FakeURLStreamHandlerFactory())
        }
    }

    @Test
    fun `initialization with existing factory works correctly`() {
        factoryField = FakeURLStreamHandlerFactory()
        instrumentationInitializer = InstrumentationInitializer(
            streamHandlerFactoryFieldProvider = { fieldRef },
            factoryInstaller = { factoryField = it }
        )
        instrumentationInitializer.installURLStreamHandlerFactory { t: Throwable -> throw t }
        assertTrue(factoryField is DelegatingInstrumentedURLStreamHandlerFactory)
    }

    @Test
    fun `method invocation works correctly`() {
        val instrumentationClass = Class.forName("io.embrace.android.embracesdk.instrumentation.huclite.InstrumentationInitializer")
        val instrumentationObject = instrumentationClass.getDeclaredConstructor().newInstance()
        val initMethod = instrumentationObject::class.java.getDeclaredMethod(
            "installURLStreamHandlerFactory",
            Function1::class.java
        )
        assertThrows(InvocationTargetException::class.java) {
            initMethod.invoke(
                instrumentationObject,
                fun(t: Throwable) { throw t }
            )
        }
    }

    companion object {
        var factoryField: URLStreamHandlerFactory? = null
    }
}
