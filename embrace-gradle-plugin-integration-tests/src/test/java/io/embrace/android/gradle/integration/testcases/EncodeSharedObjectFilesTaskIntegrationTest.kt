package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.buildFile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EncodeSharedObjectFilesTaskIntegrationTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val expectedBase64Encoding = "eyJzeW1ib2xzIjp7ImFybWVhYmktdjdhIjp7ImxpYmVtYi1kb251dHMuc28iOiI1ZDgxMTAwYzUxYWRkYjU3MjY0NDgwNz" +
        "FhMjk0N2JjY2U2YzMyZTJjIiwibGliZW1iLWNyaXNwcy5zbyI6ImNhNDg5NTYyODIxMzEwNDIzNzExYjY4Nzg4N2Y1Y2EzMzNiN2ZkNjkifSwieDg2Ijp7ImxpYmVtY" +
        "i1kb251dHMuc28iOiIxMWUxYzNjYzM5ZmM1MDZkMDZmY2RiNzViYjRkMDA1MzQ1YWJhOTVlIiwibGliZW1iLWNyaXNwcy5zbyI6ImFjZTM3NTVlMjFjNjk2MWU5YzNl" +
        "MzI5MjE2ZWViNzMzMzg0MDA3NGIifSwiYXJtNjQtdjhhIjp7ImxpYmVtYi1kb251dHMuc28iOiJiNTY1MmM1OTdhYmVlYWI5YTRhMmJmYTU3NzlmYzNkNDFhNzkyMGZ" +
        "lIiwibGliZW1iLWNyaXNwcy5zbyI6IjI5ZmU5YmE5YzkwNWI2Y2EwMzU3ODYyZDNiYjQ3MmQ4ZDE4NzgxNTkifSwieDg2XzY0Ijp7ImxpYmVtYi1kb251dHMuc28iOi" +
        "I2MDFiOTAxYWU1ZGMxZWQxNDhhM2YwMWNlZjExM2YwZTdkZGJmM2NhIiwibGliZW1iLWNyaXNwcy5zbyI6IjIzMTMyNDJmYTBhNDY0ZjFkOTEwNWE4N2NhZTZmY2ViY" +
        "zNhY2JkZWMifX19"

    @Test
    fun `map is encoded correctly`() {
        rule.runTest(
            fixture = "encode-shared-object-files",
            assertions = { projectDir ->
                val encodedMap = projectDir.buildFile("encoded_map.txt").bufferedReader().use { it.readText() }
                assertEquals(expectedBase64Encoding, encodedMap)
            }
        )
    }
}
