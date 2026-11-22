package com.example.proiectcmo.importer;

import com.example.proiectcmo.model.ImportReview;
import com.example.proiectcmo.model.ReviewItem;
import com.example.proiectcmo.model.Subscription;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

public final class ImportReviewJson {
    private ImportReviewJson() {
    }

    public static String toJson(ImportReview review) {
        try {
            JSONObject root = new JSONObject();
            root.put("newItems", items(review.newItems));
            root.put("updates", items(review.updates));
            root.put("inactive", items(review.inactive));
            root.put("toVerify", items(review.toVerify));
            JSONObject sync = new JSONObject();
            for (java.util.Map.Entry<String, Long> e : review.lastChargeSync.entrySet()) {
                sync.put(e.getKey(), e.getValue());
            }
            root.put("lastChargeSync", sync);
            return root.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ImportReview fromJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            ImportReview review = new ImportReview();
            readItems(root.optJSONArray("newItems"), review.newItems);
            readItems(root.optJSONArray("updates"), review.updates);
            readItems(root.optJSONArray("inactive"), review.inactive);
            readItems(root.optJSONArray("toVerify"), review.toVerify);
            JSONObject sync = root.optJSONObject("lastChargeSync");
            if (sync != null) {
                Iterator<String> keys = sync.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    review.lastChargeSync.put(key, sync.getLong(key));
                }
            }
            return review;
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static JSONArray items(List<ReviewItem> items) throws JSONException {
        JSONArray array = new JSONArray();
        for (ReviewItem item : items) {
            JSONObject o = new JSONObject();
            o.put("kind", item.kind.name());
            o.put("proposed", sub(item.proposed));
            o.put("previous", item.previous == null ? JSONObject.NULL : sub(item.previous));
            o.put("reason", item.reason == null ? JSONObject.NULL : item.reason);
            o.put("cancelUrl", item.cancelUrl == null ? JSONObject.NULL : item.cancelUrl);
            o.put("selected", item.selected);
            array.put(o);
        }
        return array;
    }

    private static void readItems(JSONArray array, List<ReviewItem> out) throws JSONException {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            out.add(new ReviewItem(
                    ReviewItem.Kind.valueOf(o.getString("kind")),
                    sub(o.getJSONObject("proposed")),
                    o.isNull("previous") ? null : sub(o.getJSONObject("previous")),
                    str(o, "reason"),
                    str(o, "cancelUrl"),
                    o.getBoolean("selected")));
        }
    }

    private static JSONObject sub(Subscription s) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", nul(s.getId()));
        o.put("name", nul(s.getName()));
        o.put("price", s.getPrice());
        o.put("currency", nul(s.getCurrency()));
        o.put("billingCycle", nul(s.getBillingCycle()));
        o.put("nextPaymentDate", s.getNextPaymentDate());
        o.put("category", nul(s.getCategory()));
        o.put("notifyBeforePayment", s.isNotifyBeforePayment());
        o.put("cancelUrl", nul(s.getCancelUrl()));
        o.put("source", nul(s.getSource()));
        o.put("cancelled", s.isCancelled());
        o.put("vendor", nul(s.getVendor()));
        o.put("lastChargeDate", s.getLastChargeDate());
        return o;
    }

    private static Subscription sub(JSONObject o) throws JSONException {
        Subscription s = new Subscription();
        s.setId(str(o, "id"));
        s.setName(str(o, "name"));
        s.setPrice(o.getDouble("price"));
        s.setCurrency(str(o, "currency"));
        s.setBillingCycle(str(o, "billingCycle"));
        s.setNextPaymentDate(o.getLong("nextPaymentDate"));
        s.setCategory(str(o, "category"));
        s.setNotifyBeforePayment(o.getBoolean("notifyBeforePayment"));
        s.setCancelUrl(str(o, "cancelUrl"));
        s.setSource(str(o, "source"));
        s.setCancelled(o.getBoolean("cancelled"));
        s.setVendor(str(o, "vendor"));
        s.setLastChargeDate(o.getLong("lastChargeDate"));
        return s;
    }

    private static Object nul(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static String str(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key, null);
    }
}
