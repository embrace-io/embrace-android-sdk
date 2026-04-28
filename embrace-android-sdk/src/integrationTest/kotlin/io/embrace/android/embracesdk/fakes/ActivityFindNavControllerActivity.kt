package io.embrace.android.embracesdk.fakes

import androidx.navigation.NavController
import androidx.navigation.findNavController

class ActivityFindNavControllerActivity : NonAutoDetectedNavHostActivity() {
    override fun getNavController(): NavController = findNavController(navHostContainerId)
}
