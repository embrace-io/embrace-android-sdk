package io.embrace.android.embracesdk.fakes

import android.view.View
import androidx.navigation.NavController
import androidx.navigation.findNavController

class ViewFindNavControllerActivity : NonAutoDetectedNavHostActivity() {
    override fun getNavController(): NavController = findViewById<View>(navHostContainerId).findNavController()
}
