package com.example.proiectcmo.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.proiectcmo.R;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public final class UiUtils {
    private UiUtils() {
    }

    public static void applySystemBarsPadding(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

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
                if (layout.getError() != null) {
                    layout.setError(null);

                    layout.setErrorEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public static String textOf(TextInputLayout layout) {
        return layout.getEditText() == null ? "" : layout.getEditText().getText().toString();
    }

    public static void setLinkText(TextView view, @StringRes int prefixRes, @StringRes int linkRes) {
        Context context = view.getContext();
        SpannableStringBuilder text = new SpannableStringBuilder(context.getString(prefixRes));
        text.append(' ');
        int start = text.length();
        text.append(context.getString(linkRes));
        text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.st_primary)),
                start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(text);
    }

    public static String quantityString(Context context, @PluralsRes int pluralRes,
                                        int count, Object... args) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(new Locale("ro"));
        return context.createConfigurationContext(config).getResources()
                .getQuantityString(pluralRes, count, args);
    }

    public static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    public static String initialOf(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        return name.trim().substring(0, 1).toUpperCase();
    }

    public static void tintBackground(View view, @ColorRes int colorRes) {
        ViewCompat.setBackgroundTintList(view,
                ColorStateList.valueOf(ContextCompat.getColor(view.getContext(), colorRes)));
    }
}
