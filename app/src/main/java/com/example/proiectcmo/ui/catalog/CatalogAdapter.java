package com.example.proiectcmo.ui.catalog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proiectcmo.R;
import com.example.proiectcmo.model.CatalogItem;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.utils.MoneyUtils;
import com.example.proiectcmo.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Grila cu serviciile din catalog. Primul element este mereu „Personalizat”.
 */
public class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.ViewHolder> {

    /** @param item serviciul ales sau null pentru „Personalizat” */
    public interface Listener {
        void onCatalogItemSelected(@Nullable CatalogItem item);
    }

    private final Listener listener;
    private final List<CatalogItem> items = new ArrayList<>();

    public CatalogAdapter(Listener listener) {
        this.listener = listener;
    }

    /** Înlocuiește lista afișată (după căutare). */
    public void submit(List<CatalogItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size() + 1; // +1 pentru „Personalizat”
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_catalog, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(position == 0 ? null : items.get(position - 1));
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView avatar;
        private final TextView name;
        private final TextView price;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatarText);
            name = itemView.findViewById(R.id.nameText);
            price = itemView.findViewById(R.id.priceText);
        }

        void bind(@Nullable CatalogItem item) {
            Context context = itemView.getContext();
            if (item == null) {
                avatar.setText("+");
                UiUtils.tintBackground(avatar, R.color.st_primary);
                name.setText(R.string.catalog_custom);
                price.setText(R.string.catalog_custom_subtitle);
            } else {
                avatar.setText(UiUtils.initialOf(item.getName()));
                UiUtils.tintBackground(avatar, item.getCategory().getColorRes());
                name.setText(item.getName());
                String amount = MoneyUtils.format(item.getPrice(), item.getCurrency());
                price.setText(context.getString(isYearly(item)
                        ? R.string.catalog_price_yearly : R.string.catalog_price_monthly, amount));
            }
            itemView.setOnClickListener(v -> listener.onCatalogItemSelected(item));
        }
    }

    private static boolean isYearly(CatalogItem item) {
        return Subscription.CYCLE_YEARLY.equals(item.getBillingCycle());
    }
}
