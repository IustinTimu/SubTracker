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

/**
 * Logica ecranului „Adaugă / Editează abonament” (05).
 *
 * În modul editare încarcă abonamentul din Firestore; în modul adăugare poate porni
 * de la un serviciu din catalog. Validează câmpurile și salvează prin {@link SubscriptionRepository}.
 */
public class AddEditViewModel extends ViewModel {

    private final SubscriptionRepository repository = new SubscriptionRepository();

    /**
     * Valorile inițiale ale formularului (abonamentul editat sau precompletat din catalog).
     * Învelite în Event ca formularul să fie completat o singură dată – după rotirea ecranului
     * câmpurile își păstrează singure ce a scris utilizatorul.
     */
    private final MutableLiveData<Event<Subscription>> initial = new MutableLiveData<>();
    /** Data aleasă în calendar; păstrată aici ca să nu se piardă la rotirea ecranului. */
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>();
    private final MutableLiveData<SubscriptionFormState> formState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Boolean>> saved = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> errors = new MutableLiveData<>();

    /** Id-ul abonamentului editat (null = abonament nou). */
    @Nullable private String editingId;
    private boolean initialized;

    public LiveData<Event<Subscription>> getInitial() { return initial; }

    public LiveData<Long> getSelectedDate() { return selectedDate; }

    public LiveData<SubscriptionFormState> getFormState() { return formState; }

    public LiveData<Boolean> getSaving() { return saving; }

    public LiveData<Event<Boolean>> getSaved() { return saved; }

    public LiveData<Event<Integer>> getErrors() { return errors; }

    public boolean isEditing() { return editingId != null; }

    /**
     * Pregătește formularul o singură dată (nu și după rotirea ecranului).
     * @param subscriptionId id-ul pentru editare sau null
     * @param catalogItem serviciul ales din catalog sau null (abonament personalizat)
     */
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
                selectedDate.setValue(subscription.getNextPaymentDate());
                initial.setValue(new Event<>(subscription));
            });
        } else if (catalogItem != null) {
            Subscription prefilled = new Subscription(catalogItem.getName(), catalogItem.getPrice(),
                    catalogItem.getCurrency(), catalogItem.getBillingCycle(),
                    defaultNextPayment(catalogItem.getBillingCycle()),
                    catalogItem.getCategory().name());
            selectedDate.setValue(prefilled.getNextPaymentDate());
            initial.setValue(new Event<>(prefilled));
        }
    }

    public void setSelectedDate(long millis) {
        selectedDate.setValue(millis);
    }

    /** Validează datele și salvează abonamentul în Firestore. */
    public void save(String name, String priceText, String currency, boolean yearly,
                     Category category, boolean notify) {
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

    /** Pentru un serviciu din catalog propunem prima plată peste o lună / un an de azi. */
    private static long defaultNextPayment(String cycle) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(DateUtils.startOfDay(System.currentTimeMillis()));
        calendar.add(Subscription.CYCLE_YEARLY.equals(cycle) ? Calendar.YEAR : Calendar.MONTH, 1);
        return calendar.getTimeInMillis();
    }
}
