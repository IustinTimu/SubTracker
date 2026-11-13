package com.example.proiectcmo.repository;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Base64;
import android.util.Log;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Citește emailurile din Gmail prin API-ul REST și caută în ele abonamente.
 *
 * Emailurile sunt descărcate și analizate doar pe telefon, în memorie: nu se salvează
 * nicăieri și nu sunt trimise către alt server. Accesul este doar de citire
 * (permisiunea gmail.readonly).
 */
public class GmailRepository {

    /** Rezultatul căutării, livrat pe firul principal. */
    public interface Callback {
        void onResult(List<DetectedSubscription> subscriptions);

        /** @param detail codul HTTP sau mesajul erorii, pentru afișare */
        void onError(String detail);
    }

    private static final String TAG = "GmailRepository";
    private static final String BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
    /** Câte emailuri analizăm cel mult (fiecare înseamnă o cerere HTTP). */
    private static final int MAX_MESSAGES = 40;
    private static final int MAX_TEXT_LENGTH = 20000;

    /** Cuvinte care arată că emailul este o chitanță/factură, nu o reclamă. */
    private static final String RECEIPT_WORDS =
            "(factura OR factură OR chitanta OR chitanță OR plata OR plată OR abonament"
                    + " OR receipt OR invoice OR payment OR subscription OR renewal)";

    private static final Pattern EMAIL_ADDRESS = Pattern.compile("[\\w.+-]+@[\\w.-]+");
    private static final Pattern YEARLY_WORDS = Pattern.compile(
            "\\b(anual|anuală|annual|yearly|per year|12 luni)\\b", Pattern.CASE_INSENSITIVE);

    private final SubscriptionCatalog catalog;
    /** Cererile de rețea nu au voie pe firul principal, așa că rulează pe un fir separat. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GmailRepository(SubscriptionCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Caută emailuri din ultimul an de la serviciile din catalog și întoarce
     * câte un abonament propus pentru fiecare serviciu găsit.
     *
     * @param accessToken token-ul OAuth primit după ce utilizatorul a acordat accesul
     */
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

    private List<DetectedSubscription> search(String token) throws IOException, JSONException {
        // 1. Lista id-urilor de emailuri care se potrivesc cu interogarea
        String query = "from:(" + String.join(" OR ", catalog.getAllEmailDomains()) + ") "
                + RECEIPT_WORDS + " newer_than:1y";
        String listUrl = BASE_URL + "?maxResults=" + MAX_MESSAGES
                + "&q=" + URLEncoder.encode(query, "UTF-8");
        JSONArray messages = new JSONObject(get(listUrl, token)).optJSONArray("messages");

        // 2. Fiecare email este descărcat și grupat după serviciu.
        //    Gmail întoarce emailurile de la cel mai nou la cel mai vechi.
        Map<String, Found> foundByService = new LinkedHashMap<>();
        for (int i = 0; messages != null && i < messages.length(); i++) {
            String id = messages.getJSONObject(i).getString("id");
            JSONObject message = new JSONObject(get(BASE_URL + "/" + id + "?format=full", token));
            collect(message, foundByService);
        }

        // 3. Câte un abonament propus pentru fiecare serviciu
        List<DetectedSubscription> result = new ArrayList<>();
        for (Found found : foundByService.values()) {
            result.add(found.toDetected());
        }
        return result;
    }

    /** Analizează un email și actualizează datele serviciului de la care a venit. */
    private void collect(JSONObject message, Map<String, Found> foundByService) {
        JSONObject payload = message.optJSONObject("payload");
        if (payload == null) {
            return;
        }
        CatalogItem item = catalog.findBySender(extractSender(header(payload, "From")));
        if (item == null) {
            return;
        }

        Found found = foundByService.get(item.getName());
        if (found == null) {
            // primul email văzut este și cel mai recent
            found = new Found(item, message.optLong("internalDate"));
            foundByService.put(item.getName(), found);
        }

        StringBuilder text = new StringBuilder();
        text.append(header(payload, "Subject")).append(' ')
                .append(message.optString("snippet")).append(' ');
        appendBodyText(payload, text);

        if (found.amount == null) {
            found.amount = AmountParser.parse(text.toString());
        }
        if (YEARLY_WORDS.matcher(text).find()) {
            found.yearly = true;
        }
    }

    /** Extrage adresa din antetul From, ex. „Netflix <info@mailer.netflix.com>”. */
    private static String extractSender(String from) {
        Matcher matcher = EMAIL_ADDRESS.matcher(from);
        return matcher.find() ? matcher.group().toLowerCase(Locale.ROOT) : "";
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

    /**
     * Adună textul din corpul emailului. Un email poate avea mai multe părți
     * (text simplu, HTML, atașamente), așa că funcția le parcurge recursiv.
     */
    private static void appendBodyText(JSONObject part, StringBuilder out) {
        if (out.length() >= MAX_TEXT_LENGTH) {
            return;
        }
        String mimeType = part.optString("mimeType");
        JSONObject body = part.optJSONObject("body");
        String data = body == null ? "" : body.optString("data");
        if (!data.isEmpty() && mimeType.startsWith("text/")) {
            try {
                // corpul vine codificat Base64 (varianta „URL safe”)
                String decoded = new String(
                        Base64.decode(data, Base64.URL_SAFE), StandardCharsets.UTF_8);
                if (mimeType.equals("text/html")) {
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

    /** Cerere HTTP GET autentificată cu token-ul OAuth. */
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

    /** Ce s-a aflat despre un serviciu din emailurile lui. */
    private static final class Found {
        final CatalogItem item;
        final long lastEmailDate;
        AmountParser.Result amount;
        boolean yearly;

        Found(CatalogItem item, long lastEmailDate) {
            this.item = item;
            this.lastEmailDate = lastEmailDate;
        }

        DetectedSubscription toDetected() {
            // fără sumă în email → prețul tipic din catalog
            double price = amount == null ? item.getPrice() : amount.amount;
            String currency = amount == null ? item.getCurrency() : amount.currency;
            String cycle = yearly ? Subscription.CYCLE_YEARLY : item.getBillingCycle();
            return new DetectedSubscription(item.getName(), price, currency, cycle,
                    item.getCategory(), lastEmailDate, amount != null);
        }
    }
}
