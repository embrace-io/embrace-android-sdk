package io.embrace.android.testapiserver

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HeldCertificate
import okhttp3.tls.decodeCertificatePem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.net.ssl.SSLHandshakeException

internal class FakeBackendTlsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var tls: FakeBackendTls
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        tls = bundledFakeBackendTls()
        server = MockWebServer().apply {
            dispatcher = FakeApiServer(SdkBackendApi(RemoteConfigEndpoint("{}", "\"etag\"")))
            useHttps(tls.serverCertificates.sslSocketFactory(), false)
            protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
            start()
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `the build's certificates serve the fake over HTTP 2 to a client that trusts their root alone`() {
        val client = OkHttpClient.Builder()
            .sslSocketFactory(tls.clientCertificates.sslSocketFactory(), tls.clientCertificates.trustManager)
            .build()

        client.newCall(post("/v2/logs")).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals(Protocol.HTTP_2, response.protocol)
            assertEquals("localhost", response.request.url.host)
        }
    }

    @Test
    fun `a client that trusts only the platform's roots refuses the fake`() {
        assertThrows(SSLHandshakeException::class.java) {
            OkHttpClient().newCall(post("/v2/logs")).execute().close()
        }
    }

    @Test
    fun `generated certificates are an RSA root, intermediate and leaf for the loopback host, and read back as written`() {
        val dir = folder.newFolder()
        val generated = generateFakeBackendTls()
        generated.writeTo(dir)

        val root = dir.resolve(FakeBackendTls.ROOT_PEM).readText().decodeCertificatePem()
        val intermediate = dir.resolve(FakeBackendTls.INTERMEDIATE_PEM).readText().decodeCertificatePem()
        val leaf = HeldCertificate.decode(dir.resolve(FakeBackendTls.LEAF_PEM).readText())

        assertEquals(generated.root, root)
        listOf(root, intermediate, leaf.certificate).forEach { assertEquals("RSA", it.publicKey.algorithm) }
        root.verify(root.publicKey)
        intermediate.verify(root.publicKey)
        leaf.certificate.verify(intermediate.publicKey)
        assertTrue(root.basicConstraints >= 1)
        assertTrue(intermediate.basicConstraints >= 0)
        assertEquals(-1, leaf.certificate.basicConstraints)
        assertEquals(setOf("localhost", "127.0.0.1"), leaf.certificate.subjectAlternativeNames.map { it[1] }.toSet())

        val read = readFakeBackendTls(
            dir.resolve(FakeBackendTls.ROOT_PEM).readText(),
            dir.resolve(FakeBackendTls.INTERMEDIATE_PEM).readText(),
            dir.resolve(FakeBackendTls.LEAF_PEM).readText(),
        )
        assertEquals(root, read.root)
    }

    private fun post(path: String): Request =
        Request.Builder().url(server.url(path)).post(ByteArray(0).toRequestBody()).build()
}
