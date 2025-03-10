package com.example.app

import android.view.View

/**
 * A custom object which implements OnLongClickListener.
 */
class OnLongClickListenerFixture : View.OnLongClickListener {

    override fun onLongClick(view: View?): Boolean {
        return view?.isActivated ?: false
    }
}
