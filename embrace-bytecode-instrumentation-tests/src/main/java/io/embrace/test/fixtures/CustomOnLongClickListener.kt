package io.embrace.test.fixtures

import android.view.View

/**
 * A custom object which implements OnLongClickListener.
 */
open class CustomOnLongClickListener : View.OnLongClickListener {

    override fun onLongClick(view: View?): Boolean {
        return view?.isActivated ?: false
    }
}
