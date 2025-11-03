package io.embrace.android.embracesdk.instrumentation.huclite

import android.annotation.SuppressLint
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.HucLiteDataSource
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory

class InstrumentationInitializer(
    private val streamHandlerFactoryFieldProvider: () -> Field? = fun(): Field? {
        // Use reflection to get the field holding the factory
        val fields = URL::class.java.declaredFields
        for (current in fields) {
            if (Modifier.isStatic(current.modifiers) && current.type == URLStreamHandlerFactory::class.java) {
                current.isAccessible = true
                return current
            }
        }
        return null
    },
    private val factoryInstaller: (URLStreamHandlerFactory) -> Unit = { factory ->
        URL.setURLStreamHandlerFactory(factory)
    },
) {
    /**
     * Replace existing [URLStreamHandlerFactory] with one where HTTPS connections are instrumented.
     */
    fun installURLStreamHandlerFactory(
        clock: Clock,
        logger: EmbLogger,
        hucLiteDataSource: HucLiteDataSource,
    ) {
        @SuppressLint("PrivateApi")
        val httpsHandlerClass = Class.forName("com.android.okhttp.HttpsHandler")
        val instrumentedUrlStreamHandlerFactoryProvider = {
            InstrumentedUrlStreamHandlerFactory(
                httpsHandler = httpsHandlerClass.getDeclaredConstructor().newInstance() as URLStreamHandler,
                clock = clock,
                hucLiteDataSource = hucLiteDataSource,
            )
        }

        val newFactory =
            streamHandlerFactoryFieldProvider()?.let { field ->
                val existingFactory: Any? = field.get(null)
                if (existingFactory is URLStreamHandlerFactory) {
                    field.set(null, null)
                    DelegatingInstrumentedURLStreamHandlerFactory(
                        delegateHandlerFactory = existingFactory,
                        instrumentedHandlerFactory = instrumentedUrlStreamHandlerFactoryProvider,
                        clock = clock,
                        logger = logger,
                        hucLiteDataSource = hucLiteDataSource,
                    )
                } else {
                    null
                }
            }

        factoryInstaller(newFactory ?: instrumentedUrlStreamHandlerFactoryProvider())
    }
}
