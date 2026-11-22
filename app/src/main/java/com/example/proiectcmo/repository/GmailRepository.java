package com.example.proiectcmo.repository;

import android.content.Context;
import android.text.Html;
import android.util.Base64;
import android.util.Log;

import com.example.proiectcmo.importer.ExcerptBuilder;
import com.example.proiectcmo.importer.GmailQuery;
import com.example.proiectcmo.model.EmailExcerpt;
import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;

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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

public class GmailRepository {
    public static final class ExcerptBatch {
        public final List<EmailExcerpt> excerpts;
        public final boolean windowComplete;
        public final long newestEmailDate;

        ExcerptBatch(List<EmailExcerpt> excerpts, boolean windowComplete, long newestEmailDate) {
            this.excerpts = excerpts;
            this.windowComplete = windowComplete;
            this.newestEmailDate = newestEmailDate;
        }
    }

    private static final class GmailRateLimitException extends IOException {
        GmailRateLimitException(int code) {
            super("HTTP " + code + " rate limit");
        }
    }

    private static final String TAG = "GmailRepository";
    private static final String GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages";

    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int MAX_TEXT_LENGTH = 20000;
    private static final long[] RATE_LIMIT_WAITS_MS = {2_000, 4_000, 8_000, 16_000, 32_000, 32_000};

    private static final Pattern STYLE_BLOCK = Pattern.compile(
            "<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final ExecutorService searchPool = Executors.newFixedThreadPool(5);

    public ExcerptBatch fetchExcerpts(String token, long newerThanMillis, int max)
            throws IOException {
        List<String> ids = new ArrayList<>();
        String query = URLEncoder.encode(GmailQuery.build(newerThanMillis), "UTF-8");
        String pageToken = null;
        try {
            do {
                String url = BASE_URL + "?maxResults=" + Math.min(500, max - ids.size())
                        + "&q=" + query + (pageToken == null ? "" : "&pageToken=" + pageToken);
                JSONObject page = new JSONObject(get(url, token));
                JSONArray messages = page.optJSONArray("messages");
                for (int i = 0; messages != null && i < messages.length() && ids.size() < max; i++) {
                    ids.add(messages.getJSONObject(i).getString("id"));
                }
                pageToken = page.optString("nextPageToken", null);
            } while (pageToken != null && !pageToken.isEmpty() && ids.size() < max);
        } catch (JSONException e) {
            throw new IOException(e);
        }

        List<Future<EmailExcerpt>> tasks = new ArrayList<>();
        long[] newest = {0L};
        for (String id : ids) {
            tasks.add(searchPool.submit(() -> {
                JSONObject message = new JSONObject(get(BASE_URL + "/" + id + "?format=full", token));
                long date = message.optLong("internalDate");
                if (!GmailQuery.isNewer(date, newerThanMillis)) {
                    return null;
                }
                synchronized (newest) {
                    newest[0] = Math.max(newest[0], date);
                }
                JSONObject payload = message.optJSONObject("payload");
                StringBuilder body = new StringBuilder();
                if (payload != null) {
                    appendBodyText(payload, body);
                }
                return ExcerptBuilder.build(id, date,
                        payload == null ? "" : header(payload, "From"),
                        payload == null ? "" : header(payload, "Subject"), body.toString());
            }));
        }

        List<EmailExcerpt> excerpts = new ArrayList<>();
        int skipped = 0;
        for (Future<EmailExcerpt> task : tasks) {
            try {
                EmailExcerpt excerpt = task.get();
                if (excerpt != null) {
                    excerpts.add(excerpt);
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof GmailAuthException) {
                    cancelAll(tasks);
                    throw (GmailAuthException) cause;
                }
                skipped++;
                Log.w(TAG, "Un email a fost sărit: " + cause.getClass().getSimpleName()
                        + (cause.getClass() == IOException.class ? " " + cause.getMessage() : ""));
            } catch (InterruptedException e) {
                cancelAll(tasks);
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }
        boolean complete = skipped == 0;
        return new ExcerptBatch(excerpts, complete && ids.size() < max, complete ? newest[0] : 0);
    }

    public ExcerptBatch fetchExcerpts(Context context, String token, long newerThanMillis, int max)
            throws IOException, ExecutionException, InterruptedException {
        try {
            return fetchExcerpts(token, newerThanMillis, max);
        } catch (GmailAuthException e) {
            if (e.getCode() != HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw e;
            }
            AuthorizationClient client = Identity.getAuthorizationClient(context);
            Tasks.await(client.clearToken(ClearTokenRequest.builder().setToken(token).build()));
            AuthorizationResult result = Tasks.await(client.authorize(authorizationRequest()));
            if (result.hasResolution() || result.getAccessToken() == null) {
                throw e;
            }
            return fetchExcerpts(result.getAccessToken(), newerThanMillis, max);
        }
    }

    public static AuthorizationRequest authorizationRequest() {
        return AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(GMAIL_READONLY_SCOPE)))
                .build();
    }

    private static void cancelAll(List<? extends Future<?>> tasks) {
        for (Future<?> task : tasks) {
            task.cancel(true);
        }
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
                    decoded = decoded.replaceAll("(?i)</(tr|td|th|li|table|div|p|h[1-6])>", "$0<br>");
                    decoded = Html.fromHtml(decoded, Html.FROM_HTML_MODE_COMPACT).toString();
                }
                out.append(decoded, 0, Math.min(decoded.length(), MAX_TEXT_LENGTH)).append(' ');
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Corp de email care nu a putut fi decodificat", e);
            }
        }
        JSONArray parts = part.optJSONArray("parts");
        JSONObject plain = mimeType.equals("multipart/alternative") ? plainPart(parts) : null;
        if (plain != null) {
            appendBodyText(plain, out);
            return;
        }
        for (int i = 0; parts != null && i < parts.length(); i++) {
            JSONObject child = parts.optJSONObject(i);
            if (child != null) {
                appendBodyText(child, out);
            }
        }
    }

    private static JSONObject plainPart(JSONArray parts) {
        for (int i = 0; parts != null && i < parts.length(); i++) {
            JSONObject child = parts.optJSONObject(i);
            JSONObject body = child == null ? null : child.optJSONObject("body");
            if (child != null && "text/plain".equals(child.optString("mimeType"))
                    && body != null && !body.optString("data").isEmpty()) {
                return child;
            }
        }
        return null;
    }

    private static String get(String url, String token) throws IOException {
        for (long wait : RATE_LIMIT_WAITS_MS) {
            try {
                return getOnce(url, token);
            } catch (GmailRateLimitException e) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }
        }
        return getOnce(url, token);
    }

    private static String getOnce(String url, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw new GmailAuthException(code);
            }
            if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                if (GmailQuery.isRateLimit(readAll(connection.getErrorStream()))) {
                    throw new GmailRateLimitException(code);
                }
                throw new GmailAuthException(code);
            }
            if (code == HTTP_TOO_MANY_REQUESTS) {
                throw new GmailRateLimitException(code);
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            return readAll(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }
}
