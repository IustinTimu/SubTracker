package com.example.proiectcmo.ui.catalog;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proiectcmo.R;
import com.example.proiectcmo.data.SubscriptionCatalog;
import com.example.proiectcmo.model.CatalogItem;
import com.example.proiectcmo.ui.edit.AddEditSubscriptionActivity;
import com.example.proiectcmo.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CatalogActivity extends AppCompatActivity implements CatalogAdapter.Listener {
    private List<CatalogItem> allItems;
    private CatalogAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_catalog);
        UiUtils.applySystemBarsPadding(findViewById(R.id.main));

        ((TextView) findViewById(R.id.screenTitle)).setText(R.string.catalog_title);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        allItems = SubscriptionCatalog.getInstance(this).getItems();
        adapter = new CatalogAdapter(this);
        adapter.submit(allItems);

        RecyclerView grid = findViewById(R.id.catalogGrid);
        grid.setLayoutManager(new GridLayoutManager(this, 2));
        grid.setAdapter(adapter);

        EditText search = findViewById(R.id.searchInput);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void filter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<CatalogItem> result = new ArrayList<>();
        for (CatalogItem item : allItems) {
            if (item.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(item);
            }
        }
        adapter.submit(result);
    }

    @Override
    public void onCatalogItemSelected(@Nullable CatalogItem item) {
        startActivity(AddEditSubscriptionActivity.addIntent(this, item == null ? null : item.getName()));
        finish();
    }
}
