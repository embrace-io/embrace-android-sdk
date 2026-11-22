package io.embrace.android.testapiserver

import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okhttp3.tls.certificatePem
import okhttp3.tls.decodeCertificatePem
import java.io.File
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

/**
 * TLS for a fake of Embrace's server backend served over HTTPS, so that its clients do the work they do against the
 * real backend: a handshake, and verification of a certificate chain shaped like the real one. That is an RSA-2048
 * leaf for `localhost` and `127.0.0.1`, signed by an RSA-2048 intermediate, signed by an RSA-2048 [root].
 *
 * The certificates are generated for each build ([generateFakeBackendTls]) and never committed. The build writes them
 * as PEM files ([writeTo]) into this module's resources, where [bundledFakeBackendTls] reads them, and offers the
 * root to the benchmarked apps, which trust it for `localhost` alone.
 */
class FakeBackendTls(
    /** The root the chain ends at; a client of the fake trusts it, and nothing else. */
    val root: X509Certificate,
    private val intermediate: X509Certificate,
    private val leaf: HeldCertificate,
) {
    /** The server side: presents the leaf and the intermediate. */
    val serverCertificates: HandshakeCertificates = HandshakeCertificates.Builder()
        .heldCertificate(leaf, intermediate)
        .build()

    /** A client side that trusts [root] alone, for a client of the fake other than the app under test. */
    val clientCertificates: HandshakeCertificates = HandshakeCertificates.Builder()
        .addTrustedCertificate(root)
        .build()

    /** Writes the certificates, and the leaf's private key, into [dir] as the PEM files [readFakeBackendTls] reads. */
    fun writeTo(dir: File) {
        dir.mkdirs()
        File(dir, ROOT_PEM).writeText(root.certificatePem())
        File(dir, INTERMEDIATE_PEM).writeText(intermediate.certificatePem())
        File(dir, LEAF_PEM).writeText(leaf.certificatePem() + leaf.privateKeyPkcs8Pem())
    }

    companion object {
        /** The root certificate's file, which the benchmarked apps trust. */
        const val ROOT_PEM: String = "fake-backend-root.pem"

        /** The intermediate certificate's file. */
        const val INTERMEDIATE_PEM: String = "fake-backend-intermediate.pem"

        /** The leaf certificate's file, with its private key after it. */
        const val LEAF_PEM: String = "fake-backend-leaf.pem"
    }
}

/** A new root, intermediate and leaf, valid for [validDays] from now. */
fun generateFakeBackendTls(validDays: Long = VALID_DAYS): FakeBackendTls {
    val root = HeldCertificate.Builder()
        .rsa2048()
        .commonName("Embrace fake backend root")
        .certificateAuthority(1)
        .duration(validDays, TimeUnit.DAYS)
        .build()
    val intermediate = HeldCertificate.Builder()
        .rsa2048()
        .commonName("Embrace fake backend intermediate")
        .certificateAuthority(0)
        .signedBy(root)
        .duration(validDays, TimeUnit.DAYS)
        .build()
    val leaf = HeldCertificate.Builder()
        .rsa2048()
        .commonName(LOCALHOST)
        .addSubjectAlternativeName(LOCALHOST)
        .addSubjectAlternativeName(LOOPBACK)
        .signedBy(intermediate)
        .duration(validDays, TimeUnit.DAYS)
        .build()
    return FakeBackendTls(root.certificate, intermediate.certificate, leaf)
}

/** The certificates [FakeBackendTls.writeTo] wrote, from the text of its files. */
fun readFakeBackendTls(rootPem: String, intermediatePem: String, leafPem: String): FakeBackendTls =
    FakeBackendTls(rootPem.decodeCertificatePem(), intermediatePem.decodeCertificatePem(), HeldCertificate.decode(leafPem))

/**
 * The certificates this build generated, from this module's resources, wherever it runs: on the computer or, inside
 * a benchmark's APK, on the device.
 */
fun bundledFakeBackendTls(): FakeBackendTls {
    fun resource(name: String): String {
        val stream = checkNotNull(FakeBackendTls::class.java.getResourceAsStream("/$RESOURCE_DIR/$name")) {
            "$name isn't among the resources: the build generates it with the generateFakeBackendTls task"
        }
        return stream.bufferedReader().use { it.readText() }
    }
    return readFakeBackendTls(
        resource(FakeBackendTls.ROOT_PEM),
        resource(FakeBackendTls.INTERMEDIATE_PEM),
        resource(FakeBackendTls.LEAF_PEM),
    )
}

/**
 * Writes newly generated certificates into the directory named by the first argument, for the build: run by this
 * module's `generateFakeBackendTls` task.
 */
fun main(args: Array<String>) {
    generateFakeBackendTls().writeTo(File(args.single()))
}

/** The resource directory the build puts the certificates in, which the generateFakeBackendTls task names too. */
private const val RESOURCE_DIR = "fake-backend-tls"

private const val LOCALHOST = "localhost"
private const val LOOPBACK = "127.0.0.1"

/** Longer than any checkout lives, so that a build's certificates don't expire under a benchmark that reuses them. */
private const val VALID_DAYS = 3650L
