@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal

import android.app.FragmentManager
import android.content.Context
import android.view.Window
import androidx.appcompat.app.AppCompatActivity

/**
 * Used to Mock an Activity instance, which will return the Mocked FragmentManager
 */
public class MockActivity(private val context: EmbraceContext) : AppCompatActivity() {
    private val fragmentManager = MockFragmentManager(this)
    private val mockView = MockView(context)
    public fun setContext(context: Context) {
        this.attachBaseContext(context)
    }

    @Deprecated("Deprecated in Java")
    override fun getFragmentManager(): FragmentManager {
        return fragmentManager
    }

    override fun getLocalClassName(): String {
        return "io.embrace.android.embracesdk.TestActivity"
    }

    override fun getWindow(): Window {
        return MockWindow(context, mockView)
    }
}
