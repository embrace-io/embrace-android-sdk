package io.embrace.android.exampleapp

import android.annotation.SuppressLint
import android.app.Application
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.otel.java.addJavaLogRecordExporter
import io.embrace.android.embracesdk.otel.java.addJavaSpanExporter
import io.embrace.android.exampleapp.paradigms.bluesky.data.BlueskyFeedStore
import io.embrace.android.exampleapp.paradigms.data.SampleData
import java.net.URL
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory

class MainApplication : Application() {

    companion object {
        init {
            System.loadLibrary("emb-samples")
        }
    }

    @SuppressLint("PrivateApi")
    private val httpsHandlerClass = Class.forName("com.android.okhttp.HttpsHandler")

    override fun onCreate() {
        super.onCreate()

        // wire SampleData to assets; lazy reads parse JSON on first paradigm-screen open
        SampleData.init(this)
        // load any cached Bluesky posts from disk (cacheDir/social/dynamic_posts.json)
        BlueskyFeedStore.init(this)

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
