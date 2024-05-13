package io.embrace.android.embracesdk.network.logging

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec

@RunWith(AndroidJUnit4::class)
internal class NetworkCaptureEncryptionManagerTest {

    private lateinit var networkCaptureEncryptionManager: NetworkCaptureEncryptionManager

    @Before
    fun setup() {
        networkCaptureEncryptionManager = NetworkCaptureEncryptionManager(EmbLoggerImpl())
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

        assertEquals(encryptedText, null)
    }

    @Test
    fun `test encrypt and decrypt long payload correctly`() {
        val encryptedText = checkNotNull(networkCaptureEncryptionManager.encrypt(encryptedPayload, sPublicKey))

        assertNotNull(decrypt(encryptedText))
    }

    private fun decrypt(data: String): String? {
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(sPrivateKey, Base64.DEFAULT)))
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
        "MIIEpQIBAAKCAQEAuAZAv5tzK9Ab/DsVpNaYiuslKQsOHjz4N4haZLT8VaVIrlVj\n" +
            "tkd5nPrVgEKStQf6PKnQ+1C0Tp069b6aPUkG22UL96nCKQ1eCIwRUT+Da7ac2YVu\n" +
            "L21+HTs1KxLEWgN7qGy1uYNonrpsiY3XqzDvYMo65oFzbBV+yctuGHDFaulULJiL\n" +
            "L8cE3/Rg3T0RfHK+C5/PqC8FBj6kn3FP9FZJM4cty3nzbNWknj8r7+ikmOwma6CH\n" +
            "EZz2u1gwPhIchNxNKuUF+4vxcBre9V/96LYOjSOGSDJmJN6ehUJjUpu7YSuGCki8\n" +
            "YoLHAyoD/mYy7N/hYSeZwHiNjM+r44lZHNQTpwIDAQABAoIBAQCP8ww5Fd9cmVka\n" +
            "0BkZLWh72n7iASzVCHpd7kJPXqe4Uydsf40VLAn8etYBk5HxHEFprKi1viadDC7v\n" +
            "xl4erH45pmxbGiawOC2jX/W36Yfi/SDqoo5TeUHamdL4U6DWjLzxPcBVUm7HIyr9\n" +
            "2r+mwQuvWeIDJ6XjGVlpfsErSyOSgYh/XTCa1hawSRT5PAfOifOKMwF0Yqr5fXZY\n" +
            "BWTjm3M/PV+kamF118/Y+DZeEr6liG/OyIeWGcnv0IqxxxDukMbqZLuSZo9t1wEZ\n" +
            "lTganTsS5avNfrMuL29YQjdzmU2p471ZM381Z+a5sqD/T/+mpnv05Ae8DvgMJo7c\n" +
            "EE68r305AoGBAORFzo4iGNIVsCjRpSkMeQXN4UOG3Tv3orKJaMlas101lMQwHmu6\n" +
            "Ui8PqUK5rz8ziZvLyPJjo8lSbsVYO/RH9BxwJOIBxHbQTYTfSQyBeW5dom3LNN1k\n" +
            "xEXiGPqQcn/rGPYVCJffImQYE2UbMad6ju0qg2YHW0CWecTE5zC9nRl9AoGBAM5g\n" +
            "iTiK8/UL7yusph6lTxiNnDZztEskITE7xY5mda4VBkckRMO8J6hEyLMnbWvi645M\n" +
            "ckCoeTRklwJ4AdaSzKXdbFywIxVkB+ciYJ76juC7pIRmgaNaKgx0og1wtzc4e9KQ\n" +
            "Uch+2MIHkgfPQrY/vBmS+rBqE4cK/H8LLqpSbgrzAoGBAKQtLLz++vkGDjedaHsY\n" +
            "dGZfR3eIpM8/cK2VtF61NDGCmudrcEWssPUV/3d1EvyStZLuwyzJyv+9oNugdSZh\n" +
            "JcnaQjymZsXJVSeOa/xplotxHqR2tSPSGHPmhG6ZuzATR1WdlRudqR9yTWi3YUQC\n" +
            "Go+qtuyHt/LBBv0lXN2qUjYFAoGAUw4oy1eonJrj8zi1VioDLgd3sbZY/dCZhx3e\n" +
            "ANQdUiTl9OWUww1LDH46I1efwsZ9NDRx2rGyrbI5z+WKH9fOgoYdISRFyksKnyuH\n" +
            "pRODQtBhgmNakuord/3MZgpRweh6dKBeOYlLJLM1Qu1XlM8LnWM4fp0CJNv4CAzx\n" +
            "B9zKqp8CgYEAysNuHbhUCkr0ZuUjPaKmZegxjyiqkrahNSZJjVQJfmicTwJBlqAv\n" +
            "Z10/PLpJ7dXNS1DdHT5hLXczCd/9BA9EsmiU6Ny/TshHw2BcSBLfQBS7KYEh/+cQ\n" +
            "D256kKbCmQKlNYs3bZEuAcVsrBabIPNIfxMzc+bdxUsW+fTw8DP8zzE="
}
