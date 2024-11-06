package io.embrace.android.exampleapp.ui.examples

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.embrace.android.embracesdk.Embrace

@Composable
fun SdkStateApiExample() {
    Text("SDK started")
    Text("${Embrace.getInstance().isStarted}")
    Spacer(Modifier.padding(4.dp))

    Text("Device ID")
    Text(Embrace.getInstance().deviceId)
    Spacer(Modifier.padding(4.dp))

    Text("Session ID")
    Text("${Embrace.getInstance().currentSessionId}")
    Spacer(Modifier.padding(4.dp))

    Text("Last run end state")
    Text("${Embrace.getInstance().lastRunEndState}")
    Spacer(Modifier.padding(4.dp))
}
