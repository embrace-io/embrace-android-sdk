package io.embrace.android.embracesdk.fakes

import android.app.Activity
import io.embrace.android.embracesdk.annotation.StartupActivity

/**
 * Activity that will not be used in recording the startup trace
 */
@StartupActivity
class FakeSplashScreenActivity : Activity()
