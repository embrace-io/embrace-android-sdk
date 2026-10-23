package io.embrace.android.embracesdk.internal.instrumentation.network

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

internal class NetworkCaptureEncryptionManagerTest {

    private lateinit var networkCaptureEncryptionManager: NetworkCaptureEncryptionManager

    @Before
    fun setup() {
        networkCaptureEncryptionManager = jvmEncryptionManager()
    }

    @Test
    fun `test encrypt and decrypt correctly`() {
        val textToEncrypt = "text to encrypt"
        val encryptedText = checkNotNull(networkCaptureEncryptionManager.encrypt(textToEncrypt, sPublicKey))

        assertEquals(textToEncrypt, decrypt(encryptedText))
    }

    @Test
    fun `test encrypt exception`() {
        val textToEncrypt = "text to encrypt"
        val encryptedText = networkCaptureEncryptionManager.encrypt(textToEncrypt, "12345")

        Assert.assertEquals(encryptedText, null)
    }

    @Test
    fun `test encrypt and decrypt long payload correctly`() {
        val encryptedText = checkNotNull(networkCaptureEncryptionManager.encrypt(encryptedPayload, sPublicKey))

        assertNotNull(decrypt(encryptedText))
    }

    private fun decrypt(data: String): String? {
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(sPrivateKey)))
        return networkCaptureEncryptionManager.decrypt(data, privateKey)
    }

    private val encryptedPayload =
        "onilxHAND1nF2t21dktOG16FeLzICtqwSeW9IM5X4sFnuM+ixvI0mGGTuXzCTFAmISh+H0zwQte9\n" +
            "OC3/+FilKZuLWGTxVHulaExzMj8tjRk6+gelfFyA+V9jaad7MQUfQGMFbs7GTVX3RbLgJtmp7giF" +
            "\nThSOHmbYEw5l0C2IuHBF6O/xEIsdlMEce2fVHp8iU29KCXr8OyH5tBbXM5yb9jIqbolIiyOk2rIR" +
            "\nclh/wPRQ4pUDxyLefJcnHSEh24Fj/7X3K2PvmQaKsVHMkrrVj6wc/bmrIVWCdrB54Hl+3GiJFWPi" +
            "\nkTCUvBBwv8HkJP/CL3rzHf+KoilQ9DgocRUO5lSmoFyb7I+Z7iSPz7ybpW93N/b+VJ5hau+3aVUD" +
            "\nH1A9fkegMXlGxJRPoJ0qT5b8zMKewBk5KAYVMpAIaXCEiAgJmiYvbquCsJ6aVHWFDgGqiEw7HcJB" +
            "\nBj3INb6NVw6SY+hsLwKv3XIVwxZd+38ofUrtj1ZnH1KvxnvRFHc+aL40VneTxct0Oei4Do+dZENJ" +
            "\ngSpd0d3FYJbrfjytS5ghQUcXlZUWNk787rb6NFxb3a3qtmB/bWZT15BR+vmdMSg6Xe5i//nJXT68" +
            "\n134V+JdBOLxvKZ+6CqBoqSlor+rnT6lIaN9ZlPmjLKgXoAw73IxzgttpabQ+MvY4Tz6V9lCG6AsV" +
            "\ngw0+D64mrhBIETXo7y2RwH3/s0VRVIbKDdFmmHDMczPLkpI5ucDSdZvwzWl1CJea1o9lnPTHzgXt" +
            "\nHBRcfom9k7q8BpYzF1RNhgBjMtPQbA5Cia5hZ3ZqBfA1fuWCc27hDR8zNoRcaAl9ldxKmqruhKKX" +
            "\nfa5+GWuteSrA0x3mC58yEKHtN0prRj9x7DQ1UGqMktqcep9JsL9BkQ7NqWyyg6ID4ZIarwaL1Ivr" +
            "\nQqTPVzvr8ZJ8ZQkbDsu570EDUvPC6ZFKZbsq+SyYd9EXJeftmhC7qFoXKzV1Ii8Sm3kL4PveM7/T" +
            "\nBRonOlkj6wpwL0Uk/aViv3iCRDeGwgesg0tCMws7sidn8GXMwM18o0+eF0qb7bKlH6yYYK4iWlrI" +
            "\nPD2Bp9gb8gwrbSI2injJeG+jrUx7bT0dYJ+crHtqBJ/kY+b6OfzvXP6DxuKN+DEn71nOOO6FEEp2" +
            "\nsdMqOkEfM0/1Vi/3pBL7oauMHhERXo/SJ+sT+MOCj18dtHHtXF0UZ0o8BLYvpQ0KR54iaj03ffuN" +
            "\ndQk+uAysGdeXQZLq76U3bVyhRjvnivNt085qUMG1Gw672+A74ABCfq8q0nNoTPQPO9DaVc2ddYt1" +
            "\nfcwf3bUcfJoy44zX00is5RJMDhbtxLs+8UP+IXslhaY3KE0WBI3tZTUNoYQy5WuCNrEhPve0obb8" +
            "\nobMx+d4+0wLJ2cpXPZDyBRY0f8thYGjkblObAw8hq5Jt2ubat5Q1wS3Hlpm46VCcmT992iwYHPkq" +
            "\nrNJw3cB5tai384+c6nFFLQPjEAEiUm8KOpMPgbojfR+DEKmBUfBy248VgKfTOgyxCwtPUTflEJNR" +
            "\nofRMYqqDQy2KdIaORtc6dUozwK2ReJNT2Ha+W3idcNgTbug89TavGwomowqmcM8B6qfxB7Tmz5YA" +
            "\nRdGXGfTK7YNf8/t3CEexJ+XGyhAXirBI8VklW15MIALspTy7y24TvvaINS6+W2bzAa4Rut6fYklI" +
            "\nDh8sATxOCK4aRVZoShhbIJLBGkexdOF72kY+6HtDd5/oBtM3H7Smxjku2Q+VaELspi1Pe0xZlCSe" +
            "\nmej/Yux36VzftCCfzKl/JfDV+jgIC315pp6JQLEw3ushK0vHVtasrbu2O209victMjRhJ5LFZccl" +
            "\nnAhvyDMuuPzgwIiljfHLa4JhQj9MHe8cOBEsRRY+NnFvdbgXASTLc+fXEZt2eGtOMNSF03Fdw3s6" +
            "\nCp4isGCX8eL0RVLENMFJVhCC+NxWLZMgUi4rWqsQp5Hm5GrrdmAbSlEMhAtHezKszsS+Ch45kGPO" +
            "\nqEJHl1jfSO7NR6o2ecZUND7z1UEXCOJuLYVZxBe1q+n2Uh5FNY3uh3+xKPa6zNILr0Ew8/92mFdL" +
            "\nIExHmCZLnbVbL1yLva54t2CZrmRMBI7JkRo2xogL4COSL0XVmTddUxtpjMnHPXADnlM7Q38Xa83h" +
            "\nUpHOBHQTpHy0l7+qROeTE5pvw7v3vxoczl+VCy2GUabF2WgfjrPmooNZkgWfiKMf9kjSIVw2OCDA" +
            "\nAzSYGgdgENmsf8tEY6W7ff775EvtbR58+V/HosSWu6B5n+K8PIWU/GMyX36CevUVtQPUmyso/" +
            "\nSBjTwoK/Jezjun1cnbxcYFv62l0wv20mG+HSaSGofKb1CarM6pOzxSwvD2VxaBNleiyyXXp8MzGI" +
            "\nmJuhjVRmqfN3AUCiLK4D6SJz+bGHq5U2dw==\n"

    private val sPublicKey: String =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuAZAv5tzK9Ab/DsVpNaY\n" +
            "iuslKQsOHjz4N4haZLT8VaVIrlVjtkd5nPrVgEKStQf6PKnQ+1C0Tp069b6aPUkG\n" +
            "22UL96nCKQ1eCIwRUT+Da7ac2YVuL21+HTs1KxLEWgN7qGy1uYNonrpsiY3XqzDv\n" +
            "YMo65oFzbBV+yctuGHDFaulULJiLL8cE3/Rg3T0RfHK+C5/PqC8FBj6kn3FP9FZJ\n" +
            "M4cty3nzbNWknj8r7+ikmOwma6CHEZz2u1gwPhIchNxNKuUF+4vxcBre9V/96LYO\n" +
            "jSOGSDJmJN6ehUJjUpu7YSuGCki8YoLHAyoD/mYy7N/hYSeZwHiNjM+r44lZHNQT\n" +
            "pwIDAQAB"

    private val sPrivateKey: String =
        "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQC4BkC/m3Mr0Bv8\n" +
            "OxWk1piK6yUpCw4ePPg3iFpktPxVpUiuVWO2R3mc+tWAQpK1B/o8qdD7ULROnTr1\n" +
            "vpo9SQbbZQv3qcIpDV4IjBFRP4NrtpzZhW4vbX4dOzUrEsRaA3uobLW5g2ieumyJ\n" +
            "jderMO9gyjrmgXNsFX7Jy24YcMVq6VQsmIsvxwTf9GDdPRF8cr4Ln8+oLwUGPqSf\n" +
            "cU/0Vkkzhy3LefNs1aSePyvv6KSY7CZroIcRnPa7WDA+EhyE3E0q5QX7i/FwGt71\n" +
            "X/3otg6NI4ZIMmYk3p6FQmNSm7thK4YKSLxigscDKgP+ZjLs3+FhJ5nAeI2Mz6vj\n" +
            "iVkc1BOnAgMBAAECggEBAI/zDDkV31yZWRrQGRktaHvafuIBLNUIel3uQk9ep7hT\n" +
            "J2x/jRUsCfx61gGTkfEcQWmsqLW+Jp0MLu/GXh6sfjmmbFsaJrA4LaNf9bfph+L9\n" +
            "IOqijlN5QdqZ0vhToNaMvPE9wFVSbscjKv3av6bBC69Z4gMnpeMZWWl+wStLI5KB\n" +
            "iH9dMJrWFrBJFPk8B86J84ozAXRiqvl9dlgFZOObcz89X6RqYXXXz9j4Nl4SvqWI\n" +
            "b87Ih5YZye/QirHHEO6Qxupku5Jmj23XARmVOBqdOxLlq81+sy4vb1hCN3OZTanj\n" +
            "vVkzfzVn5rmyoP9P/6ame/TkB7wO+AwmjtwQTryvfTkCgYEA5EXOjiIY0hWwKNGl\n" +
            "KQx5Bc3hQ4bdO/eisoloyVqzXTWUxDAea7pSLw+pQrmvPzOJm8vI8mOjyVJuxVg7\n" +
            "9Ef0HHAk4gHEdtBNhN9JDIF5bl2ibcs03WTEReIY+pByf+sY9hUIl98iZBgTZRsx\n" +
            "p3qO7SqDZgdbQJZ5xMTnML2dGX0CgYEAzmCJOIrz9QvvK6ymHqVPGI2cNnO0SyQh\n" +
            "MTvFjmZ1rhUGRyREw7wnqETIsydta+LrjkxyQKh5NGSXAngB1pLMpd1sXLAjFWQH\n" +
            "5yJgnvqO4LukhGaBo1oqDHSiDXC3Nzh70pBRyH7YwgeSB89Ctj+8GZL6sGoThwr8\n" +
            "fwsuqlJuCvMCgYEApC0svP76+QYON51oexh0Zl9Hd4ikzz9wrZW0XrU0MYKa52tw\n" +
            "Rayw9RX/d3US/JK1ku7DLMnK/72g26B1JmElydpCPKZmxclVJ45r/GmWi3EepHa1\n" +
            "I9IYc+aEbpm7MBNHVZ2VG52pH3JNaLdhRAIaj6q27Ie38sEG/SVc3apSNgUCgYBT\n" +
            "DijLV6icmuPzOLVWKgMuB3extlj90JmHHd4A1B1SJOX05ZTDDUsMfjojV5/Cxn00\n" +
            "NHHasbKtsjnP5Yof186Chh0hJEXKSwqfK4elE4NC0GGCY1qS6it3/cxmClHB6Hp0\n" +
            "oF45iUskszVC7VeUzwudYzh+nQIk2/gIDPEH3MqqnwKBgQDKw24duFQKSvRm5SM9\n" +
            "oqZl6DGPKKqStqE1JkmNVAl+aJxPAkGWoC9nXT88uknt1c1LUN0dPmEtdzMJ3/0E\n" +
            "D0SyaJTo3L9OyEfDYFxIEt9AFLspgSH/5xAPbnqQpsKZAqU1izdtkS4BxWysFpsg\n" +
            "80h/EzNz5t3FSxb59PDwM/zPMQ=="
}
