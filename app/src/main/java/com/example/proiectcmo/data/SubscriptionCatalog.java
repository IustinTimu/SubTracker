package com.example.proiectcmo.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.proiectcmo.model.CatalogItem;
import com.example.proiectcmo.model.Category;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SubscriptionCatalog {
    private static final String TAG = "SubscriptionCatalog";
    private static final String FILE_NAME = "catalog.json";

    private static SubscriptionCatalog instance;

    private final List<CatalogItem> items;

    private SubscriptionCatalog(List<CatalogItem> items) {
        this.items = Collections.unmodifiableList(items);
    }

    public static synchronized SubscriptionCatalog getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new SubscriptionCatalog(load(context.getApplicationContext()));
        }
        return instance;
    }

    public List<CatalogItem> getItems() {
        return items;
    }

    @Nullable
    public CatalogItem findByName(String name) {
        for (CatalogItem item : items) {
            if (item.getName().equalsIgnoreCase(name)) {
                return item;
            }
        }
        return null;
    }

    private static List<CatalogItem> load(Context context) {
        List<CatalogItem> result = new ArrayList<>();
        try (InputStream input = context.getAssets().open(FILE_NAME)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            JSONArray array = new JSONArray(buffer.toString(StandardCharsets.UTF_8.name()));
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                List<String> domains = new ArrayList<>();
                JSONArray domainsJson = json.getJSONArray("domains");
                for (int j = 0; j < domainsJson.length(); j++) {
                    domains.add(domainsJson.getString(j));
                }
                result.add(new CatalogItem(
                        json.getString("name"),
                        json.getDouble("price"),
                        json.getString("currency"),
                        json.getString("cycle"),
                        Category.fromName(json.getString("category")),
                        domains,
                        json.optString("keyword"),
                        json.optString("cancelUrl")));
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Nu s-a putut citi catalogul", e);
        }
        return result;
    }
}
