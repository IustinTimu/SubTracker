package com.example.proiectcmo.ui.statistics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.proiectcmo.R;
import com.example.proiectcmo.model.CategoryStat;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.ui.views.DonutChartView;
import com.example.proiectcmo.utils.MoneyUtils;
import com.example.proiectcmo.utils.UiUtils;
import com.example.proiectcmo.viewmodel.StatisticsViewModel;

public class StatisticsActivity extends AppCompatActivity {
    private TextView monthlyText;
    private TextView yearlyText;
    private DonutChartView donut;
    private LinearLayout legend;
    private View chartCard;
    private View emptyText;
    private View mostExpensiveCard;
    private TextView mostExpensiveText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_statistics);
        UiUtils.applySystemBarsPadding(findViewById(R.id.main));

        ((TextView) findViewById(R.id.screenTitle)).setText(R.string.statistics_title);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        monthlyText = findViewById(R.id.monthlyText);
        yearlyText = findViewById(R.id.yearlyText);
        donut = findViewById(R.id.donutChart);
        legend = findViewById(R.id.legendContainer);
        chartCard = findViewById(R.id.chartCard);
        emptyText = findViewById(R.id.emptyText);
        mostExpensiveCard = findViewById(R.id.mostExpensiveCard);
        mostExpensiveText = findViewById(R.id.mostExpensiveText);

        StatisticsViewModel viewModel = new ViewModelProvider(this).get(StatisticsViewModel.class);
        viewModel.getStatistics().observe(this, this::render);
    }

    private void render(StatisticsViewModel.Statistics stats) {
        monthlyText.setText(getString(R.string.amount_lei, MoneyUtils.formatNumber(stats.getMonthlyTotal())));
        yearlyText.setText(getString(R.string.amount_lei, MoneyUtils.formatRounded(stats.getYearlyTotal())));

        boolean empty = stats.getCategories().isEmpty();
        chartCard.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);

        donut.setData(stats.getCategories(), MoneyUtils.formatNumber(stats.getMonthlyTotal()),
                getString(R.string.currency_lei) + " " + getString(R.string.donut_center_label));
        renderLegend(stats);

        Subscription top = stats.getMostExpensive();
        mostExpensiveCard.setVisibility(top == null ? View.GONE : View.VISIBLE);
        if (top != null) {
            mostExpensiveText.setText(getString(R.string.most_expensive_value, top.getName(),
                    getString(R.string.amount_lei, MoneyUtils.formatNumber(
                            MoneyUtils.toRon(top.getMonthlyCost(), top.getCurrency())))));
        }
    }

    private void renderLegend(StatisticsViewModel.Statistics stats) {
        legend.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (CategoryStat stat : stats.getCategories()) {
            View row = inflater.inflate(R.layout.item_legend, legend, false);
            UiUtils.tintBackground(row.findViewById(R.id.legendDot), stat.getCategory().getColorRes());
            ((TextView) row.findViewById(R.id.legendName)).setText(stat.getCategory().getLabelRes());
            ((TextView) row.findViewById(R.id.legendAmount)).setText(
                    getString(R.string.amount_lei, MoneyUtils.formatNumber(stat.getMonthlyAmount())));
            ((TextView) row.findViewById(R.id.legendPercent)).setText(
                    getString(R.string.legend_percent, stat.getPercent()));
            legend.addView(row);
        }
    }
}
