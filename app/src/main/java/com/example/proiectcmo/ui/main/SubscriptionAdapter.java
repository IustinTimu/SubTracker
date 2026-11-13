package com.example.proiectcmo.ui.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proiectcmo.R;
import com.example.proiectcmo.model.Category;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.utils.DateUtils;
import com.example.proiectcmo.utils.MoneyUtils;
import com.example.proiectcmo.utils.UiUtils;

/**
 * Adapter pentru lista de abonamente de pe ecranul principal.
 *
 * ListAdapter + DiffUtil compară lista veche cu cea nouă și actualizează doar rândurile
 * modificate (cu animație), în loc să redeseneze toată lista la fiecare schimbare din Firestore.
 */
public class SubscriptionAdapter extends ListAdapter<Subscription, SubscriptionAdapter.ViewHolder> {

    /** Plățile la cel mult atâtea zile distanță sunt evidențiate cu culoarea de accent. */
    private static final int URGENT_DAYS = 3;

    /** Evenimentele de click transmise activității. */
    public interface Listener {
        /** Click simplu → deschide editarea. */
        void onSubscriptionClick(Subscription subscription);

        /** Click lung → meniul contextual, ancorat pe cardul apăsat. */
        void onSubscriptionLongClick(Subscription subscription, View anchor);
    }

    private final Listener listener;

    public SubscriptionAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subscription, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    /** Ține referințele către view-urile unui rând, ca să nu fie căutate la fiecare derulare. */
    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView avatar;
        private final TextView name;
        private final TextView subtitle;
        private final TextView price;
        private final TextView badge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatarText);
            name = itemView.findViewById(R.id.nameText);
            subtitle = itemView.findViewById(R.id.subtitleText);
            price = itemView.findViewById(R.id.priceText);
            badge = itemView.findViewById(R.id.dueBadge);
        }

        void bind(Subscription subscription) {
            Context context = itemView.getContext();
            Category category = subscription.getCategoryEnum();

            avatar.setText(UiUtils.initialOf(subscription.getName()));
            UiUtils.tintBackground(avatar, category.getColorRes());

            name.setText(subscription.getName());
            subtitle.setText(context.getString(R.string.item_subtitle,
                    context.getString(category.getLabelRes()),
                    context.getString(subscription.isYearly()
                            ? R.string.cycle_yearly : R.string.cycle_monthly)));
            price.setText(MoneyUtils.format(subscription.getPrice(), subscription.getCurrency()));

            // Badge: „mâine”, „în 3 zile”… cu accent dacă plata e aproape.
            badge.setText(DateUtils.relativeDays(context, subscription.getNextPaymentDate()));
            boolean urgent = DateUtils.daysUntil(subscription.getNextPaymentDate()) <= URGENT_DAYS;
            UiUtils.tintBackground(badge,
                    urgent ? R.color.st_accent_container : R.color.st_primary_container);
            badge.setTextColor(ContextCompat.getColor(context,
                    urgent ? R.color.st_on_accent_container : R.color.st_on_primary_container));

            itemView.setOnClickListener(v -> listener.onSubscriptionClick(subscription));
            itemView.setOnLongClickListener(v -> {
                listener.onSubscriptionLongClick(subscription, v);
                return true; // evenimentul a fost tratat → nu se mai declanșează și click-ul simplu
            });
        }
    }

    /** Spune RecyclerView-ului când două elemente reprezintă același abonament / au același conținut. */
    private static final DiffUtil.ItemCallback<Subscription> DIFF = new DiffUtil.ItemCallback<Subscription>() {
        @Override
        public boolean areItemsTheSame(@NonNull Subscription a, @NonNull Subscription b) {
            return a.getId() != null && a.getId().equals(b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Subscription a, @NonNull Subscription b) {
            return a.getName().equals(b.getName())
                    && a.getPrice() == b.getPrice()
                    && a.getNextPaymentDate() == b.getNextPaymentDate()
                    && String.valueOf(a.getCurrency()).equals(String.valueOf(b.getCurrency()))
                    && String.valueOf(a.getBillingCycle()).equals(String.valueOf(b.getBillingCycle()))
                    && a.getCategoryEnum() == b.getCategoryEnum();
        }
    };
}
