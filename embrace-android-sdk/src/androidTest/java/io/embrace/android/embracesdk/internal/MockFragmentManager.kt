@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal

import android.app.Activity
import android.app.Fragment
import android.app.FragmentManager
import android.app.FragmentTransaction
import android.os.Bundle
import androidx.lifecycle.MockReportFragment
import androidx.lifecycle.ReportFragment
import java.io.FileDescriptor
import java.io.PrintWriter

/**
 * Used to mock a FragmentManager instance in order to inject a Mock ReportFragment
 */
public class MockFragmentManager(private val activity: Activity) : FragmentManager() {
    public var reportFragment: ReportFragment = MockReportFragment().apply {
        this.activity
    }

    override fun saveFragmentInstanceState(f: Fragment?): Fragment.SavedState {
        TODO("Not yet implemented")
    }

    override fun findFragmentById(id: Int): Fragment {
        TODO("Not yet implemented")
    }

    override fun getFragments(): MutableList<Fragment> {
        TODO("Not yet implemented")
    }

    override fun beginTransaction(): FragmentTransaction {
        TODO("Not yet implemented")
    }

    override fun putFragment(bundle: Bundle?, key: String?, fragment: Fragment?) {
        TODO("Not yet implemented")
    }

    override fun removeOnBackStackChangedListener(listener: OnBackStackChangedListener?) {
        TODO("Not yet implemented")
    }

    override fun getFragment(bundle: Bundle?, key: String?): Fragment {
        TODO("Not yet implemented")
    }

    override fun unregisterFragmentLifecycleCallbacks(cb: FragmentLifecycleCallbacks?) {
        TODO("Not yet implemented")
    }

    override fun getPrimaryNavigationFragment(): Fragment {
        TODO("Not yet implemented")
    }

    override fun getBackStackEntryCount(): Int {
        TODO("Not yet implemented")
    }

    override fun isDestroyed(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getBackStackEntryAt(index: Int): BackStackEntry {
        TODO("Not yet implemented")
    }

    override fun executePendingTransactions(): Boolean {
        TODO("Not yet implemented")
    }

    override fun popBackStackImmediate(): Boolean {
        TODO("Not yet implemented")
    }

    override fun popBackStackImmediate(name: String?, flags: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun popBackStackImmediate(id: Int, flags: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun findFragmentByTag(tag: String?): Fragment {
        if (tag == "androidx.lifecycle.LifecycleDispatcher.report_fragment_tag") {
            return reportFragment
        } else {
            return Fragment()
        }
    }

    override fun addOnBackStackChangedListener(listener: OnBackStackChangedListener?) {
        TODO("Not yet implemented")
    }

    override fun dump(prefix: String?, fd: FileDescriptor?, writer: PrintWriter?, args: Array<out String>?) {
        TODO("Not yet implemented")
    }

    override fun isStateSaved(): Boolean {
        TODO("Not yet implemented")
    }

    override fun popBackStack() {
        TODO("Not yet implemented")
    }

    override fun popBackStack(name: String?, flags: Int) {
        TODO("Not yet implemented")
    }

    override fun popBackStack(id: Int, flags: Int) {
        TODO("Not yet implemented")
    }

    override fun registerFragmentLifecycleCallbacks(cb: FragmentLifecycleCallbacks?, recursive: Boolean) {
        TODO("Not yet implemented")
    }
}
