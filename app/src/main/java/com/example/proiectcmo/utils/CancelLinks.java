package com.example.proiectcmo.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.example.proiectcmo.data.SubscriptionCatalog;
import com.example.proiectcmo.model.CatalogItem;
import com.example.proiectcmo.model.Subscription;

/**
 * Găsește și deschide pagina de anulare a unui abonament.
 *
 * Aplicația nu poate anula singură un abonament: niciun serviciu nu oferă acest lucru
 * printr-un API public. De aceea deschidem pagina oficială, unde utilizatorul anulează
 * din contul lui.
 */
public final class CancelLinks {

    private CancelLinks() {
    }

    /**
     * @return link-ul salvat pe abonament sau, dacă lipsește (abonamente adăugate înainte
     * de această funcție), cel din catalog după nume. Null dacă nu se cunoaște niciunul.
     */
    @Nullable
    public static String resolve(Context context, Subscription subscription) {
        if (subscription.getCancelUrl() != null && !subscription.getCancelUrl().trim().isEmpty()) {
            return subscription.getCancelUrl().trim();
        }
        CatalogItem item = SubscriptionCatalog.getInstance(context).findByName(subscription.getName());
        return item == null || item.getCancelUrl().isEmpty() ? null : item.getCancelUrl();
    }

    /** Intentul care deschide link-ul în browser. */
    public static Intent browserIntent(String url) {
        String fixed = url.startsWith("http://") || url.startsWith("https://") ? url : "https://" + url;
        return new Intent(Intent.ACTION_VIEW, Uri.parse(fixed));
    }

    /** @return true dacă link-ul a fost deschis, false dacă nu există niciun browser. */
    public static boolean open(Context context, String url) {
        try {
            context.startActivity(browserIntent(url));
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
