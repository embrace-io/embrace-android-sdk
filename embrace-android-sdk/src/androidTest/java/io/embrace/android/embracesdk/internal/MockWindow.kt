package io.embrace.android.embracesdk.internal

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.InputQueue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.Window

public class MockWindow(context: Context, public val mockView: View) : Window(context) {
    override fun superDispatchTrackballEvent(event: MotionEvent?): Boolean = false

    override fun setNavigationBarColor(color: Int) {}

    override fun onConfigurationChanged(newConfig: Configuration?) {}

    override fun peekDecorView(): View? = null

    override fun setFeatureDrawableUri(featureId: Int, uri: Uri?) {}

    override fun setVolumeControlStream(streamType: Int) {}

    override fun setBackgroundDrawable(drawable: Drawable?) {}

    override fun takeKeyEvents(get: Boolean) {}

    override fun getNavigationBarColor(): Int = 0

    override fun superDispatchGenericMotionEvent(event: MotionEvent?): Boolean = false

    override fun superDispatchKeyEvent(event: KeyEvent?): Boolean = false

    override fun getLayoutInflater(): LayoutInflater = LayoutInflater.from(context)

    override fun performContextMenuIdentifierAction(id: Int, flags: Int): Boolean = false

    override fun setStatusBarColor(color: Int) {}

    override fun togglePanel(featureId: Int, event: KeyEvent?) {}

    override fun performPanelIdentifierAction(featureId: Int, id: Int, flags: Int): Boolean = false

    override fun closeAllPanels() {}

    override fun superDispatchKeyShortcutEvent(event: KeyEvent?): Boolean = false

    override fun superDispatchTouchEvent(event: MotionEvent?): Boolean = false

    override fun setDecorCaptionShade(decorCaptionShade: Int) {}

    override fun takeInputQueue(callback: InputQueue.Callback?) {}

    override fun setResizingCaptionDrawable(drawable: Drawable?) {}

    override fun performPanelShortcut(featureId: Int, keyCode: Int, event: KeyEvent?, flags: Int): Boolean = false

    override fun setFeatureDrawable(featureId: Int, drawable: Drawable?) {}

    override fun saveHierarchyState(): Bundle? = null

    override fun addContentView(view: View?, params: ViewGroup.LayoutParams?) {}

    override fun invalidatePanelMenu(featureId: Int) {}

    override fun setTitle(title: CharSequence?) {}

    override fun setChildDrawable(featureId: Int, drawable: Drawable?) {}

    override fun closePanel(featureId: Int) {}

    override fun restoreHierarchyState(savedInstanceState: Bundle?) {}

    override fun onActive() {}

    override fun getDecorView(): View = mockView

    override fun setTitleColor(textColor: Int) {}

    override fun setContentView(layoutResID: Int) {}

    override fun setContentView(view: View?) {}

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {}

    override fun getVolumeControlStream(): Int = 0

    override fun getCurrentFocus(): View? = null

    override fun getStatusBarColor(): Int = 0

    override fun isShortcutKey(keyCode: Int, event: KeyEvent?): Boolean = false

    override fun setFeatureDrawableAlpha(featureId: Int, alpha: Int) {}

    override fun isFloating(): Boolean = false

    override fun setFeatureDrawableResource(featureId: Int, resId: Int) {}

    override fun setFeatureInt(featureId: Int, value: Int) {}

    override fun setChildInt(featureId: Int, value: Int) {}

    override fun takeSurface(callback: SurfaceHolder.Callback2?) {}

    override fun openPanel(featureId: Int, event: KeyEvent?) {}
}
