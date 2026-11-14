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
                if (layout.getError() != null) {
                    layout.setError(null);
                    // eliberează și spațiul rezervat mesajului (setError îl reactivează la nevoie)
                    layout.setErrorEnabled(false);
                }
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

    /**
     * Afișează un text de forma „Nu ai cont? Înregistrează-te”, unde a doua parte
     * apare îngroșată și în culoarea principală, ca un link.
     */
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

    /**
     * Alege forma de plural după regulile limbii române („2 zile”, dar „20 de zile”),
     * chiar dacă telefonul este setat pe altă limbă. Fără asta, pe un telefon în engleză
     * Android ar aplica regulile englezei și ar afișa „6 de zile”.
     */
    public static String quantityString(Context context, @PluralsRes int pluralRes,
                                        int count, Object... args) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(new Locale("ro"));
        return context.createConfigurationContext(config).getResources()
                .getQuantityString(pluralRes, count, args);
    }

    /** Convertește dp în pixeli, pentru dimensiuni setate din cod. */
    public static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /** Prima literă a numelui, pentru avatarul colorat (ex. „Netflix” → „N”). */
    public static String initialOf(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        return name.trim().substring(0, 1).toUpperCase();
    }

    /** Colorează fundalul unui view (avatar, badge) păstrând forma din drawable. */
    public static void tintBackground(View view, @ColorRes int colorRes) {
        ViewCompat.setBackgroundTintList(view,
                ColorStateList.valueOf(ContextCompat.getColor(view.getContext(), colorRes)));
    }
}
