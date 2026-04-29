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
import io.embrace.android.exampleapp.ObservedNavControllerActivity

@Composable
fun ObservedNavControllerExample() {
    val context = LocalContext.current
    Text(
        "Opens an Activity that uses `rememberObservedNavController()` (Compose Navigation 2.x). " +
            "Each navigation in the hosted NavHost is captured as a state-span transition by the SDK."
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = {
        context.startActivity(Intent(context, ObservedNavControllerActivity::class.java))
    }) {
        Text("Open rememberObservedNavController Activity")
    }
}
