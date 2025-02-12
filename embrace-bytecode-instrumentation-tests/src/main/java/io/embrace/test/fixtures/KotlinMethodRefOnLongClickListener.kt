package io.embrace.test.fixtures

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * A Kotlin class which implements an OnLongClickListener as an object.
 */
class KotlinMethodRefOnLongClickListener : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = checkNotNull(super.onCreateView(inflater, container, savedInstanceState))
        view.setOnLongClickListener(this::performFoo)
        return view
    }

    @Suppress("UNUSED_PARAMETER")
    private fun performFoo(v: View): Boolean {
        Log.d("Embrace", "test")
        return true
    }
}
