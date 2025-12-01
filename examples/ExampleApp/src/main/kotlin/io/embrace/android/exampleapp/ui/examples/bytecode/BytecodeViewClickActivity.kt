package io.embrace.android.exampleapp.ui.examples.bytecode

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import io.embrace.android.exampleapp.R

class BytecodeViewClickActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bytecode_view_click)

        findViewById<View>(R.id.btn_bytecode_view_click).setOnClickListener(::onClick)
        findViewById<View>(R.id.btn_bytecode_view_click).setOnLongClickListener(::onLongClick)
    }

    private fun onClick(v: View) {
        Log.d("BytecodeViewClickActivity", "Button clicked : ${v.id}")
    }

    private fun onLongClick(v: View): Boolean {
        Log.d("BytecodeViewClickActivity", "Button long clicked : ${v.id}")
        return false
    }
}
