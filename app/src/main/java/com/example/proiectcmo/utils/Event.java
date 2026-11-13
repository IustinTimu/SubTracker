package com.example.proiectcmo.utils;

import androidx.annotation.Nullable;

/**
 * Învelește o valoare publicată prin LiveData care trebuie tratată o singură dată
 * (ex. un mesaj Snackbar sau închiderea ecranului). Fără ea, mesajul ar reapărea
 * după rotirea ecranului, când LiveData retrimite ultima valoare.
 */
public final class Event<T> {

    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    /** @return valoarea la primul apel, apoi null. */
    @Nullable
    public T getIfNotHandled() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }
}
