package com.example.proiectcmo.viewmodel;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

public final class SubscriptionFormState {
    @Nullable private final Integer nameError;
    @Nullable private final Integer priceError;
    @Nullable private final Integer dateError;

    public SubscriptionFormState(@Nullable @StringRes Integer nameError,
                                 @Nullable @StringRes Integer priceError,
                                 @Nullable @StringRes Integer dateError) {
        this.nameError = nameError;
        this.priceError = priceError;
        this.dateError = dateError;
    }

    public boolean isValid() {
        return nameError == null && priceError == null && dateError == null;
    }

    @Nullable public Integer getNameError() { return nameError; }

    @Nullable public Integer getPriceError() { return priceError; }

    @Nullable public Integer getDateError() { return dateError; }
}
