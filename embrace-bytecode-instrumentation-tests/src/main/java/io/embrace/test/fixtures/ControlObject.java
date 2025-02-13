package io.embrace.test.fixtures;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * An object which should not be affected by bytecode instrumentation.
 */
public class ControlObject extends AppCompatActivity {
    public void processView(View view) {

    }
}
