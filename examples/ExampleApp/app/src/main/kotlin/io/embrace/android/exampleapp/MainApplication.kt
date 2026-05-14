package io.embrace.android.exampleapp

import android.annotation.SuppressLint
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dev.zacsweers.metro.createGraphFactory
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.otel.java.addJavaLogRecordExporter
import io.embrace.android.embracesdk.otel.java.addJavaSpanExporter
import io.embrace.android.exampleapp.di.AppGraph
import java.net.URL
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory

class MainApplication : Application(), ImageLoaderFactory {

    /**
     * Application-scoped Metro graph. Built once in [onCreate] and exposed for activities,
     * fragments, and Compose code (via `appGraph()`) to resolve dependencies through.
     */
    lateinit var graph: AppGraph
        private set

    /** Coil reads this on first [ImageLoader] access; we delegate to the graph-provided one. */
    override fun newImageLoader(): ImageLoader = graph.imageLoader

    companion object {
        init {
            System.loadLibrary("emb-samples")
        }
    }

    @SuppressLint("PrivateApi")
    private val httpsHandlerClass = Class.forName("com.android.okhttp.HttpsHandler")

    override fun onCreate() {
        super.onCreate()

        // build the application-scoped DI graph; subsequent code resolves through it
        graph = createGraphFactory<AppGraph.Factory>().create(this)

        // load any cached Bluesky posts from disk (cacheDir/social/dynamic_posts.json)
        graph.blueskyFeedStore.loadFromDisk()

        // preinstall an existing URLStreamFactory to ensure the wrapping factor for instrumentation works
        val error = installFakeURLStreamHandlerFactory()

        // add OTel exporters to send data to 3rd party destinations
        Embrace.addJavaSpanExporter(LogcatSpanExporter())
        Embrace.addJavaLogRecordExporter(LogcatLogRecordExporter())
        Embrace.setResourceAttribute("my.cool.resource.id", "innit")

        // start embrace SDK
        Embrace.start(this)
        Embrace.setUserIdentifier("test-bloke")
        Embrace.logInfo(message = "We out here")

        error?.let {
            Embrace.logException(throwable = it, message = "URLStreamHandlerFactory install failed")
        }
    }

    private fun installFakeURLStreamHandlerFactory(): Throwable? =
        runCatching {
            URL.setURLStreamHandlerFactory(TestURLStreamHandlerFactory(httpsHandlerClass))
        }.exceptionOrNull()
}

private class TestURLStreamHandlerFactory(
    private val clazz: Class<*>,
) : URLStreamHandlerFactory {
    override fun createURLStreamHandler(protocol: String?): URLStreamHandler? {
        return if (protocol == "https") {
            clazz.getDeclaredConstructor().newInstance() as URLStreamHandler
        } else {
            null
        }
    }
}
