package io.embrace.android.embracesdk.testcases.persistence

import android.content.Context
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
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
import java.io.File
import java.util.concurrent.TimeUnit

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
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode, superiorStorageSystemEnabled = true)

    @Test
    fun `a 9x install keeps its prefs state`() {
        lateinit var deviceId: String
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            setupAction = {
                superiorStorageSystemEnabled = false
            },
            testCaseAction = {
                embrace.setUserIdentifier(USER_ID)
                embrace.addUserSessionProperty(PROPERTY_KEY, PROPERTY_VALUE, PropertyScope.PERMANENT)
                recordSession()
                deviceId = testRule.services.configService.deviceId
            },
            assertAction = {
                // otherwise the 9.x session is migrated and delivered by the next launch too
                getSingleSessionEnvelope()
                awaitNoLegacyPayloads()
            },
        )

        testRule.runTest(
            persistRemoteConfig = false,
            serverResponseConfig = remoteConfig,
            setupAction = {
                prefs().edit()
                    .putInt(Ordinal.SESSION.key, 41)
                    .putInt("$NETWORK_CAPTURE_RULE_PREFIX${rule.id}", 3)
                    .commit()
                standInPrefsFile()
                getClock().tick(TimeUnit.HOURS.toMillis(1))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val attrs = checkNotNull(getSingleSessionEnvelope().findSessionPartSpan().attributes)
                assertEquals("42", attrs.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_NUMBER))
                assertEquals("2", attrs.findAttributeValue(EmbAppAttributes.EMB_APP_VERSION_STARTUP_COUNTER))

                val state = testRule.services.persistedSdkStateStore.value.snapshot()
                assertEquals(deviceId, state.deviceId)
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

    private fun awaitNoLegacyPayloads() {
        val payloads = File(appContext().filesDir, "embrace_payloads")
        returnIfConditionMet(
            desiredValueSupplier = { true },
            dataProvider = { payloads.list().isNullOrEmpty() },
            condition = { it },
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
        const val USER_ID = "user-1"
        const val PROPERTY_KEY = "perm"
        const val PROPERTY_VALUE = "value"
        const val NETWORK_CAPTURE_RULE_PREFIX = "io.embrace.networkcapturerule"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
