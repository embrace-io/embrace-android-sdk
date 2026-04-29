package io.embrace.android.exampleapp.ui.examples

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.ObservedBackStackActivity

@Composable
fun ObservedBackStackExample() {
    val context = LocalContext.current
    Text(
        "Opens an Activity that uses `rememberObservedBackStack()` (Navigation 3). The hosted " +
            "NavDisplay's back-stack mutations (push/pop) are captured as state-span transitions by " +
            "the SDK."
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = {
        context.startActivity(Intent(context, ObservedBackStackActivity::class.java))
    }) {
        Text("Open rememberObservedBackStack Activity")
    }
}
