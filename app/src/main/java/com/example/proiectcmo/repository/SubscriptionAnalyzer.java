package com.example.proiectcmo.repository;

import android.util.Log;

import com.example.proiectcmo.importer.AiFindingParser;
import com.example.proiectcmo.importer.AnalyzerException;
import com.example.proiectcmo.importer.AnalyzerPrompt;
import com.example.proiectcmo.importer.ImportReviewBuilder;
import com.example.proiectcmo.model.AiFinding;
import com.example.proiectcmo.model.EmailExcerpt;
import com.example.proiectcmo.model.Subscription;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.QuotaExceededException;
import com.google.firebase.ai.type.ResponseStoppedException;
import com.google.firebase.ai.type.Schema;
import com.google.firebase.ai.type.ServerException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class SubscriptionAnalyzer {
    public static final String GEMINI_MODEL = "gemini-3.8-flash";
    public static final int CHUNK_SIZE = 150;
    private static final String TAG = "SubscriptionAnalyzer";
    private static final long QUOTA_WAIT_MS = 60_000;

    private final GenerativeModelFutures model;

    public SubscriptionAnalyzer() {
        GenerationConfig config = new GenerationConfig.Builder()
                .setResponseMimeType("application/json")
                .setResponseSchema(schema())
                .build();
        model = GenerativeModelFutures.from(FirebaseAI.getInstance(GenerativeBackend.googleAI())
                .generativeModel(GEMINI_MODEL, config));
    }

    public List<AiFinding> analyze(List<EmailExcerpt> excerpts, List<Subscription> existing,
                                   boolean windowComplete) throws AnalyzerException {
        String today = LocalDate.now().toString();
        List<AiFinding> out = new ArrayList<>();
        for (int i = 0; i < excerpts.size(); i += CHUNK_SIZE) {
            List<EmailExcerpt> chunk = excerpts.subList(i, Math.min(i + CHUNK_SIZE, excerpts.size()));
            String prompt = AnalyzerPrompt.build(chunk, existing, today, windowComplete);
            Set<String> sentIds = new HashSet<>();
            for (EmailExcerpt e : chunk) {
                sentIds.add(e.id);
            }
            out.addAll(ImportReviewBuilder.requireEvidence(AiFindingParser.parse(callWithRetry(prompt)), sentIds));
        }
        return out;
    }

    private String callWithRetry(String prompt) throws AnalyzerException {
        try {
            return call(prompt);
        } catch (AnalyzerException e) {
            if (e.getKind() != AnalyzerException.Kind.QUOTA) throw e;
        }
        try {
            Thread.sleep(QUOTA_WAIT_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AnalyzerException(AnalyzerException.Kind.NETWORK, "Întrerupt", ie);
        }
        return call(prompt);
    }

    private String call(String prompt) throws AnalyzerException {
        try {
            GenerateContentResponse r = model.generateContent(new Content.Builder().addText(prompt).build()).get();
            String text = r.getText();
            if (text == null) {
                throw new AnalyzerException(AnalyzerException.Kind.INVALID_RESPONSE, "Răspuns gol", null);
            }
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AnalyzerException(AnalyzerException.Kind.NETWORK, "Întrerupt", e);
        } catch (ExecutionException e) {
            throw map(e.getCause() != null ? e.getCause() : e);
        }
    }

    private static AnalyzerException map(Throwable t) {
        String msg = String.valueOf(t.getMessage()).toLowerCase();
        AnalyzerException.Kind kind;
        if (t instanceof QuotaExceededException || msg.contains("429") || msg.contains("quota")) {
            kind = AnalyzerException.Kind.QUOTA;
        } else if (msg.contains("app check") || msg.contains("appcheck") || msg.contains("firebase-app-check")) {
            kind = AnalyzerException.Kind.APP_CHECK;
        } else if (t instanceof IOException || t.getCause() instanceof IOException) {
            kind = AnalyzerException.Kind.NETWORK;
        } else {
            kind = AnalyzerException.Kind.INVALID_RESPONSE;
        }
        StringBuilder chain = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            chain.append(" > ").append(c.getClass().getName());
        }
        if (t instanceof ResponseStoppedException
                && !((ResponseStoppedException) t).getResponse().getCandidates().isEmpty()) {
            chain.append(" finishReason=")
                    .append(((ResponseStoppedException) t).getResponse().getCandidates().get(0).getFinishReason().getName());
        }
        if (t instanceof ServerException) {
            String detail = String.valueOf(t.getMessage()).replaceAll("\\s+", " ");
            chain.append(" : ").append(detail.substring(0, Math.min(detail.length(), 300)));
        }
        Log.w(TAG, "Eroare Gemini:" + chain);
        return new AnalyzerException(kind, t.getMessage(), t);
    }

    private static Schema schema() {
        Map<String, Schema> f = new LinkedHashMap<>();
        f.put("name", Schema.str());
        f.put("vendor", Schema.str());
        f.put("price", Schema.numDouble());
        f.put("currency", Schema.str());
        f.put("billingCycle", Schema.enumeration(Arrays.asList("MONTHLY", "YEARLY", "WEEKLY", "UNKNOWN")));
        f.put("lastChargeDate", Schema.str());
        f.put("nextPaymentDate", Schema.str());
        f.put("status", Schema.enumeration(Arrays.asList("ACTIVE", "CANCELED", "LAPSED", "UNCERTAIN")));
        f.put("evidenceIds", Schema.array(Schema.str()));
        f.put("matchesExistingId", Schema.str());
        f.put("reason", Schema.str());
        Schema item = Schema.obj(f, Arrays.asList("nextPaymentDate", "matchesExistingId"));
        Map<String, Schema> root = new LinkedHashMap<>();
        root.put("subscriptions", Schema.array(item));
        return Schema.obj(root);
    }
}
