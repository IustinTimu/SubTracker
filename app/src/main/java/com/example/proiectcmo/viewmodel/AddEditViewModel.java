package com.example.proiectcmo.viewmodel;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proiectcmo.R;
import com.example.proiectcmo.model.CatalogItem;
import com.example.proiectcmo.model.Category;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.repository.OperationCallback;
import com.example.proiectcmo.repository.SubscriptionRepository;
import com.example.proiectcmo.utils.DateUtils;
import com.example.proiectcmo.utils.Event;
import com.example.proiectcmo.utils.MoneyUtils;
import com.example.proiectcmo.utils.ValidationUtils;

import java.util.Calendar;

public class AddEditViewModel extends ViewModel {
    private final SubscriptionRepository repository = new SubscriptionRepository();

    private final MutableLiveData<Event<Subscription>> initial = new MutableLiveData<>();

    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>();
    private final MutableLiveData<SubscriptionFormState> formState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Boolean>> saved = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> errors = new MutableLiveData<>();

    @Nullable private String editingId;

    @Nullable private Subscription original;

    private String newSource = Subscription.SOURCE_MANUAL;
    private boolean initialized;

    public LiveData<Event<Subscription>> getInitial() { return initial; }

    public LiveData<Long> getSelectedDate() { return selectedDate; }

    public LiveData<SubscriptionFormState> getFormState() { return formState; }

    public LiveData<Boolean> getSaving() { return saving; }

    public LiveData<Event<Boolean>> getSaved() { return saved; }

    public LiveData<Event<Integer>> getErrors() { return errors; }

    public boolean isEditing() { return editingId != null; }

    public void init(@Nullable String subscriptionId, @Nullable CatalogItem catalogItem) {
        if (initialized) {
            return;
        }
        initialized = true;
        editingId = subscriptionId;

        if (subscriptionId != null) {
            repository.getById(subscriptionId, subscription -> {
                if (subscription == null) {
                    errors.setValue(new Event<>(R.string.error_generic));
                    return;
                }
                original = subscription;
                selectedDate.setValue(subscription.getUpcomingPaymentDate());
                initial.setValue(new Event<>(subscription));
            });
        } else if (catalogItem != null) {
            Subscription prefilled = new Subscription(catalogItem.getName(), catalogItem.getPrice(),
                    catalogItem.getCurrency(), catalogItem.getBillingCycle(),
                    defaultNextPayment(catalogItem.getBillingCycle()),
                    catalogItem.getCategory().name());
            prefilled.setCancelUrl(catalogItem.getCancelUrl());
            newSource = Subscription.SOURCE_CATALOG;
            selectedDate.setValue(prefilled.getNextPaymentDate());
            initial.setValue(new Event<>(prefilled));
        }
    }

    public void setSelectedDate(long millis) {
        selectedDate.setValue(millis);
    }

    public void save(String name, String priceText, String currency, boolean yearly,
                     Category category, boolean notify, String cancelUrl) {
        name = name.trim();
        double price = MoneyUtils.parse(priceText);
        Long date = selectedDate.getValue();

        SubscriptionFormState form = new SubscriptionFormState(
                ValidationUtils.isNotBlank(name) ? null : R.string.error_subscription_name,
                price > 0 ? null : R.string.error_price,
                date != null ? null : R.string.error_date);
        formState.setValue(form);
        if (!form.isValid()) {
            return;
        }

        Subscription subscription = new Subscription(name, price, currency,
                yearly ? Subscription.CYCLE_YEARLY : Subscription.CYCLE_MONTHLY,
                date, category.name());
        subscription.setId(editingId);
        subscription.setNotifyBeforePayment(notify);
        subscription.setCancelUrl(cancelUrl.trim().isEmpty() ? null : cancelUrl.trim());

        subscription.setSource(original != null ? original.getSource() : newSource);
        subscription.setCancelled(original != null && original.isCancelled());

        saving.setValue(true);
        repository.save(subscription, new OperationCallback() {
            @Override
            public void onSuccess() {
                saving.setValue(false);
                saved.setValue(new Event<>(true));
            }

            @Override
            public void onError(int messageRes) {
                saving.setValue(false);
                errors.setValue(new Event<>(messageRes));
            }
        });
    }

    @Nullable
    public Subscription getOriginal() {
        return original;
    }

    public void setCancelled(boolean cancelled) {
        if (original == null) {
            return;
        }
        saving.setValue(true);
        repository.setCancelled(original, cancelled, new OperationCallback() {
            @Override
            public void onSuccess() {
                saving.setValue(false);
                saved.setValue(new Event<>(true));
            }

            @Override
            public void onError(int messageRes) {
                saving.setValue(false);
                errors.setValue(new Event<>(messageRes));
            }
        });
    }

    private static long defaultNextPayment(String cycle) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(DateUtils.startOfDay(System.currentTimeMillis()));
        calendar.add(Subscription.CYCLE_YEARLY.equals(cycle) ? Calendar.YEAR : Calendar.MONTH, 1);
        return calendar.getTimeInMillis();
    }
}
