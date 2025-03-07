package com.example.app

import android.view.View

/**
 * A custom object which implements OnClickListener.
 */
class OnClickListenerFixture : View.OnClickListener {
    override fun onClick(view: View) {
        if (view.isActivated) {
            return
        }
    }
}
