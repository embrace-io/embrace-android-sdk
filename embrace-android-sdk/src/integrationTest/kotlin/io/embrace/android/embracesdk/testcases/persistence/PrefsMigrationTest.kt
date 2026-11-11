package io.embrace.android.embracesdk.testcases.persistence

import android.content.Context
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.prefs.defaultPrefsFile
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.semconv.EmbAppAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testframework.InMemorySharedPreferences
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * An install upgrading from 9.x keeps the state it kept in prefs, which the first launch moves into `xcor/install_state/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class PrefsMigrationTest(
    private val otelSdkMode: OtelSdkMode,
) {

    private val rule = NetworkCaptureRuleRemoteConfig(
        id = "rule",
        duration = null,
        method = "GET",
        urlRegex = "embrace.io",
        maxCount = 5,
    )
    private val remoteConfig = RemoteConfig(networkCaptureRules = setOf(rule))

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode)

    @Test
    fun `a 9x install keeps its prefs state`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            setupAction = {
                prefs().edit()
                    .putString(DEVICE_ID_KEY, LEGACY_DEVICE_ID)
                    .putInt(Ordinal.SESSION.key, 41)
                    .putInt(Ordinal.APP_VERSION_STARTUP.key, 1)
                    .putString(checkNotNull(Ordinal.APP_VERSION_STARTUP.scopeKey), UNKNOWN_APP_VERSION)
                    .putInt("$NETWORK_CAPTURE_RULE_PREFIX${rule.id}", 3)
                    .commit()
                seedLegacyPrefsState {
                    userId = USER_ID
                    sessionProperties = mapOf(PROPERTY_KEY to PROPERTY_VALUE)
                }
                standInPrefsFile()
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val attrs = checkNotNull(getSingleSessionEnvelope().findSessionPartSpan().attributes)
                assertEquals("42", attrs.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_NUMBER))
                assertEquals("2", attrs.findAttributeValue(EmbAppAttributes.EMB_APP_VERSION_STARTUP_COUNTER))

                val state = testRule.services.persistedSdkStateStore.value.snapshot()
                assertEquals(LEGACY_DEVICE_ID, state.deviceId)
                assertEquals(USER_ID, state.userId)
                assertEquals(PROPERTY_VALUE, state.sessionProperties[PROPERTY_KEY])
                assertEquals(3, testRule.services.networkCaptureRuleStore.value.remaining(rule.id, rule.maxCount))
                assertEquals(emptyList<String>(), prefs().all.keys.filter(::isSdkKey))
            },
        )
    }

    @Test
    fun `a fresh install starts from session 1 without opening prefs`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val attrs = checkNotNull(getSingleSessionEnvelope().findSessionPartSpan().attributes)
                assertEquals("1", attrs.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_NUMBER))
                assertEquals(emptySet<String>(), InMemorySharedPreferences.requestedNames(appContext()))
                assertFalse(defaultPrefsFile(appContext()).exists())
            },
        )
    }

    private fun appContext(): Context = testRule.setup.services.appContext

    private fun prefs(): InMemorySharedPreferences =
        InMemorySharedPreferences.get(appContext(), "${appContext().packageName}_preferences")

    private fun standInPrefsFile() {
        defaultPrefsFile(appContext()).apply {
            checkNotNull(parentFile).mkdirs()
            writeText("<map />")
        }
    }

    private fun isSdkKey(key: String) = key.startsWith("io.embrace.") || key == "embrace.user_session"

    internal companion object {
        const val DEVICE_ID_KEY = "io.embrace.deviceid"
        const val LEGACY_DEVICE_ID = "9x-device-id"
        const val UNKNOWN_APP_VERSION = "UNKNOWN"
        const val USER_ID = "user-1"
        const val PROPERTY_KEY = "perm"
        const val PROPERTY_VALUE = "value"
        const val NETWORK_CAPTURE_RULE_PREFIX = "io.embrace.networkcapturerule"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
