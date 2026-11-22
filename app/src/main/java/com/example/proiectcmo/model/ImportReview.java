package com.example.proiectcmo.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImportReview {
    public List<ReviewItem> newItems = new ArrayList<>();
    public List<ReviewItem> updates = new ArrayList<>();
    public List<ReviewItem> inactive = new ArrayList<>();
    public List<ReviewItem> toVerify = new ArrayList<>();
    public Map<String, Long> lastChargeSync = new HashMap<>();

    public boolean isEmpty() {
        return newItems.isEmpty() && updates.isEmpty() && inactive.isEmpty() && toVerify.isEmpty();
    }

    public List<ReviewItem> selected() {
        List<ReviewItem> result = new ArrayList<>();
        for (List<ReviewItem> group : Arrays.asList(newItems, updates, inactive, toVerify)) {
            for (ReviewItem item : group) {
                if (item.selected) {
                    result.add(item);
                }
            }
        }
        return result;
    }
}
