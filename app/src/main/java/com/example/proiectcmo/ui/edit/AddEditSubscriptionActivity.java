package com.example.proiectcmo.ui.edit;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.proiectcmo.R;
import com.example.proiectcmo.data.SubscriptionCatalog;
import com.example.proiectcmo.model.Category;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.notifications.NotificationHelper;
import com.example.proiectcmo.utils.CancelLinks;
import com.example.proiectcmo.utils.DatePickerUtils;
import com.example.proiectcmo.utils.DateUtils;
import com.example.proiectcmo.utils.MoneyUtils;
import com.example.proiectcmo.utils.UiUtils;
import com.example.proiectcmo.viewmodel.AddEditViewModel;
import com.example.proiectcmo.viewmodel.SubscriptionFormState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

/**
 * Ecranul 05 – formularul de adăugare sau editare a unui abonament.
 *
 * Se deschide cu {@link #addIntent} (abonament nou, eventual precompletat din catalog)
 * sau cu {@link #editIntent} (abonament existent, după id).
 */
public class AddEditSubscriptionActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "subscription_id";
    private static final String EXTRA_CATALOG_NAME = "catalog_name";
    /** Deschide direct explicația despre anulare (din alerta de plată, când nu există link). */
    public static final String EXTRA_SHOW_CANCEL = "show_cancel";
    private static final String DATE_PICKER_TAG = "date_picker";
    private static final int CHIP_ID_BASE = 0x00100000; // în afara zonei id-urilor din R

    private AddEditViewModel viewModel;

    private TextInputLayout nameLayout;
    private TextInputLayout priceLayout;
    private TextInputLayout dateLayout;
    private AutoCompleteTextView currencyInput;
    private MaterialButtonToggleGroup cycleToggle;
    private ChipGroup categoryChips;
    private MaterialSwitch notifySwitch;
    private TextInputLayout cancelUrlLayout;
    private View cancelCard;
    private MaterialButton cancelStatusButton;
    /** true după ce s-a deschis pagina de anulare; la revenire întrebăm dacă abonamentul a fost anulat. */
    private boolean waitingForCancelConfirmation;
    private MaterialButton saveButton;
    private View progressBar;

    /** Abonament nou; {@code catalogName} = serviciul ales din catalog sau null (personalizat). */
    public static Intent addIntent(Context context, @Nullable String catalogName) {
        return new Intent(context, AddEditSubscriptionActivity.class)
                .putExtra(EXTRA_CATALOG_NAME, catalogName);
    }

    /** Editarea unui abonament existent. */
    public static Intent editIntent(Context context, String subscriptionId) {
        return new Intent(context, AddEditSubscriptionActivity.class)
                .putExtra(EXTRA_ID, subscriptionId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_add_edit);
        UiUtils.applySystemBarsPadding(findViewById(R.id.main));

        viewModel = new ViewModelProvider(this).get(AddEditViewModel.class);
        String id = getIntent().getStringExtra(EXTRA_ID);
        String catalogName = getIntent().getStringExtra(EXTRA_CATALOG_NAME);
        viewModel.init(id, catalogName == null ? null
                : SubscriptionCatalog.getInstance(this).findByName(catalogName));

        bindViews();
        setupCurrency();
        setupCategories();
        setupListeners();
        observeViewModel();
    }

    private void bindViews() {
        TextView title = findViewById(R.id.screenTitle);
        title.setText(viewModel.isEditing() ? R.string.edit_title : R.string.add_title);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        nameLayout = findViewById(R.id.nameLayout);
        priceLayout = findViewById(R.id.priceLayout);
        dateLayout = findViewById(R.id.dateLayout);
        currencyInput = findViewById(R.id.currencyInput);
        cycleToggle = findViewById(R.id.cycleToggle);
        categoryChips = findViewById(R.id.categoryChips);
        notifySwitch = findViewById(R.id.notifySwitch);
        cancelUrlLayout = findViewById(R.id.cancelUrlLayout);
        cancelCard = findViewById(R.id.cancelCard);
        cancelStatusButton = findViewById(R.id.cancelStatusButton);
        saveButton = findViewById(R.id.saveButton);
        progressBar = findViewById(R.id.progressBar);
    }

    /** Lista derulantă cu monedele RON / EUR / USD. */
    private void setupCurrency() {
        currencyInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, MoneyUtils.CURRENCIES));
        if (currencyInput.getText().length() == 0) {
            currencyInput.setText(MoneyUtils.RON, false);
        }
    }

    /** Creează câte un chip pentru fiecare categorie, cu un punct colorat în față. */
    private void setupCategories() {
        for (Category category : Category.values()) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_category_chip, categoryChips, false);
            // Id fix (nu generat), ca Android să păstreze chip-ul bifat la rotirea ecranului.
            chip.setId(CHIP_ID_BASE + category.ordinal());
            chip.setTag(category);
            chip.setText(category.getLabelRes());
            chip.setChipIconTint(ColorStateList.valueOf(
                    ContextCompat.getColor(this, category.getColorRes())));
            categoryChips.addView(chip);
            if (category == Category.STREAMING) {
                chip.setChecked(true);
            }
        }
    }

    private void setupListeners() {
        UiUtils.clearErrorOnType(nameLayout);
        UiUtils.clearErrorOnType(priceLayout);

        // Data se alege doar din calendar: click pe câmp sau pe iconiță.
        View dateInput = findViewById(R.id.dateInput);
        dateInput.setOnClickListener(v -> showDatePicker());
        dateLayout.setEndIconOnClickListener(v -> showDatePicker());

        // Când utilizatorul activează notificarea, verificăm că aplicația chiar poate notifica.
        notifySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !NotificationHelper.canNotify(this)) {
                Snackbar.make(findViewById(R.id.main), R.string.notifications_denied,
                        Snackbar.LENGTH_LONG).show();
            }
        });

        saveButton.setOnClickListener(v -> viewModel.save(
                UiUtils.textOf(nameLayout),
                UiUtils.textOf(priceLayout),
                currencyInput.getText().toString(),
                cycleToggle.getCheckedButtonId() == R.id.yearlyButton,
                selectedCategory(),
                notifySwitch.isChecked(),
                UiUtils.textOf(cancelUrlLayout)));

        findViewById(R.id.cancelPageButton).setOnClickListener(v -> openCancelPage());
        cancelStatusButton.setOnClickListener(v -> {
            Subscription original = viewModel.getOriginal();
            if (original != null) {
                viewModel.setCancelled(!original.isCancelled());
            }
        });
    }

    private void observeViewModel() {
        viewModel.getInitial().observe(this, event -> {
            Subscription subscription = event.getIfNotHandled();
            if (subscription != null) {
                fillForm(subscription);
                if (viewModel.isEditing()) {
                    showCancelCard(subscription);
                    if (getIntent().getBooleanExtra(EXTRA_SHOW_CANCEL, false)) {
                        getIntent().removeExtra(EXTRA_SHOW_CANCEL); // o singură dată
                        openCancelPage();
                    }
                }
            }
        });
        viewModel.getSelectedDate().observe(this, date -> {
            dateLayout.setError(null);
            if (date != null) {
                ((TextView) findViewById(R.id.dateInput)).setText(DateUtils.formatDate(date));
            }
        });
        viewModel.getFormState().observe(this, this::showFormErrors);
        viewModel.getSaving().observe(this, saving -> {
            saveButton.setEnabled(!saving);
            saveButton.setText(saving ? "" : getString(R.string.action_save_subscription));
            progressBar.setVisibility(saving ? View.VISIBLE : View.GONE);
        });
        viewModel.getSaved().observe(this, event -> {
            if (event.getIfNotHandled() != null) {
                finish(); // lista de pe ecranul principal se actualizează singură (listener Firestore)
            }
        });
        viewModel.getErrors().observe(this, event -> {
            Integer message = event.getIfNotHandled();
            if (message != null) {
                Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    /** Completează câmpurile cu valorile abonamentului editat / ale serviciului din catalog. */
    private void fillForm(Subscription subscription) {
        nameLayout.getEditText().setText(subscription.getName());
        priceLayout.getEditText().setText(
                String.format(Locale.US, "%.2f", subscription.getPrice()));
        currencyInput.setText(subscription.getCurrency() == null
                ? MoneyUtils.RON : subscription.getCurrency(), false);
        cycleToggle.check(subscription.isYearly() ? R.id.yearlyButton : R.id.monthlyButton);
        notifySwitch.setChecked(subscription.isNotifyBeforePayment());
        // Abonamentele mai vechi nu au link salvat: îl propunem pe cel din catalog.
        String cancelUrl = CancelLinks.resolve(this, subscription);
        cancelUrlLayout.getEditText().setText(cancelUrl == null ? "" : cancelUrl);
        Category category = subscription.getCategoryEnum();
        for (int i = 0; i < categoryChips.getChildCount(); i++) {
            Chip chip = (Chip) categoryChips.getChildAt(i);
            if (chip.getTag() == category) {
                chip.setChecked(true);
            }
        }
    }

    /** Cardul „Anulare”: butonul spre pagina de anulare și marcarea ca anulat / reactivarea. */
    private void showCancelCard(Subscription subscription) {
        cancelCard.setVisibility(View.VISIBLE);
        boolean cancelled = subscription.isCancelled();
        findViewById(R.id.cancelPageButton).setVisibility(cancelled ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.cancelCardText)).setText(
                cancelled ? R.string.cancel_status_cancelled : R.string.cancel_card_text);
        cancelStatusButton.setText(cancelled ? R.string.action_reactivate : R.string.action_mark_cancelled);
    }

    /**
     * Deschide pagina de anulare (link-ul din câmp sau din catalog). Dacă nu există link,
     * explică unde se anulează de obicei un abonament.
     */
    private void openCancelPage() {
        String typed = UiUtils.textOf(cancelUrlLayout).trim();
        Subscription original = viewModel.getOriginal();
        String url = !typed.isEmpty() ? typed
                : original == null ? null : CancelLinks.resolve(this, original);
        String name = original == null ? UiUtils.textOf(nameLayout) : original.getName();
        waitingForCancelConfirmation = CancelLinks.openOrExplain(this, name, url);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Subscription original = viewModel.getOriginal();
        if (waitingForCancelConfirmation && original != null) {
            waitingForCancelConfirmation = false;
            CancelLinks.askIfCancelled(this, original.getName(), () -> viewModel.setCancelled(true));
        }
    }

    private Category selectedCategory() {
        View checked = categoryChips.findViewById(categoryChips.getCheckedChipId());
        return checked == null ? Category.OTHER : (Category) checked.getTag();
    }

    private void showFormErrors(SubscriptionFormState form) {
        nameLayout.setError(form.getNameError() == null ? null : getString(form.getNameError()));
        priceLayout.setError(form.getPriceError() == null ? null : getString(form.getPriceError()));
        dateLayout.setError(form.getDateError() == null ? null : getString(form.getDateError()));
    }

    /** Calendarul Material (MaterialDatePicker); permite doar date de azi încolo. */
    private void showDatePicker() {
        if (getSupportFragmentManager().findFragmentByTag(DATE_PICKER_TAG) != null) {
            return; // e deja deschis (dublu click)
        }
        Long current = viewModel.getSelectedDate().getValue();
        long selection = DatePickerUtils.localToUtcSelection(
                current != null ? current : System.currentTimeMillis());

        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.date_picker_title)
                .setSelection(selection)
                .setCalendarConstraints(constraints)
                .build();
        picker.addOnPositiveButtonClickListener(utc ->
                viewModel.setSelectedDate(DatePickerUtils.utcSelectionToLocal(utc)));
        picker.show(getSupportFragmentManager(), DATE_PICKER_TAG);
    }
}
