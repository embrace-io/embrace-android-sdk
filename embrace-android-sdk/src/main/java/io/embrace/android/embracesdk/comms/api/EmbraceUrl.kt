package io.embrace.android.embracesdk.comms.api

import java.io.IOException
import java.net.MalformedURLException

internal abstract class EmbraceUrl {

    override fun toString(): String {
        return file
    }

    @Throws(IOException::class)
    abstract fun openConnection(): EmbraceConnection
    abstract val file: String

    internal interface UrlFactory {
        fun getInstance(url: String): EmbraceUrl
    }

    companion object {
        private var embraceUrlFactory: UrlFactory? = null

        @JvmStatic
        fun setEmbraceUrlFactory(urlConstructor: UrlFactory?) {
            embraceUrlFactory = urlConstructor
        }

        @Throws(MalformedURLException::class)
        @JvmStatic
        fun getUrl(url: String): EmbraceUrl {
            embraceUrlFactory?.let {
                try {
                    return it.getInstance(url)
                } catch (expected: Exception) {
                }
            }
            return EmbraceUrlImpl(url)
        }
    }
}
