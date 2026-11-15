package com.example.proiectcmo.ui.main;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proiectcmo.R;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.notifications.NotificationHelper;
import com.example.proiectcmo.notifications.PaymentReminderScheduler;
import com.example.proiectcmo.ui.auth.LoginActivity;
import com.example.proiectcmo.ui.catalog.CatalogActivity;
import com.example.proiectcmo.ui.edit.AddEditSubscriptionActivity;
import com.example.proiectcmo.ui.gmail.GmailImportActivity;
import com.example.proiectcmo.ui.statistics.StatisticsActivity;
import com.example.proiectcmo.utils.CancelLinks;
import com.example.proiectcmo.utils.DateUtils;
import com.example.proiectcmo.utils.MoneyUtils;
import com.example.proiectcmo.utils.UiUtils;
import com.example.proiectcmo.viewmodel.AuthViewModel;
import com.example.proiectcmo.viewmodel.BillingFilter;
import com.example.proiectcmo.viewmodel.HomeSummary;
import com.example.proiectcmo.viewmodel.HomeViewModel;
import com.example.proiectcmo.viewmodel.SortOrder;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

/**
 * Ecranul principal (03): totalul lunar, filtrele și lista abonamentelor.
 *
 * Evenimente UI: click pe card → editare, click lung → meniu contextual,
 * FAB → catalogul de abonamente, meniul din bara de sus → Statistici / Import Gmail / Deconectare.
 */
public class MainActivity extends AppCompatActivity implements SubscriptionAdapter.Listener {

    private AuthViewModel authViewModel;
    private HomeViewModel homeViewModel;

    private SubscriptionAdapter adapter;
    /** Abonamentul a cărui pagină de anulare a fost deschisă; întrebăm la revenire dacă a fost anulat. */
    private Subscription pendingCancelConfirmation;
    private RecyclerView list;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyHint;
    private View progressBar;
    private TextView totalAmountText;
    private TextView activeSummaryText;
    private TextView nextPaymentPill;

    /** Cere permisiunea de notificări (Android 13+); răspunsul vine asincron. */
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // Dacă utilizatorul refuză, aplicația funcționează normal, doar fără notificări;
                // formularul de abonament îi reamintește acest lucru când activează notificarea.
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Protecție: ecranul principal nu poate fi deschis fără un utilizator logat.
        FirebaseUser user = authViewModel.getCurrentUser();
        if (user == null) {
            openLoginScreen();
            return;
        }

        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        UiUtils.applySystemBarsPadding(findViewById(R.id.main));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // Iconița „trei puncte” în cerc, ca în design.
        toolbar.setOverflowIcon(ContextCompat.getDrawable(this, R.drawable.ic_more_circle));
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        bindViews(user);
        setupList();
        setupFiltersAndSort();
        observeViewModel();
        requestNotificationPermission();
    }

    private void bindViews(FirebaseUser user) {
        TextView greeting = findViewById(R.id.greetingText);
        String name = user.getDisplayName();
        if (name == null || name.isEmpty()) {
            name = user.getEmail();
        } else {
            name = name.split(" ")[0]; // doar prenumele, ca în design: „Bună, Ana”
        }
        greeting.setText(getString(R.string.greeting_user, name));

        list = findViewById(R.id.subscriptionsList);
        emptyState = findViewById(R.id.emptyState);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyHint = findViewById(R.id.emptyHint);
        progressBar = findViewById(R.id.progressBar);
        totalAmountText = findViewById(R.id.totalAmountText);
        activeSummaryText = findViewById(R.id.activeSummaryText);
        nextPaymentPill = findViewById(R.id.nextPaymentPill);

        findViewById(R.id.addFab).setOnClickListener(v ->
                startActivity(new Intent(this, CatalogActivity.class)));
    }

    private void setupList() {
        adapter = new SubscriptionAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
    }

    private void setupFiltersAndSort() {
        ChipGroup chips = findViewById(R.id.filterChips);
        chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            homeViewModel.setFilter(id == R.id.chipMonthly ? BillingFilter.MONTHLY
                    : id == R.id.chipYearly ? BillingFilter.YEARLY : BillingFilter.ALL);
        });

        View sortButton = findViewById(R.id.sortButton);
        sortButton.setOnClickListener(this::showSortMenu);
    }

    /** Meniu pop-up cu criteriile de sortare; criteriul curent apare bifat. */
    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.menu_sort);
        SortOrder current = homeViewModel.getSortOrder().getValue();
        int checkedId = current == SortOrder.PRICE ? R.id.sort_price
                : current == SortOrder.NAME ? R.id.sort_name : R.id.sort_date;
        popup.getMenu().findItem(checkedId).setChecked(true);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            homeViewModel.setSortOrder(id == R.id.sort_price ? SortOrder.PRICE
                    : id == R.id.sort_name ? SortOrder.NAME : SortOrder.NEXT_PAYMENT);
            return true;
        });
        popup.show();
    }

    private void observeViewModel() {
        homeViewModel.getVisibleSubscriptions().observe(this, this::renderList);
        homeViewModel.getSummary().observe(this, this::renderSummary);
        homeViewModel.getMessages().observe(this, event -> {
            Integer message = event.getIfNotHandled();
            if (message != null) {
                showMessage(message);
            }
        });
    }

    private void renderList(List<Subscription> subscriptions) {
        progressBar.setVisibility(View.GONE);
        adapter.submitList(subscriptions);

        boolean empty = subscriptions.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            // Lista e goală din cauza filtrului sau chiar nu există abonamente?
            boolean filtered = homeViewModel.getFilter().getValue() != BillingFilter.ALL;
            emptyTitle.setText(filtered ? R.string.empty_filter : R.string.empty_subscriptions);
            emptyHint.setVisibility(filtered ? View.GONE : View.VISIBLE);
        }
    }

    private void renderSummary(HomeSummary summary) {
        totalAmountText.setText(MoneyUtils.formatNumber(summary.getMonthlyTotal()));
        activeSummaryText.setText(UiUtils.quantityString(this, R.plurals.active_summary,
                summary.getCount(), summary.getCount(),
                MoneyUtils.formatRounded(summary.getYearlyTotal())));

        Subscription next = summary.getNextPayment();
        nextPaymentPill.setText(next == null ? getString(R.string.no_next_payment)
                : getString(R.string.next_payment_pill, next.getName(),
                DateUtils.relativeDays(this, next.getNextPaymentDate())));
    }

    // ---------- Evenimente din listă ----------

    @Override
    public void onSubscriptionClick(Subscription subscription) {
        openEditor(subscription);
    }

    /**
     * Click lung → meniu contextual: Editează / Marchează ca plătit / Anulează abonamentul /
     * Marchează ca anulat (sau Reactivează) / Șterge.
     */
    @Override
    public void onSubscriptionLongClick(Subscription subscription, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor, android.view.Gravity.END);
        popup.inflate(R.menu.menu_subscription_context);
        popup.setForceShowIcon(true);
        // Un abonament anulat nu se mai plătește, deci nu are rost „Marchează ca plătit”.
        popup.getMenu().findItem(R.id.action_mark_paid).setVisible(!subscription.isCancelled());
        popup.getMenu().findItem(R.id.action_cancel_subscription).setVisible(!subscription.isCancelled());
        popup.getMenu().findItem(R.id.action_toggle_cancelled).setTitle(subscription.isCancelled()
                ? R.string.action_reactivate : R.string.action_mark_cancelled);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit) {
                openEditor(subscription);
            } else if (id == R.id.action_mark_paid) {
                homeViewModel.markAsPaid(subscription);
            } else if (id == R.id.action_cancel_subscription) {
                openCancelPage(subscription);
            } else if (id == R.id.action_toggle_cancelled) {
                homeViewModel.setCancelled(subscription, !subscription.isCancelled());
            } else if (id == R.id.action_delete) {
                confirmDelete(subscription);
            }
            return true;
        });
        popup.show();
    }

    /**
     * Deschide pagina de anulare a serviciului. La întoarcerea în aplicație îl întrebăm
     * pe utilizator dacă a anulat, ca să marcheze abonamentul (vezi {@link #onResume()}).
     */
    private void openCancelPage(Subscription subscription) {
        String url = CancelLinks.resolve(this, subscription);
        if (url == null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.cancel_help_title, subscription.getName()))
                    .setMessage(R.string.cancel_help_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else if (CancelLinks.open(this, url)) {
            pendingCancelConfirmation = subscription;
        } else {
            showMessage(R.string.error_no_browser);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingCancelConfirmation != null) {
            Subscription subscription = pendingCancelConfirmation;
            pendingCancelConfirmation = null;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.cancel_confirm_title, subscription.getName()))
                    .setMessage(R.string.cancel_confirm_message)
                    .setNegativeButton(R.string.action_not_yet, null)
                    .setPositiveButton(R.string.action_mark_cancelled,
                            (dialog, which) -> homeViewModel.setCancelled(subscription, true))
                    .show();
        }
    }

    private void openEditor(Subscription subscription) {
        startActivity(AddEditSubscriptionActivity.editIntent(this, subscription.getId()));
    }

    /** Dialog de confirmare înainte de ștergere; după ștergere se poate anula din Snackbar. */
    private void confirmDelete(Subscription subscription) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, subscription.getName()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    homeViewModel.delete(subscription);
                    Snackbar.make(findViewById(R.id.main), R.string.subscription_deleted,
                                    Snackbar.LENGTH_LONG)
                            .setAnchorView(R.id.addFab)
                            .setAction(R.string.action_undo, v -> homeViewModel.undoDelete(subscription))
                            .setActionTextColor(ContextCompat.getColor(this, R.color.st_secondary))
                            .show();
                })
                .show();
    }

    // ---------- Meniul din bara de sus ----------

    /** Încarcă meniul din bara de sus (res/menu/menu_main.xml). */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // „Statistici” are un aspect personalizat (iconiță în cerc), deci tratăm click-ul manual.
        MenuItem statistics = menu.findItem(R.id.action_statistics);
        View actionView = statistics.getActionView();
        if (actionView != null) {
            actionView.findViewById(R.id.statisticsButton)
                    .setOnClickListener(v -> onOptionsItemSelected(statistics));
        }
        return true;
    }

    /** Tratează click-urile pe opțiunile din meniu. */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_statistics) {
            startActivity(new Intent(this, StatisticsActivity.class));
            return true;
        } else if (id == R.id.action_import_gmail) {
            startActivity(new Intent(this, GmailImportActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            confirmLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Cere confirmarea utilizatorului înainte de deconectare. */
    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_logout, (dialog, which) -> {
                    PaymentReminderScheduler.cancelAll(this);
                    authViewModel.logout();
                    openLoginScreen();
                })
                .show();
    }

    private void requestNotificationPermission() {
        NotificationHelper.createChannel(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !NotificationHelper.canNotify(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void showMessage(int messageRes) {
        Snackbar.make(findViewById(R.id.main), messageRes, Snackbar.LENGTH_LONG)
                .setAnchorView(R.id.addFab)
                .show();
    }

    private void openLoginScreen() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
