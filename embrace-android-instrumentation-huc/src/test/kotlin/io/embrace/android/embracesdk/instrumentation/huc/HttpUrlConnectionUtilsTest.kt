package io.embrace.android.embracesdk.instrumentation.huc

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.net.URL
import java.net.URLConnection

internal class HttpUrlConnectionUtilsTest {
    private val url = URL("http", "embrace.io", 8080, "index.html")

    @Test
    fun `findDeclaredMethod traverses class hierarchy to find method`() {
        listOf(
            FakeURLStreamHandler(),
            WrapperURLStreamHandler(),
            NoOpenConnectionURLStreamHandler(),
            WrapperNoOpenConnectionURLStreamHandler()
        ).forEach { urlStreamHandler ->
            val method = findDeclaredMethod(
                obj = urlStreamHandler,
                objClz = urlStreamHandler.javaClass,
                methodName = EmbraceUrlStreamHandler.METHOD_NAME_OPEN_CONNECTION,
                url.javaClass
            )
            assertNotNull(method)
        }
    }

    @Test(expected = NoSuchMethodException::class)
    fun `findDeclaredMethod throws when the method name doesn't exist`() {
        val urlStreamHandler = FakeURLStreamHandler()
        findDeclaredMethod(
            obj = urlStreamHandler,
            objClz = urlStreamHandler.javaClass,
            methodName = "nahhhh",
            url.javaClass
        )
    }

    @Test(expected = NoSuchMethodException::class)
    fun `findDeclaredMethod throws when the method parameters do not match what is expected`() {
        val urlStreamHandler = FakeURLStreamHandler()
        findDeclaredMethod(
            obj = urlStreamHandler,
            objClz = urlStreamHandler.javaClass,
            methodName = EmbraceUrlStreamHandler.METHOD_NAME_OPEN_CONNECTION,
            url.javaClass,
            url.javaClass
        )
    }

    private open class WrapperURLStreamHandler : FakeURLStreamHandler() {
        override fun openConnection(url: URL): URLConnection {
            return super.openConnection(url)
        }
    }

    private open class NoOpenConnectionURLStreamHandler : FakeURLStreamHandler()

    private open class WrapperNoOpenConnectionURLStreamHandler : NoOpenConnectionURLStreamHandler()
}
