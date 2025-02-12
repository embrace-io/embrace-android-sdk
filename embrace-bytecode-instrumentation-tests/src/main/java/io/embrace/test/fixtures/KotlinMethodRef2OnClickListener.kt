package io.embrace.test.fixtures

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * A Kotlin class which implements an OnClickListener as an object.
 */
class KotlinMethodRef2OnClickListener : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = checkNotNull(super.onCreateView(inflater, container, savedInstanceState))
        view.setOnClickListener(this::performFoo)
        return view
    }

    @Suppress("UNUSED_PARAMETER")
    public fun performFoo(v: View) {
        Log.d("Embrace", "test")
    }
}
