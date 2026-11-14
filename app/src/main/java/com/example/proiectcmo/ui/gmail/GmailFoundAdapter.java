package com.example.proiectcmo.ui.gmail;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proiectcmo.R;
import com.example.proiectcmo.model.DetectedSubscription;
import com.example.proiectcmo.utils.DateUtils;
import com.example.proiectcmo.utils.MoneyUtils;
import com.example.proiectcmo.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/** Lista „Abonamente găsite”: fiecare rând poate fi bifat sau debifat. */
public class GmailFoundAdapter extends RecyclerView.Adapter<GmailFoundAdapter.ViewHolder> {

    /** Anunță ecranul că s-a schimbat o bifă, ca să actualizeze butonul de import. */
    public interface Listener {
        void onSelectionChanged();
    }

    private final List<DetectedSubscription> items = new ArrayList<>();
    private final Listener listener;

    public GmailFoundAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<DetectedSubscription> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gmail_found, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DetectedSubscription item = items.get(position);
        Context context = holder.itemView.getContext();

        holder.check.setChecked(item.isSelected());
        holder.avatar.setText(UiUtils.initialOf(item.getName()));
        UiUtils.tintBackground(holder.avatar, item.getCategory().getColorRes());
        holder.name.setText(item.getName());
        holder.meta.setText(context.getString(R.string.gmail_item_meta,
                context.getString(item.getCategory().getLabelRes()),
                DateUtils.formatShort(item.getLastEmailDate())));
        holder.price.setText(MoneyUtils.format(item.getPrice(), item.getCurrency()));

        holder.itemView.setOnClickListener(v -> {
            item.setSelected(!item.isSelected());
            holder.check.setChecked(item.isSelected());
            listener.onSelectionChanged();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox check;
        final TextView avatar;
        final TextView name;
        final TextView meta;
        final TextView price;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            check = itemView.findViewById(R.id.selectedCheck);
            avatar = itemView.findViewById(R.id.avatarText);
            name = itemView.findViewById(R.id.nameText);
            meta = itemView.findViewById(R.id.metaText);
            price = itemView.findViewById(R.id.priceText);
        }
    }
}
