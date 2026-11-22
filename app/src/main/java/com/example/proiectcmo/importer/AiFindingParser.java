package com.example.proiectcmo.importer;

import com.example.proiectcmo.model.AiFinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class AiFindingParser {
    private AiFindingParser() {
    }

    public static List<AiFinding> parse(String json) throws AnalyzerException {
        try {
            JSONArray items = new JSONObject(json).getJSONArray("subscriptions");
            List<AiFinding> out = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                AiFinding f = new AiFinding();
                f.name = text(o, "name");
                f.vendor = text(o, "vendor");
                f.currency = text(o, "currency");
                f.billingCycle = text(o, "billingCycle");
                f.matchesExistingId = text(o, "matchesExistingId");
                f.reason = text(o, "reason");
                f.price = o.optDouble("price", 0);
                f.lastChargeDate = date(text(o, "lastChargeDate"));
                f.nextPaymentDate = date(text(o, "nextPaymentDate"));
                String status = text(o, "status");
                f.status = status == null ? AiFinding.Status.UNCERTAIN : AiFinding.Status.valueOf(status);
                JSONArray ev = o.optJSONArray("evidenceIds");
                for (int j = 0; ev != null && j < ev.length(); j++) {
                    f.evidenceIds.add(ev.getString(j));
                }
                out.add(f);
            }
            return out;
        } catch (JSONException | IllegalArgumentException | DateTimeException e) {
            throw new AnalyzerException(AnalyzerException.Kind.INVALID_RESPONSE, "Răspuns AI invalid: " + e.getClass().getSimpleName(), null);
        }
    }

    private static String text(JSONObject o, String key) {
        if (o.isNull(key)) return null;
        String s = o.optString(key, "");
        return s.isEmpty() ? null : s;
    }

    private static long date(String iso) {
        if (iso == null) return 0;
        return LocalDate.parse(iso.length() > 10 ? iso.substring(0, 10) : iso)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
