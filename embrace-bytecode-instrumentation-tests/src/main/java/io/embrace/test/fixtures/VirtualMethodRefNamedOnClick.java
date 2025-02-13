package io.embrace.test.fixtures;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Objects;

/**
 * A custom object which implements OnClickListener via a _virtual_ method reference
 * that is called onClick
 */
public class VirtualMethodRefNamedOnClick extends Fragment {

    private void onClick(View lambdaView) {
        Log.d("Embrace", "test");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        Objects.requireNonNull(view).setOnClickListener(this::onClick);
        return view;
    }
}
