package io.embrace.test.fixtures

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * A Kotlin class which implements an OnClickListener as an object.
 */
@Suppress("ObjectLiteralToLambda")
class KotlinObjectOnClickListener : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = checkNotNull(super.onCreateView(inflater, container, savedInstanceState))
        view.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View?) {
            }
        })
        return view
    }
}
