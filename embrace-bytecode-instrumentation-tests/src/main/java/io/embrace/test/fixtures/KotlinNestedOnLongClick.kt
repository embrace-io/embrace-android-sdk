package io.embrace.test.fixtures

import android.view.View

/**
 * Contains nested classes which implement OnLongClickListener.
 */
class KotlinNestedOnLongClick {
    inner class OnLongClickInnerListener : View.OnLongClickListener {
        override fun onLongClick(view: View?): Boolean {
            return true
        }
    }

    class OnLongClickStaticListener : View.OnLongClickListener {
        override fun onLongClick(view: View?): Boolean {
            return true
        }
    }
}
