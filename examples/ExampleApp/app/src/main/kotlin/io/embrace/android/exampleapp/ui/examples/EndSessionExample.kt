package io.embrace.android.exampleapp.ui.examples

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.embrace.android.embracesdk.Embrace

@Composable
fun EndSessionExample() {
    Button(onClick = {
        Embrace.getInstance().endSession()
    }) {
        Text("End Session")
    }
}
