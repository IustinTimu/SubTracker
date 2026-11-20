package com.example.proiectcmo.viewmodel;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.proiectcmo.model.Category;
import com.example.proiectcmo.model.CategoryStat;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.repository.SubscriptionRepository;
import com.example.proiectcmo.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class StatisticsViewModel extends ViewModel {
    public static final class Statistics {
        private final double monthlyTotal;
        private final List<CategoryStat> categories;
        @Nullable private final Subscription mostExpensive;

        Statistics(double monthlyTotal, List<CategoryStat> categories,
                   @Nullable Subscription mostExpensive) {
            this.monthlyTotal = monthlyTotal;
            this.categories = categories;
            this.mostExpensive = mostExpensive;
        }

        public double getMonthlyTotal() { return monthlyTotal; }

        public double getYearlyTotal() { return monthlyTotal * 12; }

        public List<CategoryStat> getCategories() { return categories; }

        @Nullable
        public Subscription getMostExpensive() { return mostExpensive; }
    }

    private final LiveData<Statistics> statistics = Transformations.map(
            new SubscriptionRepository().observeAll(), StatisticsViewModel::compute);

    public LiveData<Statistics> getStatistics() {
        return statistics;
    }

    static Statistics compute(List<Subscription> subscriptions) {
        Map<Category, Double> sums = new EnumMap<>(Category.class);
        double total = 0;
        Subscription mostExpensive = null;
        double maxMonthly = -1;

        for (Subscription s : subscriptions) {
            if (s.isCancelled()) {
                continue;
            }
            double monthly = MoneyUtils.toRon(s.getMonthlyCost(), s.getCurrency());
            total += monthly;
            Category category = s.getCategoryEnum();
            Double previous = sums.get(category);
            sums.put(category, (previous == null ? 0 : previous) + monthly);
            if (monthly > maxMonthly) {
                maxMonthly = monthly;
                mostExpensive = s;
            }
        }

        List<CategoryStat> stats = new ArrayList<>();
        for (Map.Entry<Category, Double> entry : sums.entrySet()) {
            float fraction = total > 0 ? (float) (entry.getValue() / total) : 0f;
            stats.add(new CategoryStat(entry.getKey(), entry.getValue(), fraction));
        }
        Collections.sort(stats, (a, b) -> Double.compare(b.getMonthlyAmount(), a.getMonthlyAmount()));
        return new Statistics(total, stats, mostExpensive);
    }
}
