package io.embrace.android.embracesdk.internal.instrumentation.aei

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30], shadows = [PackageCapturingShadowActivityManager::class])
internal class ActivityManagerAeiRecordSourceTest {

    private lateinit var context: Context
    private lateinit var activityManager: ActivityManager
    private lateinit var shadowActivityManager: PackageCapturingShadowActivityManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowActivityManager = Shadow.extract(activityManager)
    }

    @Test
    fun `queries by own package name`() {
        val source = ActivityManagerAeiRecordSource(activityManager, context.packageName)
        source.getHistoricalProcessExitReasons(16)
        assertEquals(listOf(context.packageName), shadowActivityManager.queriedPackageNames)
    }

    @Test
    fun `maps framework records`() {
        shadowActivityManager.addApplicationExitInfo(
            ApplicationExitInfoBuilder.newBuilder()
                .setPid(1234)
                .setReason(ApplicationExitInfo.REASON_CRASH)
                .build(),
        )
        val records = ActivityManagerAeiRecordSource(activityManager, context.packageName)
            .getHistoricalProcessExitReasons(16)
        assertEquals(1234, records.single().pid)
        assertEquals(ApplicationExitInfo.REASON_CRASH, records.single().reason)
    }
}
