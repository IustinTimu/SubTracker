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
import com.example.proiectcmo.model.ImportReview;
import com.example.proiectcmo.model.ReviewItem;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.utils.DateUtils;
import com.example.proiectcmo.utils.MoneyUtils;
import com.example.proiectcmo.utils.UiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImportReviewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onSelectionChanged();

        void onCancelClicked(ReviewItem item);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Object> rows = new ArrayList<>();
    private final Listener listener;

    public ImportReviewAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(Context context, ImportReview review) {
        rows.clear();
        if (review != null) {
            addGroup(context, R.string.review_group_new, review.newItems);
            addGroup(context, R.string.review_group_updates, review.updates);
            addGroup(context, R.string.review_group_inactive, review.inactive);
            addGroup(context, R.string.review_group_verify, review.toVerify);
        }
        notifyDataSetChanged();
    }

    private void addGroup(Context context, int titleRes, List<ReviewItem> items) {
        if (items.isEmpty()) {
            return;
        }
        rows.add(context.getString(titleRes, items.size()));
        rows.addAll(items);
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new RecyclerView.ViewHolder(
                    inflater.inflate(R.layout.item_review_header, parent, false)) {
            };
        }
        return new ItemHolder(inflater.inflate(R.layout.item_review, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (row instanceof String) {
            ((TextView) holder.itemView).setText((String) row);
            return;
        }
        ReviewItem item = (ReviewItem) row;
        ItemHolder h = (ItemHolder) holder;
        Context context = h.itemView.getContext();
        Subscription s = item.proposed;
        boolean info = item.kind == ReviewItem.Kind.INFO;

        h.avatar.setText(UiUtils.initialOf(s.getName()));
        UiUtils.tintBackground(h.avatar, s.getCategoryEnum().getColorRes());
        h.name.setText(item.kind == ReviewItem.Kind.ADD || info
                ? displayName(context, s) : s.getName());
        h.detail.setText(detail(context, item));
        String reason = reason(context, item);
        h.reason.setText(reason);
        h.reason.setVisibility(reason == null ? View.GONE : View.VISIBLE);

        h.check.setVisibility(info ? View.GONE : View.VISIBLE);
        h.check.setChecked(item.selected);
        h.cancel.setVisibility(info ? View.VISIBLE : View.GONE);
        h.cancel.setOnClickListener(v -> listener.onCancelClicked(item));
        h.itemView.setClickable(!info);
        h.itemView.setOnClickListener(info ? null : v -> {
            item.selected = !item.selected;
            h.check.setChecked(item.selected);
            listener.onSelectionChanged();
        });
    }

    private static String displayName(Context context, Subscription s) {
        String vendor = s.getVendor();
        if (vendor == null || vendor.trim().isEmpty() || vendor.equalsIgnoreCase(s.getName())) {
            return s.getName();
        }
        return context.getString(R.string.review_vendor_name, vendor, s.getName());
    }

    private static String detail(Context context, ReviewItem item) {
        Subscription s = item.proposed;
        switch (item.kind) {
            case UPDATE:
                return context.getString(R.string.review_change,
                        pricePerCycle(context, item.previous), pricePerCycle(context, s));
            case REACTIVATE:
                return context.getString(R.string.review_reactivate);
            case DEACTIVATE:
                return context.getString(R.string.review_mark_cancelled);
            default:
                String price = pricePerCycle(context, s);
                return s.getNextPaymentDate() > 0
                        ? context.getString(R.string.review_next_payment, price,
                        DateUtils.formatShort(s.getNextPaymentDate()))
                        : price;
        }
    }

    private static String reason(Context context, ReviewItem item) {
        if (item.reason != null && !item.reason.trim().isEmpty()) {
            return context.getString(R.string.review_reason, item.reason.trim());
        }
        if (item.kind == ReviewItem.Kind.DEACTIVATE && item.proposed != null
                && item.proposed.getLastChargeDate() > 0) {
            return context.getString(R.string.review_no_payment_since,
                    DateUtils.formatShort(item.proposed.getLastChargeDate()));
        }
        return null;
    }

    private static String pricePerCycle(Context context, Subscription s) {
        String currency = s.getCurrency();
        String amount = Arrays.asList(MoneyUtils.CURRENCIES).contains(currency)
                ? MoneyUtils.format(s.getPrice(), currency)
                : MoneyUtils.formatNumber(s.getPrice()) + (currency == null ? "" : " " + currency);
        String cycle = s.getBillingCycle();
        if (Subscription.CYCLE_MONTHLY.equals(cycle)) {
            return context.getString(R.string.catalog_price_monthly, amount);
        } else if (Subscription.CYCLE_YEARLY.equals(cycle)) {
            return context.getString(R.string.catalog_price_yearly, amount);
        } else if ("WEEKLY".equals(cycle)) {
            return context.getString(R.string.review_price_weekly, amount);
        }
        return amount;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ItemHolder extends RecyclerView.ViewHolder {
        final CheckBox check;
        final TextView avatar;
        final TextView name;
        final TextView detail;
        final TextView reason;
        final View cancel;

        ItemHolder(@NonNull View itemView) {
            super(itemView);
            check = itemView.findViewById(R.id.selectedCheck);
            avatar = itemView.findViewById(R.id.avatarText);
            name = itemView.findViewById(R.id.nameText);
            detail = itemView.findViewById(R.id.detailText);
            reason = itemView.findViewById(R.id.reasonText);
            cancel = itemView.findViewById(R.id.cancelButton);
        }
    }
}
