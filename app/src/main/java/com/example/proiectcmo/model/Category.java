package com.example.proiectcmo.model;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.proiectcmo.R;

/**
 * Categoriile în care pot fi grupate abonamentele.
 * Fiecare categorie are un nume afișat (din strings.xml) și o culoare din paleta designului,
 * folosită la avatarul din listă și la graficul donut din Statistici.
 * În Firestore se salvează doar numele constantei (ex. "STREAMING").
 */
public enum Category {
    STREAMING(R.string.category_streaming, R.color.cat_streaming),
    MUSIC(R.string.category_music, R.color.cat_music),
    SOFTWARE(R.string.category_software, R.color.cat_software),
    SPORT(R.string.category_sport, R.color.cat_sport),
    CLOUD(R.string.category_cloud, R.color.cat_cloud),
    OTHER(R.string.category_other, R.color.cat_other);

    @StringRes private final int labelRes;
    @ColorRes private final int colorRes;

    Category(@StringRes int labelRes, @ColorRes int colorRes) {
        this.labelRes = labelRes;
        this.colorRes = colorRes;
    }

    @StringRes
    public int getLabelRes() { return labelRes; }

    @ColorRes
    public int getColorRes() { return colorRes; }

    /**
     * Transformă textul salvat în Firestore înapoi în categorie.
     * Pentru valori necunoscute sau lipsă întoarce {@link #OTHER}, ca aplicația să nu se blocheze.
     */
    @NonNull
    public static Category fromName(String name) {
        if (name != null) {
            for (Category category : values()) {
                if (category.name().equals(name)) {
                    return category;
                }
            }
        }
        return OTHER;
    }
}
