package io.embrace.test.fixtures

import android.view.View

/**
 * Contains nested classes which implement OnClickListener.
 */
class KotlinNested {
    inner class KotlinInnerListener : View.OnClickListener {
        override fun onClick(view: View) {}
    }

    class KotlinStaticListener : View.OnClickListener {
        override fun onClick(view: View) {}
    }
}
