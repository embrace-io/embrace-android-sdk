package io.embrace.android.exampleapp

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class PlaceholderFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val label =
            "Activity hashcode: ${this@PlaceholderFragment.activity.hashCode()}\nFragment hashcode: ${this@PlaceholderFragment.hashCode()}"
        return TextView(requireContext()).apply {
            text = label
            textSize = 24f
            gravity = Gravity.CENTER
        }
    }
}
