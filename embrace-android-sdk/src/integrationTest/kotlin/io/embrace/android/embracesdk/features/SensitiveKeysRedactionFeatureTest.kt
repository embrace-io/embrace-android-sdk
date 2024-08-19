package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.recordSession
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SensitiveKeysRedactionFeatureTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(startImmediately = false)
    }

    @Before
    fun setUp() {
        testRule.harness.overriddenConfigService.sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(
            localConfig = SdkLocalConfig(
                sensitiveKeysDenylist = listOf("password")
            )
        )
    }

    @Test
    fun `custom span properties are redacted if they are sensitive`() {
        with(testRule) {
            startSdk()
            val session = harness.recordSession {
                val span = embrace.startSpan("test span")
                span?.addAttribute("password", "1234")
                span?.addAttribute("not a password", "1234")
                span?.stop()
            }

            val recordedSpan = session?.data?.spans?.find { it.name == "test span" }
            val sensitiveAttribute = recordedSpan?.attributes?.first { it.key == "password" }
            val notSensitiveAttribute = recordedSpan?.attributes?.first { it.key == "not a password" }

            assertEquals("<redacted>", sensitiveAttribute?.data)
            assertEquals("1234", notSensitiveAttribute?.data)
        }
    }
}