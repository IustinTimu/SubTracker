package com.example.proiectcmo.utils;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputLayout;

/**
 * Funcții ajutătoare pentru interfață, folosite de mai multe activități.
 */
public final class UiUtils {

    private UiUtils() {
    }

    /**
     * Adaugă padding pentru bara de status, bara de navigare și tastatură,
     * ca interfața să nu fie acoperită când aplicația rulează edge-to-edge.
     */
    public static void applySystemBarsPadding(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    /**
     * Șterge mesajul de eroare al unui câmp imediat ce utilizatorul începe să scrie în el.
     */
    public static void clearErrorOnType(TextInputLayout layout) {
        if (layout.getEditText() == null) {
            return;
        }
        layout.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                layout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /** @return textul introdus în câmp, sau "" dacă acesta nu are EditText. */
    public static String textOf(TextInputLayout layout) {
        return layout.getEditText() == null ? "" : layout.getEditText().getText().toString();
    }
}
