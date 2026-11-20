package com.example.proiectcmo.model;

public final class CategoryStat {
    private final Category category;
    private final double monthlyAmount;
    private final float fraction;

    public CategoryStat(Category category, double monthlyAmount, float fraction) {
        this.category = category;
        this.monthlyAmount = monthlyAmount;
        this.fraction = fraction;
    }

    public Category getCategory() { return category; }

    public double getMonthlyAmount() { return monthlyAmount; }

    public float getFraction() { return fraction; }

    public int getPercent() { return Math.round(fraction * 100); }
}
