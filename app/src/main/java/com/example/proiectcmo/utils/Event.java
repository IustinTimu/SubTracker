package com.example.proiectcmo.utils;

import androidx.annotation.Nullable;

public final class Event<T> {
    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    @Nullable
    public T getIfNotHandled() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }
}
