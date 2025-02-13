package io.embrace.test.fixtures

import android.view.View

/**
 * A custom object which implements OnClickListener.
 */
open class CustomOnClickListener : View.OnClickListener {
    override fun onClick(view: View) {
        if (view.isActivated) {
            return
        }
    }
}
