package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageInfo
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.APP_IMAGE_AT_INIT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.ART_COMPILER_FILTER
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.ART_COMPILER_FILTER_NOT_FOUND
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.LOW_MEMORY
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.MEM_AVAILABLE_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.NUMERIC_ERROR
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.PREFS_FILE_BYTES
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.SECONDS_SINCE_BOOT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.SECONDS_SINCE_INSTALL
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.SECONDS_SINCE_UPDATE
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.STRING_ERROR
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_HEADROOM_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_HEADROOM_PCT_MIN_API
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_HEADROOM_PCT_UNAVAILABLE
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_STATUS
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_STATUS_MIN_API
import io.embrace.android.embracesdk.internal.instrumentation.startup.shadows.EmbraceShadowPowerManager
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

@RunWith(AndroidJUnit4::class)
@Config(shadows = [EmbraceShadowPowerManager::class])
internal class SdkInitEnvironmentAttributesTest {

    private lateinit var context: Context
    private lateinit var logger: FakeInternalLogger
    private lateinit var powerManager: PowerManager
    private lateinit var shadowPowerManager: EmbraceShadowPowerManager
    private var uptimeMs: Long = 45_000L
    private var prefsFileBytes: Long? = 63_070L
    private var artOptimizationState: ArtOptimizationState? =
        ArtOptimizationState(artCompilerFilter = "speed-profile", hasAppImage = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        logger = FakeInternalLogger(throwOnInternalError = false)
        powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowPowerManager = Shadow.extract(powerManager)
        shadowPowerManager.thermalStatus = PowerManager.THERMAL_STATUS_MODERATE
        shadowPowerManager.thermalHeadroom = 0.5f
        setMemoryInfo(availMem = 1_000_000_000L, totalMem = 4_000_000_000L, lowMemory = true)
        setPackageTimestamps(firstInstallTime = NOW_MS - 90_000L, lastUpdateTime = NOW_MS - 30_000L)
    }

    @Test
    fun `a device that answers everything records every attribute and reports nothing`() {
        buildEnvironmentAttributes().assertDefaults()
        assertNoInternalErrors()
    }

    @Test
    fun `a device that answers nothing records an error for every attribute it should have had`() {
        val attributes = buildEnvironmentAttributes(
            powerManagerProvider = { error("no power manager") },
            activityManagerProvider = { error("no activity manager") },
            packageInfo = null,
            uptimeProvider = { error("no clock") },
            prefsFileSizeProvider = { error("no prefs") },
            artOptimizationProvider = { error("no odex") },
        )
        assertEquals(
            mapOf(
                THERMAL_STATUS to STRING_ERROR,
                THERMAL_HEADROOM_PCT to NUMERIC_ERROR,
                SECONDS_SINCE_INSTALL to NUMERIC_ERROR,
                SECONDS_SINCE_UPDATE to NUMERIC_ERROR,
                MEM_AVAILABLE_PCT to NUMERIC_ERROR,
                SECONDS_SINCE_BOOT to NUMERIC_ERROR,
                PREFS_FILE_BYTES to NUMERIC_ERROR,
                ART_COMPILER_FILTER to STRING_ERROR,
            ),
            attributes,
        )
        // the two presence-only attributes have no value to carry, so they stay out
        attributes.assertAbsent(LOW_MEMORY)
        attributes.assertAbsent(APP_IMAGE_AT_INIT)
        assertCaptureFailureReported(
            THERMAL_STATUS,
            SECONDS_SINCE_INSTALL,
            MEM_AVAILABLE_PCT,
            SECONDS_SINCE_BOOT,
            PREFS_FILE_BYTES,
            ART_COMPILER_FILTER,
        )
    }

    @Test
    fun `thermal status is mapped to its name`() {
        shadowPowerManager.thermalStatus = PowerManager.THERMAL_STATUS_SEVERE
        val attributes = buildEnvironmentAttributes()
        attributes.assertAttribute(THERMAL_STATUS, "severe")
        attributes.assertDefaultsExcept(THERMAL_STATUS)
        assertNoInternalErrors()
    }

    @Test
    fun `a thermal status the platform adds later is recorded as its raw value`() {
        shadowPowerManager.thermalStatus = 99
        val attributes = buildEnvironmentAttributes()
        attributes.assertAttribute(THERMAL_STATUS, "99")
        attributes.assertDefaultsExcept(THERMAL_STATUS)
        assertNoInternalErrors()
    }

    @Test
    fun `thermal status none is omitted since it carries no signal`() {
        shadowPowerManager.thermalStatus = PowerManager.THERMAL_STATUS_NONE
        val attributes = buildEnvironmentAttributes()
        attributes.assertAbsent(THERMAL_STATUS)
        attributes.assertDefaultsExcept(THERMAL_STATUS)
        assertNoInternalErrors()
    }

    @Test
    fun `thermal attributes are omitted below the version that introduced the status`() {
        val attributes = buildEnvironmentAttributes(
            powerManagerProvider = { error("should not even be asked for") },
            versionChecker = { min -> min < THERMAL_STATUS_MIN_API },
        )
        attributes.assertAbsent(THERMAL_STATUS)
        attributes.assertAbsent(THERMAL_HEADROOM_PCT)
        attributes.assertDefaultsExcept(THERMAL_STATUS, THERMAL_HEADROOM_PCT)
        assertNoInternalErrors()
    }

    @Test
    fun `only the headroom is omitted below the version that introduced it`() {
        val attributes = buildEnvironmentAttributes(versionChecker = { min -> min < THERMAL_HEADROOM_PCT_MIN_API })
        attributes.assertAbsent(THERMAL_HEADROOM_PCT)
        attributes.assertDefaultsExcept(THERMAL_HEADROOM_PCT)
        assertNoInternalErrors()
    }

    @Test
    fun `a forecast the platform cannot give records no data rather than an error`() {
        // covers both a device that never forecasts and one with nothing to give right now, which are the same to us
        listOf(Float.NaN, Float.POSITIVE_INFINITY).forEach { noForecast ->
            shadowPowerManager.thermalHeadroom = noForecast
            val attributes = buildEnvironmentAttributes()
            attributes.assertAttribute(THERMAL_HEADROOM_PCT, THERMAL_HEADROOM_PCT_UNAVAILABLE)
            attributes.assertDefaultsExcept(THERMAL_HEADROOM_PCT)
            assertNoInternalErrors()
        }
    }

    @Test
    fun `a forecast outside the calibrated range is coerced into it`() {
        listOf(2.5f to "100", 1f to "100", 0f to "0", -1f to "0").forEach { (headroom, expected) ->
            shadowPowerManager.thermalHeadroom = headroom
            val attributes = buildEnvironmentAttributes()
            attributes.assertAttribute(THERMAL_HEADROOM_PCT, expected)
            attributes.assertDefaultsExcept(THERMAL_HEADROOM_PCT)
            assertNoInternalErrors()
        }
    }

    @Test
    fun `thermal reads that fail on a supporting version are errors`() {
        shadowPowerManager.thermalStatus = null
        shadowPowerManager.thermalHeadroom = null
        val attributes = buildEnvironmentAttributes()
        attributes.assertStringError(THERMAL_STATUS)
        attributes.assertNumberError(THERMAL_HEADROOM_PCT)
        attributes.assertDefaultsExcept(THERMAL_STATUS, THERMAL_HEADROOM_PCT)
        assertCaptureFailureReported(THERMAL_STATUS, THERMAL_HEADROOM_PCT)
    }

    @Test
    fun `a missing PowerManager on a supporting version fails both thermal attributes at once`() {
        val attributes = buildEnvironmentAttributes(powerManagerProvider = { null })
        attributes.assertStringError(THERMAL_STATUS)
        attributes.assertNumberError(THERMAL_HEADROOM_PCT)
        attributes.assertDefaultsExcept(THERMAL_STATUS, THERMAL_HEADROOM_PCT)
        assertCaptureFailureReported(THERMAL_STATUS)
    }

    @Test
    fun `unusable package timestamps are an error rather than a device without the data`() {
        setPackageTimestamps(firstInstallTime = 0L, lastUpdateTime = 0L)
        val attributes = buildEnvironmentAttributes()
        attributes.assertNumberError(SECONDS_SINCE_INSTALL)
        attributes.assertNumberError(SECONDS_SINCE_UPDATE)
        attributes.assertDefaultsExcept(SECONDS_SINCE_INSTALL, SECONDS_SINCE_UPDATE)
        assertCaptureFailureReported(SECONDS_SINCE_INSTALL, SECONDS_SINCE_UPDATE)
    }

    @Test
    fun `a package timestamp in the future fails only that attribute`() {
        setPackageTimestamps(firstInstallTime = NOW_MS + 5_000L, lastUpdateTime = NOW_MS - 30_000L)
        val attributes = buildEnvironmentAttributes()
        attributes.assertNumberError(SECONDS_SINCE_INSTALL)
        attributes.assertDefaultsExcept(SECONDS_SINCE_INSTALL)
        assertCaptureFailureReported(SECONDS_SINCE_INSTALL)
    }

    @Test
    fun `an app installed in the same second reports zero rather than nothing`() {
        setPackageTimestamps(firstInstallTime = NOW_MS, lastUpdateTime = NOW_MS)
        val attributes = buildEnvironmentAttributes()
        attributes.assertAttribute(SECONDS_SINCE_INSTALL, "0")
        attributes.assertAttribute(SECONDS_SINCE_UPDATE, "0")
        attributes.assertDefaultsExcept(SECONDS_SINCE_INSTALL, SECONDS_SINCE_UPDATE)
        assertNoInternalErrors()
    }

    @Test
    fun `no package info at all fails both recency attributes but is reported once`() {
        val attributes = buildEnvironmentAttributes(packageInfo = null)
        attributes.assertNumberError(SECONDS_SINCE_INSTALL)
        attributes.assertNumberError(SECONDS_SINCE_UPDATE)
        attributes.assertDefaultsExcept(SECONDS_SINCE_INSTALL, SECONDS_SINCE_UPDATE)
        assertCaptureFailureReported(SECONDS_SINCE_INSTALL)
    }

    @Test
    fun `memory availability reported as a whole percentage of total`() {
        setMemoryInfo(availMem = 1_500_000_000L, totalMem = 4_000_000_000L, lowMemory = false)
        val attributes = buildEnvironmentAttributes()
        // 37.5% rounds to 38
        attributes.assertAttribute(MEM_AVAILABLE_PCT, "38")
        // the low memory flag is present only when the system reports it
        attributes.assertAbsent(LOW_MEMORY)
        attributes.assertDefaultsExcept(MEM_AVAILABLE_PCT, LOW_MEMORY)
        assertNoInternalErrors()
    }

    @Test
    fun `a missing ActivityManager results in an error`() {
        val attributes = buildEnvironmentAttributes(activityManagerProvider = { null })
        attributes.assertNumberError(MEM_AVAILABLE_PCT)
        attributes.assertAbsent(LOW_MEMORY)
        attributes.assertDefaultsExcept(MEM_AVAILABLE_PCT, LOW_MEMORY)
        assertCaptureFailureReported(MEM_AVAILABLE_PCT)
    }

    @Test
    fun `a memory read that throws is an error`() {
        val attributes = buildEnvironmentAttributes(activityManagerProvider = { error("binder died") })
        attributes.assertNumberError(MEM_AVAILABLE_PCT)
        attributes.assertAbsent(LOW_MEMORY)
        attributes.assertDefaultsExcept(MEM_AVAILABLE_PCT, LOW_MEMORY)
        assertCaptureFailureReported(MEM_AVAILABLE_PCT)
    }

    @Test
    fun `a total memory of zero is an error rather than a device with no RAM`() {
        setMemoryInfo(availMem = 0L, totalMem = 0L, lowMemory = false)
        val attributes = buildEnvironmentAttributes()
        attributes.assertNumberError(MEM_AVAILABLE_PCT)
        attributes.assertAbsent(LOW_MEMORY)
        attributes.assertDefaultsExcept(MEM_AVAILABLE_PCT, LOW_MEMORY)
        assertCaptureFailureReported(MEM_AVAILABLE_PCT)
    }

    @Test
    fun `seconds since boot reported from awake time, not wall time since boot`() {
        uptimeMs = 90_500L
        val attributes = buildEnvironmentAttributes()
        attributes.assertAttribute(SECONDS_SINCE_BOOT, "90")
        attributes.assertDefaultsExcept(SECONDS_SINCE_BOOT)
        assertNoInternalErrors()
    }

    @Test
    fun `an app launched during boot reports zero rather than nothing`() {
        uptimeMs = 0L
        val attributes = buildEnvironmentAttributes()
        attributes.assertAttribute(SECONDS_SINCE_BOOT, "0")
        attributes.assertDefaultsExcept(SECONDS_SINCE_BOOT)
        assertNoInternalErrors()
    }

    @Test
    fun `an uptime read that throws is an error since every version has the clock`() {
        val attributes = buildEnvironmentAttributes(uptimeProvider = { error("no clock") })
        attributes.assertNumberError(SECONDS_SINCE_BOOT)
        attributes.assertDefaultsExcept(SECONDS_SINCE_BOOT)
        assertCaptureFailureReported(SECONDS_SINCE_BOOT)
    }

    @Test
    fun `an uptime that came back negative is an error`() {
        uptimeMs = -1L
        val attributes = buildEnvironmentAttributes()
        attributes.assertNumberError(SECONDS_SINCE_BOOT)
        attributes.assertDefaultsExcept(SECONDS_SINCE_BOOT)
        assertCaptureFailureReported(SECONDS_SINCE_BOOT)
    }

    @Test
    fun `no prefs file yet is the zero bytes it will load, not missing data`() {
        listOf(null, 0L, -1L).forEach { bytes ->
            prefsFileBytes = bytes
            val attributes = buildEnvironmentAttributes()
            attributes.assertAttribute(PREFS_FILE_BYTES, "0")
            attributes.assertDefaultsExcept(PREFS_FILE_BYTES)
            assertNoInternalErrors()
        }
    }

    @Test
    fun `a failing prefs size read costs only that attribute, never the whole map`() {
        val attributes = buildEnvironmentAttributes(prefsFileSizeProvider = { error("stat failed") })
        attributes.assertNumberError(PREFS_FILE_BYTES)
        attributes.assertDefaultsExcept(PREFS_FILE_BYTES)
        assertCaptureFailureReported(PREFS_FILE_BYTES)
    }

    @Test
    fun `an app compiled without an app image records that rather than omitting it`() {
        artOptimizationState = ArtOptimizationState(artCompilerFilter = "verify", hasAppImage = false)
        val attributes = buildEnvironmentAttributes()
        attributes.assertAttribute(ART_COMPILER_FILTER, "verify")
        attributes.assertAttribute(APP_IMAGE_AT_INIT, "false")
        attributes.assertDefaultsExcept(ART_COMPILER_FILTER, APP_IMAGE_AT_INIT)
        assertNoInternalErrors()
    }

    @Test
    fun `the values the odex read arrives at are passed through as they are`() {
        // both of these are decided by whoever reads the odex, including reporting the failure behind the error one
        listOf(ART_COMPILER_FILTER_NOT_FOUND, STRING_ERROR).forEach { filter ->
            artOptimizationState = ArtOptimizationState(artCompilerFilter = filter, hasAppImage = true)
            val attributes = buildEnvironmentAttributes()
            attributes.assertAttribute(ART_COMPILER_FILTER, filter)
            attributes.assertAttribute(APP_IMAGE_AT_INIT, "true")
            attributes.assertDefaultsExcept(ART_COMPILER_FILTER)
            assertNoInternalErrors()
        }
    }

    @Test
    fun `nowhere to look for the odex omits both attributes without reporting`() {
        artOptimizationState = null
        val attributes = buildEnvironmentAttributes()
        attributes.assertAbsent(ART_COMPILER_FILTER)
        attributes.assertAbsent(APP_IMAGE_AT_INIT)
        attributes.assertDefaultsExcept(ART_COMPILER_FILTER, APP_IMAGE_AT_INIT)
        assertNoInternalErrors()
    }

    @Test
    fun `an ART state read that blows up is an error and costs the app image reading`() {
        val attributes = buildEnvironmentAttributes(artOptimizationProvider = { error("bleep bloop oh noes") })
        attributes.assertStringError(ART_COMPILER_FILTER)
        attributes.assertAbsent(APP_IMAGE_AT_INIT)
        attributes.assertDefaultsExcept(ART_COMPILER_FILTER, APP_IMAGE_AT_INIT)
        assertCaptureFailureReported(ART_COMPILER_FILTER)
    }

    private fun buildEnvironmentAttributes(
        powerManagerProvider: () -> PowerManager? = { powerManager },
        activityManagerProvider: () -> ActivityManager? = {
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        },
        versionChecker: VersionChecker = VersionChecker { true },
        packageInfo: PackageInfo? = context.packageManager.getPackageInfo(context.packageName, 0),
        uptimeProvider: () -> Long = { uptimeMs },
        prefsFileSizeProvider: () -> Long? = { prefsFileBytes },
        artOptimizationProvider: () -> ArtOptimizationState? = { artOptimizationState },
    ): Map<String, String> = sdkInitEnvironmentAttributes(
        nowMs = NOW_MS,
        logger = logger,
        packageInfo = packageInfo,
        powerManagerProvider = powerManagerProvider,
        activityManagerProvider = activityManagerProvider,
        versionChecker = versionChecker,
        uptimeMs = uptimeProvider,
        prefsFileSizeProvider = prefsFileSizeProvider,
        artOptimizationProvider = artOptimizationProvider,
    )

    private fun setPackageTimestamps(firstInstallTime: Long, lastUpdateTime: Long) {
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).apply {
            this.firstInstallTime = firstInstallTime
            this.lastUpdateTime = lastUpdateTime
        }
    }

    private fun setMemoryInfo(availMem: Long, totalMem: Long, lowMemory: Boolean) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(activityManager).setMemoryInfo(
            ActivityManager.MemoryInfo().apply {
                this.availMem = availMem
                this.totalMem = totalMem
                this.lowMemory = lowMemory
            },
        )
    }

    /**
     * Asserts that every attribute carries the value the defaults set up in [setUp] produce, so that a test which
     * breaks one input has to say so for every attribute it affects and no others.
     */
    private fun Map<String, String>.assertDefaults() = assertDefaultsExcept()

    private fun Map<String, String>.assertDefaultsExcept(vararg exceptions: String) {
        assertEquals(
            DEFAULT_ATTRIBUTES.filterKeys { it !in exceptions },
            filterKeys { it !in exceptions },
        )
    }

    private fun Map<String, String>.assertAttribute(key: String, value: String) {
        assertEquals(value, this[key])
    }

    private fun Map<String, String>.assertNumberError(key: String) = assertAttribute(key, NUMERIC_ERROR)

    private fun Map<String, String>.assertStringError(key: String) = assertAttribute(key, STRING_ERROR)

    private fun Map<String, String>.assertAbsent(key: String) {
        assertFalse(containsKey(key))
    }

    /**
     * Asserts an attribute capture failure occurred for exactly the given attributes, in order.
     */
    private fun assertCaptureFailureReported(vararg keys: String) {
        assertEquals(keys.size, logger.internalErrorMessages.size)
        assertTrue(logger.internalErrorMessages.all { it.msg == "SdkInitAttributeCaptureFail" })
        if (keys.isNotEmpty()) {
            assertEquals(
                keys.toList(),
                logger.internalErrorMessages.map { it.throwable?.message?.substringAfterLast(": ") },
            )
        }
    }

    private fun assertNoInternalErrors() {
        assertEquals(0, logger.internalErrorMessages.size)
    }

    private companion object {
        const val NOW_MS = 1_700_000_000_000L

        /**
         * What every attribute comes out as for the defaults set up in [setUp].
         */
        val DEFAULT_ATTRIBUTES = mapOf(
            THERMAL_STATUS to "moderate",
            THERMAL_HEADROOM_PCT to "50",
            SECONDS_SINCE_INSTALL to "90",
            SECONDS_SINCE_UPDATE to "30",
            MEM_AVAILABLE_PCT to "25",
            LOW_MEMORY to "true",
            SECONDS_SINCE_BOOT to "45",
            PREFS_FILE_BYTES to "63070",
            ART_COMPILER_FILTER to "speed-profile",
            APP_IMAGE_AT_INIT to "true",
        )
    }
}
