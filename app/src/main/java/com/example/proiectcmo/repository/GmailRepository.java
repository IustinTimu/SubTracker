package com.example.proiectcmo.repository;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.proiectcmo.data.SubscriptionCatalog;
import com.example.proiectcmo.model.CatalogItem;
import com.example.proiectcmo.model.DetectedSubscription;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.utils.AmountParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

public class GmailRepository {
    public interface Callback {
        void onResult(List<DetectedSubscription> subscriptions);

        void onError(String detail);
    }

    private static final String TAG = "GmailRepository";
    private static final String BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages";

    private static final int MESSAGES_PER_SERVICE = 5;
    private static final int MAX_TEXT_LENGTH = 20000;

    private static final String RECEIPT_WORDS =
            "(factura OR factură OR chitanta OR chitanță OR plata OR plată OR abonament"
                    + " OR receipt OR invoice OR payment OR subscription OR renewal)";

    private static final Pattern YEARLY_WORDS = Pattern.compile(
            "\\b(anual|anuală|annual|yearly|per year|12 luni)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern STYLE_BLOCK = Pattern.compile(
            "<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final SubscriptionCatalog catalog;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final ExecutorService searchPool = Executors.newFixedThreadPool(5);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GmailRepository(SubscriptionCatalog catalog) {
        this.catalog = catalog;
    }

    public void findSubscriptions(String accessToken, Callback callback) {
        executor.execute(() -> {
            try {
                List<DetectedSubscription> result = search(accessToken);
                mainHandler.post(() -> callback.onResult(result));
            } catch (IOException | JSONException e) {
                Log.w(TAG, "Citirea Gmail a eșuat", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private List<DetectedSubscription> search(String token)
            throws IOException, JSONException {
        List<Future<DetectedSubscription>> tasks = new ArrayList<>();
        for (CatalogItem item : catalog.getItems()) {
            tasks.add(searchPool.submit(() -> searchService(item, token)));
        }

        List<DetectedSubscription> result = new ArrayList<>();
        String lastError = null;
        for (Future<DetectedSubscription> task : tasks) {
            try {
                DetectedSubscription detected = task.get();
                if (detected != null) {
                    result.add(detected);
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                Log.w(TAG, "Căutarea unui serviciu a eșuat", cause);
                lastError = String.valueOf(cause.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }
        if (result.isEmpty() && lastError != null) {
            throw new IOException(lastError);
        }
        return result;
    }

    @Nullable
    private DetectedSubscription searchService(CatalogItem item, String token)
            throws IOException, JSONException {
        String query = "from:(" + String.join(" OR ", item.getEmailDomains()) + ") "
                + (item.getKeyword().isEmpty() ? "" : "\"" + item.getKeyword() + "\" ")
                + RECEIPT_WORDS + " newer_than:1y";
        String listUrl = BASE_URL + "?maxResults=" + MESSAGES_PER_SERVICE
                + "&q=" + URLEncoder.encode(query, "UTF-8");
        JSONArray messages = new JSONObject(get(listUrl, token)).optJSONArray("messages");
        if (messages == null || messages.length() == 0) {
            return null;
        }

        for (int i = 0; i < messages.length(); i++) {
            String id = messages.getJSONObject(i).getString("id");
            JSONObject message = new JSONObject(get(BASE_URL + "/" + id + "?format=full", token));
            String text = readText(message);
            AmountParser.Result amount;
            if (item.getKeyword().isEmpty()) {
                amount = AmountParser.parse(text);
            } else {
                if (!text.toLowerCase(Locale.ROOT).contains(item.getKeyword().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                amount = AmountParser.parseNear(text, item.getKeyword());
                if (amount == null) {
                    amount = AmountParser.parse(text);
                }
            }
            if (amount != null) {
                String cycle = YEARLY_WORDS.matcher(text).find()
                        ? Subscription.CYCLE_YEARLY : item.getBillingCycle();
                return new DetectedSubscription(item.getName(), amount.amount, amount.currency,
                        cycle, item.getCategory(), message.optLong("internalDate"),
                        item.getCancelUrl());
            }
        }
        return null;
    }

    private static String readText(JSONObject message) {
        StringBuilder text = new StringBuilder();
        JSONObject payload = message.optJSONObject("payload");
        if (payload != null) {
            text.append(header(payload, "Subject")).append(' ');
        }
        text.append(message.optString("snippet")).append(' ');
        if (payload != null) {
            appendBodyText(payload, text);
        }
        return text.toString();
    }

    private static String header(JSONObject payload, String name) {
        JSONArray headers = payload.optJSONArray("headers");
        for (int i = 0; headers != null && i < headers.length(); i++) {
            JSONObject header = headers.optJSONObject(i);
            if (header != null && name.equalsIgnoreCase(header.optString("name"))) {
                return header.optString("value");
            }
        }
        return "";
    }

    private static void appendBodyText(JSONObject part, StringBuilder out) {
        if (out.length() >= MAX_TEXT_LENGTH) {
            return;
        }
        String mimeType = part.optString("mimeType");
        JSONObject body = part.optJSONObject("body");
        String data = body == null ? "" : body.optString("data");
        if (!data.isEmpty() && mimeType.startsWith("text/")) {
            try {
                String decoded = new String(
                        Base64.decode(data, Base64.URL_SAFE), StandardCharsets.UTF_8);
                if (mimeType.equals("text/html")) {
                    decoded = STYLE_BLOCK.matcher(decoded).replaceAll(" ");
                    decoded = Html.fromHtml(decoded, Html.FROM_HTML_MODE_COMPACT).toString();
                }
                out.append(decoded, 0, Math.min(decoded.length(), MAX_TEXT_LENGTH)).append(' ');
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Corp de email care nu a putut fi decodificat", e);
            }
        }
        JSONArray parts = part.optJSONArray("parts");
        for (int i = 0; parts != null && i < parts.length(); i++) {
            JSONObject child = parts.optJSONObject(i);
            if (child != null) {
                appendBodyText(child, out);
            }
        }
    }

    private static String get(String url, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = input.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                return buffer.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            connection.disconnect();
        }
    }
}
