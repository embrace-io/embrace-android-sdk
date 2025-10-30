package io.embrace.android.embracesdk.instrumentation.huclite

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbraceInternalInterface
import io.embrace.android.embracesdk.fakes.FakeInstrumentationApi
import io.embrace.android.embracesdk.fakes.FakeNetworkRequestApi
import io.embrace.android.embracesdk.fakes.FakeSdkStateApi
import io.embrace.android.embracesdk.fakes.FakeURLStreamHandlerFactory
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    private lateinit var instrumentationInitializer: InstrumentationInitializer
    private lateinit var fakeSdkStateApi: SdkStateApi
    private lateinit var fakeInstrumentationApi: InstrumentationApi
    private lateinit var fakeNetworkingApi: NetworkRequestApi
    private lateinit var fakeInternalInterface: FakeEmbraceInternalInterface

    @Before
    fun setup() {
        factoryField = null
        fieldRef = checkNotNull(this::class.java.declaredFields.find { it.name == "factoryField" })
        fieldRef.isAccessible = true
        fakeSdkStateApi = FakeSdkStateApi()
        fakeInstrumentationApi = FakeInstrumentationApi()
        fakeNetworkingApi = FakeNetworkRequestApi()
        fakeInternalInterface = FakeEmbraceInternalInterface()
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
        instrumentationInitializer.installURLStreamHandlerFactory(
            sdkStateApi = fakeSdkStateApi,
            instrumentationApi = fakeInstrumentationApi,
            networkRequestApi = fakeNetworkingApi,
            internalInterface = fakeInternalInterface
        )
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
        instrumentationInitializer.installURLStreamHandlerFactory(
            sdkStateApi = fakeSdkStateApi,
            instrumentationApi = fakeInstrumentationApi,
            networkRequestApi = fakeNetworkingApi,
            internalInterface = fakeInternalInterface
        )
        assertTrue(factoryField is DelegatingInstrumentedURLStreamHandlerFactory)
    }

    @Test
    fun `method invocation works correctly`() {
        val instrumentationClass = Class.forName("io.embrace.android.embracesdk.instrumentation.huclite.InstrumentationInitializer")
        val instrumentationObject = instrumentationClass.getDeclaredConstructor().newInstance()
        val initMethod = instrumentationObject::class.java.getDeclaredMethod(
            "installURLStreamHandlerFactory",
            SdkStateApi::class.java,
            InstrumentationApi::class.java,
            NetworkRequestApi::class.java,
            EmbraceInternalInterface::class.java,
        )

        initMethod.invoke(
            instrumentationObject,
            fakeSdkStateApi,
            fakeInstrumentationApi,
            fakeNetworkingApi,
            fakeInternalInterface,
        )

        val errorMessage = checkNotNull(fakeInternalInterface.internalErrors.single().message)
        assertEquals(true, errorMessage.startsWith("Unable to make field private static volatile java.net.URLStreamHandlerFactory"))
    }

    companion object {
        var factoryField: URLStreamHandlerFactory? = null
    }
}
