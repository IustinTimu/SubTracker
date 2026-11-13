package com.example.proiectcmo.model;

/** Cât se cheltuie lunar (în lei) pe o categorie și ce procent reprezintă din total. */
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

    /** Valoare între 0 și 1 (ex. 0.25 = 25%). */
    public float getFraction() { return fraction; }

    public int getPercent() { return Math.round(fraction * 100); }
}
