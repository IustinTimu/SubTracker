package com.example.proiectcmo.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.proiectcmo.R;
import com.example.proiectcmo.data.SubscriptionCatalog;
import com.example.proiectcmo.model.CatalogItem;
import com.example.proiectcmo.model.Subscription;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class CancelLinks {
    private CancelLinks() {
    }

    @Nullable
    public static String resolve(Context context, Subscription subscription) {
        if (subscription.getCancelUrl() != null && !subscription.getCancelUrl().trim().isEmpty()) {
            return subscription.getCancelUrl().trim();
        }
        CatalogItem item = SubscriptionCatalog.getInstance(context).findByName(subscription.getName());
        return item == null || item.getCancelUrl().isEmpty() ? null : item.getCancelUrl();
    }

    public static Intent browserIntent(String url) {
        String fixed = url.startsWith("http://") || url.startsWith("https://") ? url : "https://" + url;
        return new Intent(Intent.ACTION_VIEW, Uri.parse(fixed));
    }

    public static boolean openOrExplain(Activity activity, String name, @Nullable String url) {
        if (url == null) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.cancel_help_title, name))
                    .setMessage(R.string.cancel_help_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return false;
        }
        try {
            activity.startActivity(browserIntent(url));
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.error_no_browser, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public static void askIfCancelled(Activity activity, String name, Runnable markCancelled) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.cancel_confirm_title, name))
                .setMessage(R.string.cancel_confirm_message)
                .setNegativeButton(R.string.action_not_yet, null)
                .setPositiveButton(R.string.action_mark_cancelled, (dialog, which) -> markCancelled.run())
                .show();
    }
}
