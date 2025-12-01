package io.embrace.android.exampleapp.ui.examples

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.embrace.android.exampleapp.ui.RadioButtonList

private enum class AnrType {
    SLEEP,
    BUSY_LOOP
}

@Composable
fun AnrDetectionExample() {
    Text("To trigger an ANR you must tap the screen anywhere AFTER clicking the button below. Android only triggers an ANR when the main thread is blocked AND the user interacted with an app.")

    var selectedType by remember { mutableStateOf(AnrType.SLEEP) }

    RadioButtonList(AnrType.entries, selectedType) {
        selectedType = it
    }

    Button(onClick = {
        when (selectedType) {
            AnrType.SLEEP -> Thread.sleep(10000)
            AnrType.BUSY_LOOP -> blockWithBusyLoop()
        }
    }) {
        Text("Trigger ANR")
    }
}

private fun blockWithBusyLoop() {
    val start = System.currentTimeMillis()
    while (true) {
        if (System.currentTimeMillis() - start >= 10000) {
            break
        }
    }
}
