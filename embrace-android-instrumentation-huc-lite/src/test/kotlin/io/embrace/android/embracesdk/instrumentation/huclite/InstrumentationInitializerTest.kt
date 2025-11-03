package io.embrace.android.embracesdk.instrumentation.huclite

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInstrumentationInstallArgs
import io.embrace.android.embracesdk.fakes.FakeURLStreamHandlerFactory
import io.embrace.android.embracesdk.internal.instrumentation.HucLiteDataSource
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field
import java.net.URL
import java.net.URLStreamHandlerFactory

@RunWith(AndroidJUnit4::class)
internal class InstrumentationInitializerTest {
    private lateinit var fieldRef: Field
    private lateinit var hucLiteDataSource: HucLiteDataSource
    private lateinit var instrumentationInitializer: InstrumentationInitializer
    private var staticURLStreamHandlerFactorySet = false

    @Before
    fun setup() {
        factoryField = null
        fieldRef = checkNotNull(this::class.java.declaredFields.find { it.name == "factoryField" })
        fieldRef.isAccessible = true
        hucLiteDataSource = HucLiteDataSource(FakeInstrumentationInstallArgs(mockk()))
    }

    @Test
    fun `initialization with no previous factory works correctly`() {
        instrumentationInitializer = InstrumentationInitializer(
            streamHandlerFactoryFieldProvider = { fieldRef },
            factoryInstaller = {
                factoryField = it
                attemptToSetURLStreamHandlerFactory(it)
            }
        )
        instrumentationInitializer.install()
        assertTrue(factoryField is InstrumentedUrlStreamHandlerFactory)
        attemptToSetURLStreamHandlerFactory(FakeURLStreamHandlerFactory())
        assertTrue(staticURLStreamHandlerFactorySet)
    }

    @Test
    fun `initialization with existing factory works correctly`() {
        factoryField = FakeURLStreamHandlerFactory()
        instrumentationInitializer = InstrumentationInitializer(
            streamHandlerFactoryFieldProvider = { fieldRef },
            factoryInstaller = { factoryField = it }
        )
        instrumentationInitializer.install()
        assertTrue(factoryField is DelegatingInstrumentedURLStreamHandlerFactory)
    }

    private fun InstrumentationInitializer.install() {
        installURLStreamHandlerFactory(
            clock = FakeClock(),
            hucLiteDataSource = hucLiteDataSource
        ) { throw it }
    }

    private fun attemptToSetURLStreamHandlerFactory(
        factory: URLStreamHandlerFactory
    ) {
        try {
            URL.setURLStreamHandlerFactory(factory)
        } catch (t: Throwable) {
            if (t.message != "factory already defined") {
                throw t
            }
        } finally {
            staticURLStreamHandlerFactorySet = true
        }
    }

    companion object {
        var factoryField: URLStreamHandlerFactory? = null
    }
}
