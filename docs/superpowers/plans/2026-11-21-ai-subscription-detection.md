# Detectarea abonamentelor cu Gemini: plan de implementare

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Importul din Gmail găsește orice abonament activ, cu ajutorul Gemini (gratuit, prin Firebase AI Logic). Corectează lista existentă după confirmarea utilizatorului și rulează automat în fundal cel mult o dată pe zi.

**Architecture:** Telefonul caută chitanțele în Gmail și extrage fragmente scurte. Le trimite la Gemini prin Firebase AI Logic, protejat de App Check, în apeluri de câte 150. Răspunsul JSON e transformat pe telefon, de clase pure testabile pe JVM (pachetul nou `importer`), într-o propunere în patru grupuri. Propunerea se aplică într-un `WriteBatch` după confirmare. Verificarea zilnică e un `OneTimeWorkRequest` WorkManager unic, pornit la deschiderea aplicației.

**Tech Stack:** Java 11, Android SDK 26–37, Firebase BoM 35.2.0 (Auth, Firestore, AI Logic, App Check), WorkManager 2.9.1, Gmail REST API, JUnit 4 + `org.json` pentru teste.

**Spec:** `docs/superpowers/specs/2026-11-21-ai-subscription-detection-design.md`

## Global Constraints

- Java + XML, MVVM (UI → ViewModel → repository), toate textele vizibile în `res/values/strings.xml`, în română.
- **Fără comentarii în cod.** Autorul le-a eliminat (commit `3a376ca`); codul nou nu adaugă altele.
- Model Gemini: constanta `GEMINI_MODEL = "gemini-3.5-flash-lite"`, într-un singur loc.
- Proiectul rămâne pe planul Spark: furnizorul `GenerativeBackend.googleAI()`, fără Vertex AI și fără cont de facturare.
- Limite: maximum **600** de emailuri per import complet, **150** de fragmente per apel Gemini, **1.200** de caractere per fragment, **5** analize pe zi per utilizator, fereastra Gmail de **400** de zile.
- Toleranțe pentru „fără plăți recente”: **7** zile la lunar, **30** de zile la anual.
- Nu se șterge niciodată un abonament. Inactiv înseamnă `cancelled = true`.
- Firestore: datele noi stau sub `users/{uid}/meta/` (documentele `usage`, `sync`, `pendingReview`), acoperite de regulile existente.
- Textul de confidențialitate (verbatim, string `import_privacy`): „Căutăm chitanțele în Gmail pe telefon și trimitem la Google Gemini doar fragmente scurte din ele (expeditor, subiect, sume, date), pentru analiză. Pe varianta gratuită, Google poate folosi aceste fragmente pentru a-și îmbunătăți serviciile și le pot citi evaluatori umani. Emailurile întregi nu pleacă de pe telefon.”
- Comenzile Gradle se rulează cu `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` din rădăcina repo-ului. Dacă build-ul dă „defined multiple times” (duplicate iCloud), se șterge `app/build`.
- Mesajele de commit sunt în română, fără rânduri de atribuire.

## Review Focus

1. **Cota Gemini se termină la al 3-lea din 4 apeluri.** Se așteaptă: niciun abonament modificat, mesaj „Limita gratuită de analize a fost atinsă, încearcă mai târziu”, iar `lastScannedEmailDate` nu avansează, ca emailurile să fie reanalizate data viitoare. Testul e în Task 4.
2. **Monedă nesuportată (GBP) sau ciclu săptămânal.** Se așteaptă: rezultatul apare doar la „De verificat” și nu ajunge niciodată în Firestore. Testul e în Task 5.
3. **Accesul la Gmail revocat în timp ce rulează verificarea din fundal.** Se așteaptă: `needsReconnect = true`, banner „Reconectează Gmail”, iar worker-ul se termină cu `Result.success()`, fără crash. Testul e în Task 8 (politica), plus verificare manuală.
4. **AI-ul întoarce un `matchesExistingId` care nu există sau al cărui nume nu se potrivește.** Se așteaptă: rezultatul e tratat ca abonament nou și niciun abonament existent nu e modificat. Testul e în Task 5.
5. **Același abonament găsit în două apeluri diferite.** Se așteaptă: un singur rând, cu cea mai recentă `lastChargeDate`. Testul e în Task 5.

---

### Task 1: Dependențe, App Check și teste JSON pe JVM

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/proiectcmo/SubTrackerApp.java`
- Modify: `app/src/main/AndroidManifest.xml` (`android:name=".SubTrackerApp"` pe `<application>`)

**Interfaces:**
- Produces: `SubTrackerApp extends Application`. În `onCreate` inițializează Firebase App Check cu `DebugAppCheckProviderFactory` când `BuildConfig.DEBUG`, altfel cu `PlayIntegrityAppCheckProviderFactory`.

- [ ] **Step 1:** Urcă `firebase-bom` la `35.2.0`. Adaugă `com.google.firebase:firebase-ai`, `com.google.firebase:firebase-appcheck-playintegrity`, `debugImplementation("com.google.firebase:firebase-appcheck-debug")`, `com.google.guava:guava:31.0.1-android`, `testImplementation("org.json:json:20231013")`. Activează `buildFeatures { buildConfig = true }`. Pune versiunile noi în `libs.versions.toml`, ca restul.
- [ ] **Step 2:** Creează `SubTrackerApp` și înregistrează-l în manifest.
- [ ] **Step 3:** Run `./gradlew testDebugUnitTest installDebug`. Expected: `BUILD SUCCESSFUL`, cele 6 teste existente trec, aplicația pornește, iar Logcat afișează tokenul de debug App Check (`Enter this debug secret into the allow list`).
- [ ] **Step 4:** Commit: `Dependențe Firebase AI Logic și App Check`.

### Task 2: Câmpuri noi pe abonament, potrivirea numelor, catalog simplificat

**Files:**
- Modify: `model/Subscription.java`, `data/SubscriptionCatalog.java`
- Create: `importer/NameMatcher.java`
- Test: `app/src/test/java/com/example/proiectcmo/importer/NameMatcherTest.java`

**Interfaces:**
- Produces:
  - `Subscription`: `String getVendor()/setVendor(String)`, `long getLastChargeDate()/setLastChargeDate(long)` (0 = necunoscut), plus copy constructor `Subscription(Subscription other)`, care copiază toate câmpurile, inclusiv `id`.
  - `NameMatcher.normalize(String name) -> String`: litere mici, fără diacritice (`Normalizer` NFD), fără cuvintele `premium`, `plus`, `pro`, `+`, fără spații în plus.
  - `NameMatcher.matches(String a, String b) -> boolean`: adevărat dacă unul dintre numele normalizate îl conține pe celălalt și niciunul nu e gol.
  - `NameMatcher.exact(String a, String b) -> boolean`: numele normalizate sunt egale.
  - `SubscriptionCatalog.findMatching(String name) -> CatalogItem` (sau null), folosind `NameMatcher.matches`.

- [ ] **Step 1: Testele**

```java
@Test public void normalizeRemovesDiacriticsAndTierWords() {
    assertEquals("icloud", NameMatcher.normalize("iCloud+"));
    assertEquals("youtube", NameMatcher.normalize("YouTube Premium"));
    assertEquals("claude", NameMatcher.normalize("Claude Pro"));
    assertEquals("televiziune", NameMatcher.normalize("Televiziune"));
}
@Test public void matchesIsContainmentAfterNormalize() {
    assertTrue(NameMatcher.matches("Apple iCloud+", "iCloud+ 50 GB"));
    assertFalse(NameMatcher.matches("Netflix", "Spotify"));
    assertFalse(NameMatcher.matches("", "Netflix"));
}
@Test public void exactNeedsEqualNormalizedNames() {
    assertTrue(NameMatcher.exact("Claude Pro", "claude"));
    assertFalse(NameMatcher.exact("Apple iCloud+", "iCloud+ 50 GB"));
}
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*NameMatcherTest'`. Expected: FAIL (clasa nu există).
- [ ] **Step 3:** Implementează `NameMatcher`, câmpurile pe `Subscription` și `findMatching`.
- [ ] **Step 4:** Run același test, apoi `./gradlew testDebugUnitTest`. Expected: PASS (toate testele).
- [ ] **Step 5:** Commit: `Câmpurile vendor și lastChargeDate, potrivirea numelor`.

### Task 3: Fragmentele de email din Gmail

**Files:**
- Create: `model/EmailExcerpt.java`, `importer/ExcerptBuilder.java`, `importer/GmailQuery.java`
- Modify: `repository/GmailRepository.java` (se adaugă `fetchExcerpts`)
- Test: `app/src/test/java/com/example/proiectcmo/importer/ExcerptBuilderTest.java`, `GmailQueryTest.java`

**Interfaces:**
- Produces:
  - `EmailExcerpt` (final, câmpuri publice imuabile), cu constructorul `EmailExcerpt(String id, long date, String from, String subject, String text)`.
  - `ExcerptBuilder.MAX_CHARS = 1200`; `ExcerptBuilder.build(String id, long date, String from, String subject, String bodyText) -> EmailExcerpt` (null dacă nu rămâne nicio linie relevantă). O linie e relevantă dacă `AmountParser.parse(line) != null`, ori conține (fără diferență de majuscule) un cuvânt din `GmailQuery.KEYWORDS`, ori un tipar de dată (`\d{1,2}[./ ]\w+[./ ]\d{2,4}` sau ISO). Se păstrează și linia de dinainte, iar textul se taie la `MAX_CHARS`.
  - `GmailQuery.KEYWORDS` (lista din spec 7.1); `GmailQuery.build(long newerThanMillis) -> String`: cu `0`, `newer_than:400d (...)`; altfel, `after:<secunde epoch> (...)`.
  - `GmailRepository.fetchExcerpts(String token, long newerThanMillis, int max) -> ExcerptBatch` (blocant; se apelează de pe un fir de fundal; aruncă `IOException`). `ExcerptBatch { List<EmailExcerpt> excerpts; boolean windowComplete; long newestEmailDate }`, unde `windowComplete = numărul de mesaje listate < max`. Păstrează conversia HTML → text și eliminarea `<style>` existente. Paginarea folosește `pageToken`; mesajele se citesc în paralel pe `searchPool`.
  - Codul vechi (`findSubscriptions`, `searchService`, `Callback`, `MESSAGES_PER_SERVICE`) rămâne neatins până la Task 7, ca proiectul să compileze între timp.

- [ ] **Step 1: Testele**

```java
@Test public void appleReceiptKeepsBothProductLines() {
    String body = "Chitanța ta Apple\nApple Music Individual\nLunar 25,99 lei\niCloud+ 50 GB\nLunar 14,99 lei\nTotal 40,98 lei";
    EmailExcerpt e = ExcerptBuilder.build("m1", 1L, "no_reply@email.apple.com", "Chitanța ta de la Apple", body);
    assertTrue(e.text.contains("Apple Music Individual"));
    assertTrue(e.text.contains("14,99 lei"));
}
@Test public void cancellationEmailIsKept() {
    EmailExcerpt e = ExcerptBuilder.build("m2", 1L, "no-reply@youtube.com", "Abonamentul tău a fost anulat", "Your YouTube Premium membership has been canceled.");
    assertNotNull(e);
}
@Test public void newsletterWithoutSignalsIsDropped() {
    assertNull(ExcerptBuilder.build("m3", 1L, "news@site.com", "Noutăți", "Citește cele mai noi articole de pe blog."));
}
@Test public void textIsCappedAt1200Chars() {
    String body = String.join("\n", java.util.Collections.nCopies(200, "Plata lunară 9,99 lei"));
    assertTrue(ExcerptBuilder.build("m4", 1L, "a@b.c", "x", body).text.length() <= 1200);
}
@Test public void fullQueryUses400Days() {
    assertTrue(GmailQuery.build(0).startsWith("newer_than:400d ("));
}
@Test public void incrementalQueryUsesEpochSeconds() {
    assertTrue(GmailQuery.build(1_700_000_000_000L).startsWith("after:1700000000 ("));
}
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*ExcerptBuilderTest' --tests '*GmailQueryTest'`. Expected: FAIL.
- [ ] **Step 3:** Implementează `EmailExcerpt`, `GmailQuery`, `ExcerptBuilder` și metoda `GmailRepository.fetchExcerpts`.
- [ ] **Step 4:** Run testele, apoi `./gradlew assembleDebug`. Expected: PASS și `BUILD SUCCESSFUL`.
- [ ] **Step 5:** Commit: `Fragmente de chitanțe din Gmail, fără limita catalogului`.

### Task 4: Analiza cu Gemini

**Files:**
- Create: `model/AiFinding.java`, `importer/AnalyzerPrompt.java`, `importer/AiFindingParser.java`, `importer/AnalyzerException.java`, `repository/SubscriptionAnalyzer.java`
- Test: `app/src/test/java/com/example/proiectcmo/importer/AiFindingParserTest.java`, `AnalyzerPromptTest.java`

**Interfaces:**
- Consumes: `EmailExcerpt` (Task 3); `Subscription.getLastChargeDate()` (Task 2).
- Produces:
  - `AiFinding` (câmpuri publice): `String name, vendor, currency, billingCycle, matchesExistingId, reason; double price; long lastChargeDate, nextPaymentDate` (0 = lipsă); `Status status`; `List<String> evidenceIds`. `enum Status { ACTIVE, CANCELED, LAPSED, UNCERTAIN }`.
  - `AnalyzerException extends Exception` cu `enum Kind { QUOTA, APP_CHECK, NETWORK, BUSY, INVALID_RESPONSE }` și `Kind getKind()`.
  - `AnalyzerPrompt.build(List<EmailExcerpt> excerpts, List<Subscription> existing, String todayIso, boolean windowComplete) -> String`. Conține regulile din spec 7.2 (plăți unice, perioade de probă, despărțirea chitanțelor Apple/Google Play, toleranțele 7/30, fără prețuri inventate). Când `windowComplete` e `false`, conține textul `Nu folosi statusul LAPSED`. Fragmentele apar ca JSON (`id`, `date` ISO, `from`, `subject`, `text`), iar abonamentele existente ca JSON (`id`, `name`, `price`, `currency`, `billingCycle`, `source`, `cancelled`, `lastChargeDate` ISO).
  - `AiFindingParser.parse(String json) -> List<AiFinding>`. Acceptă `{"subscriptions":[...]}`, transformă datele ISO în milisecunde la miezul nopții local, iar câmpurile lipsă devin 0, null sau listă goală. Pentru JSON invalid aruncă `AnalyzerException(INVALID_RESPONSE)`.
  - `SubscriptionAnalyzer.CHUNK_SIZE = 150`; `SubscriptionAnalyzer.analyze(List<EmailExcerpt>, List<Subscription> existing, boolean windowComplete) -> List<AiFinding>` (blocant). Împarte fragmentele în bucăți de 150 și le trimite pe rând, cu `GenerativeModelFutures` și `GenerationConfig` (`responseMimeType = "application/json"`, `responseSchema` construit cu `Schema.obj/array/str/numDouble/enumeration` după câmpurile `AiFinding`). La o eroare de cotă așteaptă 60 de secunde și reîncearcă o dată, apoi aruncă `QUOTA`. Erorile App Check devin `APP_CHECK`, cele de rețea `NETWORK`. Întoarce lista concatenată, fără deduplicare (aceea se face în Task 5).

- [ ] **Step 1: Testele**

```java
@Test public void parsesFullFinding() throws Exception {
    String json = "{\"subscriptions\":[{\"name\":\"Calm\",\"vendor\":\"Apple\",\"price\":49.99,\"currency\":\"RON\",\"billingCycle\":\"MONTHLY\",\"lastChargeDate\":\"2026-11-03\",\"nextPaymentDate\":\"2026-12-03\",\"status\":\"ACTIVE\",\"evidenceIds\":[\"m1\"],\"matchesExistingId\":\"\",\"reason\":\"chitanță din 3 nov.\"}]}";
    AiFinding f = AiFindingParser.parse(json).get(0);
    assertEquals("Calm", f.name);
    assertEquals(49.99, f.price, 0.001);
    assertEquals(AiFinding.Status.ACTIVE, f.status);
    assertTrue(f.nextPaymentDate > f.lastChargeDate);
}
@Test public void missingOptionalFieldsBecomeEmpty() throws Exception {
    AiFinding f = AiFindingParser.parse("{\"subscriptions\":[{\"name\":\"X\",\"status\":\"UNCERTAIN\"}]}").get(0);
    assertEquals(0, f.nextPaymentDate);
    assertNull(f.matchesExistingId);
    assertTrue(f.evidenceIds.isEmpty());
}
@Test(expected = AnalyzerException.class) public void invalidJsonThrows() throws Exception {
    AiFindingParser.parse("nu e json");
}
@Test public void promptForbidsLapsedWhenWindowIncomplete() {
    String p = AnalyzerPrompt.build(java.util.Collections.emptyList(), java.util.Collections.emptyList(), "2026-11-21", false);
    assertTrue(p.contains("Nu folosi statusul LAPSED"));
}
@Test public void promptIncludesExcerptIdsAndExistingIds() {
    EmailExcerpt e = new EmailExcerpt("m42", 1L, "a@b.c", "s", "9,99 lei");
    Subscription s = new Subscription("Netflix", 17.98, "EUR", "MONTHLY", 1L, "STREAMING");
    s.setId("sub7");
    String p = AnalyzerPrompt.build(java.util.List.of(e), java.util.List.of(s), "2026-11-21", true);
    assertTrue(p.contains("m42"));
    assertTrue(p.contains("sub7"));
    assertFalse(p.contains("Nu folosi statusul LAPSED"));
}
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*AiFindingParserTest' --tests '*AnalyzerPromptTest'`. Expected: FAIL.
- [ ] **Step 3:** Implementează `AiFinding`, `AnalyzerException`, `AnalyzerPrompt`, `AiFindingParser`, apoi `SubscriptionAnalyzer`. Pentru `Schema.obj`, verifică semnătura din SDK (pagina din documentație folosește `Schema.obj(Map, List)`) și compilează.
- [ ] **Step 4:** Run testele. Expected: PASS.
- [ ] **Step 5:** Commit: `Analiza fragmentelor cu Gemini, cu răspuns JSON structurat`.

### Task 5: Construirea propunerii (cele patru grupuri)

**Files:**
- Create: `model/ImportReview.java`, `model/ReviewItem.java`, `importer/ImportReviewBuilder.java`, `importer/LapseChecker.java`
- Test: `app/src/test/java/com/example/proiectcmo/importer/ImportReviewBuilderTest.java`, `LapseCheckerTest.java`

**Interfaces:**
- Consumes: `AiFinding` (Task 4), `NameMatcher` și câmpurile noi pe `Subscription` (Task 2), `CatalogItem.getCancelUrl()/getCategory()`.
- Produces:
  - `ReviewItem`: `enum Kind { ADD, UPDATE, REACTIVATE, DEACTIVATE, INFO }`; câmpuri `Kind kind; Subscription proposed` (cu `id` pentru tot ce nu e ADD/INFO); `Subscription previous` (null la ADD/INFO); `String reason; String cancelUrl; boolean selected`.
  - `ImportReview`: `List<ReviewItem> newItems, updates, inactive, toVerify`; `Map<String, Long> lastChargeSync` (id → noua `lastChargeDate`, aplicat fără confirmare); `boolean isEmpty()` (toate cele patru liste goale); `List<ReviewItem> selected()`.
  - `LapseChecker.isLapsed(Subscription s, long today) -> boolean`: adevărat doar dacă `source == GMAIL`, `!cancelled`, `lastChargeDate > 0` și `today > lastChargeDate + ciclu + toleranță` (lunar: +1 lună +7 zile; anual: +1 an +30 de zile, cu `Calendar`).
  - `ImportReviewBuilder.build(List<AiFinding> findings, List<Subscription> existing, Function<String, CatalogItem> catalog, long today) -> ImportReview`. Regulile, în ordine:
    1. Elimină `UNCERTAIN`. Unește rezultatele cu același `matchesExistingId` valid sau, altfel, cu nume `NameMatcher.matches` și același vendor, păstrând pe cel cu `lastChargeDate` maxim.
    2. Potrivirea: `matchesExistingId` e acceptat doar dacă id-ul există și `NameMatcher.matches` (sau sursa e GMAIL și vendor-ul e egal). Altfel, rezultatul e tratat ca nou.
    3. Rezultat nou `ACTIVE`: dacă ciclul nu e `MONTHLY`/`YEARLY`, moneda nu e RON/EUR/USD sau `nextPaymentDate == 0`, devine `INFO` în `toVerify` (`selected = false`). Altfel devine `ADD` în `newItems` (`selected = true`, `source = GMAIL`, `vendor`, `lastChargeDate`, categoria din catalog sau `OTHER`).
    4. Existent cu rezultat `ACTIVE`: dacă e `cancelled`, devine `REACTIVATE` în `updates` (`selected = true`). Dacă prețul diferă cu mai mult de 0,01 sau diferă moneda/ciclul, și (sursa e GMAIL sau `NameMatcher.exact`), devine `UPDATE` în `updates` (`selected = true`). Dacă diferă doar `lastChargeDate`, se pune în `lastChargeSync`.
    5. Existent cu `CANCELED`: devine `DEACTIVATE` în `inactive` (`selected = true`), pentru orice sursă.
    6. Existent cu `LAPSED`: devine `DEACTIVATE` (`selected = false`) doar dacă sursa e GMAIL. Pentru MANUAL/CATALOG se ignoră.
    7. Existenții GMAIL fără niciun rezultat, pentru care `LapseChecker.isLapsed`, devin `DEACTIVATE` (`selected = false`, cu motivul „nicio plată din <dată>”).
    8. `cancelUrl`: din catalog (`NameMatcher.matches`) → dacă vendor-ul conține „Apple”, `https://apps.apple.com/account/subscriptions` → dacă conține „Google Play”, `https://play.google.com/store/account/subscriptions` → altfel `https://` + vendor (dacă vendor-ul arată a domeniu).

- [ ] **Step 1: Testele** (un test pentru fiecare regulă; în helper-ul de test, `finding(...)` și `existing(...)` construiesc obiecte simple, iar catalogul de test conține doar „Netflix”).

```java
@Test public void activeAppleAppBecomesAddWithAppleCancelUrl() { /* ACTIVE, vendor "Apple", MONTHLY, RON, next>0 → newItems.size()==1, cancelUrl apps.apple.com, category OTHER, selected */ }
@Test public void weeklyOrForeignCurrencyGoesToVerifyOnly() { /* WEEKLY RON și MONTHLY GBP → toVerify.size()==2, newItems empty, kind INFO, !selected */ }
@Test public void priceChangeOnGmailSubscriptionIsUpdate() { /* existent GMAIL 99.99 RON, finding 109.99 RON → updates[0].kind==UPDATE, proposed.getPrice()==109.99, previous.getPrice()==99.99 */ }
@Test public void cancelledSubscriptionWithNewChargeIsReactivated() { /* existent cancelled=true, finding ACTIVE → updates[0].kind==REACTIVATE */ }
@Test public void explicitCancelDeactivatesEvenManual() { /* existent MANUAL, finding CANCELED → inactive[0].selected==true */ }
@Test public void lapsedIgnoredForManualAndUncheckedForGmail() { /* MANUAL LAPSED → inactive empty; GMAIL LAPSED → inactive[0].selected==false */ }
@Test public void unknownMatchIdIsTreatedAsNew() { /* matchesExistingId "nu-exista" → newItems.size()==1, updates/inactive empty */ }
@Test public void mismatchedNameDoesNotTouchExisting() { /* matchesExistingId = id-ul lui "Netflix", finding name "Spotify" → newItems.size()==1, updates empty */ }
@Test public void duplicateAcrossChunksIsMergedKeepingLatestCharge() { /* două findings "Calm"/Apple cu lastChargeDate 1 nov și 3 nov → newItems.size()==1, proposed.getLastChargeDate()==3 nov */ }
@Test public void unchangedActiveOnlySyncsLastCharge() { /* existent GMAIL, același preț, lastCharge mai nou → review.isEmpty(), lastChargeSync.get(id)==noua dată */ }
@Test public void gmailSubscriptionWithoutFindingButLapsedIsProposedUnchecked() { /* fără findings, existent GMAIL cu lastCharge acum 50 de zile, lunar → inactive[0].selected==false */ }
@Test public void uncertainIsIgnored() { /* UNCERTAIN → review.isEmpty() */ }
```

```java
@Test public void monthlyLapsesAfterOneMonthPlusSevenDays() {
    // lastCharge = 1 oct. 2026; today = 8 nov. → false; today = 9 nov. → true
}
@Test public void yearlyToleranceIsThirtyDays() { /* lastCharge 1 ian. 2026; 31 ian. 2027 → false; 1 feb. 2027 → true */ }
@Test public void manualOrCancelledNeverLapse() { /* source MANUAL → false; cancelled → false; lastChargeDate 0 → false */ }
```

Implementatorul completează corpurile după comentarii, cu exact valorile din ele.

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*ImportReviewBuilderTest' --tests '*LapseCheckerTest'`. Expected: FAIL.
- [ ] **Step 3:** Implementează `ReviewItem`, `ImportReview`, `LapseChecker`, `ImportReviewBuilder`, după regulile de mai sus.
- [ ] **Step 4:** Run testele. Expected: PASS (15 teste noi).
- [ ] **Step 5:** Commit: `Propunerea de import: noi, actualizări, inactive, de verificat`.

### Task 6: Salvarea stării și aplicarea propunerii

**Files:**
- Create: `importer/ImportReviewJson.java`, `repository/ImportStateRepository.java`
- Modify: `repository/SubscriptionRepository.java`
- Test: `app/src/test/java/com/example/proiectcmo/importer/ImportReviewJsonTest.java`

**Interfaces:**
- Consumes: `ImportReview`, `ReviewItem` (Task 5).
- Produces:
  - `ImportReviewJson.toJson(ImportReview) -> String`; `ImportReviewJson.fromJson(String) -> ImportReview`. Serializează toate câmpurile, inclusiv `Subscription` (id, name, price, currency, billingCycle, nextPaymentDate, category, notifyBeforePayment, cancelUrl, source, cancelled, vendor, lastChargeDate).
  - `ImportStateRepository` (toate întorc `com.google.android.gms.tasks.Task`, ca worker-ul să poată folosi `Tasks.await`):
    - `Task<Boolean> tryConsumeAnalysis()`: tranzacție pe `users/{uid}/meta/usage` `{day: "yyyy-MM-dd", count}`; întoarce `false` dacă `count >= 5` în ziua curentă, altfel incrementează și întoarce `true`.
    - `Task<SyncState> getSync()` și `Task<Void> updateSync(SyncState)`. `SyncState` e o clasă internă statică: `long lastAutoCheck, lastScannedEmailDate; boolean needsReconnect`, pe `users/{uid}/meta/sync`.
    - `Task<Void> savePendingReview(ImportReview)` (câmpul `json` din `users/{uid}/meta/pendingReview`), `LiveData<ImportReview> observePendingReview()` (null când lipsește), `Task<Void> clearPendingReview()`.
  - `SubscriptionRepository.applyReview(List<ReviewItem> selected, Map<String, Long> lastChargeSync, OperationCallback)`: un singur `WriteBatch`. ADD înseamnă `set` pe un document nou. UPDATE, REACTIVATE și DEACTIVATE înseamnă `set` cu `proposed` pe id-ul existent. INFO se ignoră. Fiecare intrare din `lastChargeSync` e un `update("lastChargeDate", …)`.
  - `SubscriptionRepository.syncLastCharges(Map<String, Long>) -> Task<Void>`, pentru cazul fără nimic de confirmat.

- [ ] **Step 1: Testul**

```java
@Test public void roundTripKeepsGroupsSelectionAndFields() {
    // review cu câte un item în fiecare grup și o intrare în lastChargeSync
    ImportReview back = ImportReviewJson.fromJson(ImportReviewJson.toJson(review));
    assertEquals(1, back.newItems.size());
    assertEquals(ReviewItem.Kind.DEACTIVATE, back.inactive.get(0).kind);
    assertFalse(back.inactive.get(0).selected);
    assertEquals("Apple", back.newItems.get(0).proposed.getVendor());
    assertEquals(99.99, back.updates.get(0).previous.getPrice(), 0.001);
    assertEquals(review.lastChargeSync, back.lastChargeSync);
}
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*ImportReviewJsonTest'`. Expected: FAIL.
- [ ] **Step 3:** Implementează `ImportReviewJson`, `ImportStateRepository` și cele două metode noi din `SubscriptionRepository`.
- [ ] **Step 4:** Run testul. Expected: PASS.
- [ ] **Step 5:** Commit: `Salvarea propunerii, limita zilnică și aplicarea într-un singur batch`.

### Task 7: Ecranul „Rezultatul importului” și importul manual

**Files:**
- Modify (rescriere): `ui/gmail/GmailImportActivity.java`, `viewmodel/GmailImportViewModel.java`, `res/layout/activity_gmail_import.xml`, `res/values/strings.xml`
- Create: `ui/gmail/ImportReviewAdapter.java`, `res/layout/item_review.xml`, `res/layout/item_review_header.xml`
- Delete: `model/DetectedSubscription.java`, `ui/gmail/GmailFoundAdapter.java`, `res/layout/item_gmail_found.xml`, codul vechi din `GmailRepository` (`findSubscriptions`, `searchService`, `Callback`, `MESSAGES_PER_SERVICE`, `YEARLY_WORDS`) și stringurile `gmail_found_*`, `gmail_none_found`, `gmail_all_existing`, `gmail_item_meta`, dacă nu mai sunt folosite
- Modify: `model/CatalogItem.java`, `data/SubscriptionCatalog.java`, `assets/catalog.json` (fără `domains` și `keyword`; constructorul `CatalogItem(name, price, currency, billingCycle, category, cancelUrl)`)

**Interfaces:**
- Consumes: `GmailRepository.fetchExcerpts` (Task 3), `SubscriptionAnalyzer.analyze` (Task 4), `ImportReviewBuilder.build` (Task 5), `ImportStateRepository`, `SubscriptionRepository.applyReview/syncLastCharges`, `SubscriptionRepository.getAllOnce` (existent).
- Produces:
  - `GmailImportActivity.EXTRA_OPEN_PENDING = "open_pending"`: dacă e `true`, ecranul deschide direct propunerea salvată, fără Gmail.
  - `GmailImportViewModel`: `enum Status { INTRO, LOADING, REVIEW, MESSAGE }`; `startManualImport(String token)`; `openPendingReview()`; `apply()`; `ignore()`; `LiveData<ImportReview> getReview()`; `LiveData<Integer> getProgressText()` (resurse: „Citesc emailurile…”, „Analizez cu Gemini (2 din 4)…”).
  - Importul manual, pe fundal: `tryConsumeAnalysis` (la `false`, mesajul `import_limit_reached`) → `fetchExcerpts(token, 0, 600)` → `analyze` → `build` → afișează propunerea. Dacă propunerea e goală, apar mesajul `import_nothing_new` și `syncLastCharges`. La final, `updateSync(lastAutoCheck = acum, lastScannedEmailDate = newestEmailDate, needsReconnect = false)`. Fiecare `AnalyzerException.Kind` are mesajul lui în `strings.xml`. La orice eroare nu se scrie nimic.
  - `apply()` cheamă `applyReview(review.selected(), review.lastChargeSync, …)`, apoi `clearPendingReview()`, apoi închide ecranul cu un Toast. `ignore()` cheamă `syncLastCharges`, apoi `clearPendingReview()`, apoi închide.
  - Layout-ul: panoul de introducere păstrează structura actuală, cu textul `import_privacy` (verbatim, din Global Constraints). Panoul de rezultat e o listă cu patru antete („Abonamente noi”, „Actualizări”, „Nu mai sunt active”, „De verificat”), plus butoanele „Aplică selecția (N)” și „Ignoră”. Rândurile INFO n-au bifă și au butonul „Anulează”, care deschide `cancelUrl` prin `CancelLinks.openOrExplain`. Rândurile UPDATE arată „vechi → nou”, iar DEACTIVATE arată „marchează ca anulat”. Fiecare rând arată și `reason`.

- [ ] **Step 1:** Rescrie ViewModel-ul, activitatea, adaptorul și layout-urile, cu stilurile existente (`Widget.SubTracker.*`, `item_subscription` ca model vizual).
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest installDebug`. Expected: `BUILD SUCCESSFUL`, toate testele trec.
- [ ] **Step 3: Verificare manuală pe emulator** (după Task 9, pașii 1–3, din consola Firebase): meniu → Import din Gmail → Conectează. Expected: apare progresul „Analizez cu Gemini (n din m)” și propunerea în patru grupuri. „Aplică” modifică lista de pe Acasă exact după bife, iar un al doilea import în aceeași zi nu repropune ce s-a aplicat.
- [ ] **Step 4:** Commit: `Ecranul Rezultatul importului și importul manual cu Gemini`.

### Task 8: Verificarea automată zilnică și bannerele

**Files:**
- Create: `importer/AutoImportPolicy.java`, `importer/AutoImportWorker.java`, `importer/AutoImportScheduler.java`
- Modify: `notifications/NotificationHelper.java`, `ui/main/MainActivity.java`, `res/layout/activity_main.xml`, `res/values/strings.xml`
- Test: `app/src/test/java/com/example/proiectcmo/importer/AutoImportPolicyTest.java`

**Interfaces:**
- Consumes: tot ce produc Task-urile 3–6; `GmailImportActivity.EXTRA_OPEN_PENDING` (Task 7).
- Produces:
  - `AutoImportPolicy.DAY_MS = 86_400_000L`; `AutoImportPolicy.shouldRun(long lastAutoCheck, long now) -> boolean` (adevărat dacă `lastAutoCheck == 0` sau `now - lastAutoCheck >= DAY_MS`); `AutoImportPolicy.Decision decide(boolean hasNewEmails, boolean quotaAvailable)` cu valorile `SKIP_AI`, `RUN_AI`, `LIMIT_REACHED`.
  - `AutoImportScheduler.maybeSchedule(Context)`: citește `getSync()`. Dacă `shouldRun`, face `enqueueUniqueWork("auto_import", ExistingWorkPolicy.KEEP, OneTimeWorkRequest<AutoImportWorker>` cu `NetworkType.CONNECTED`).
  - `AutoImportWorker.doWork()`, pe fundal, cu `Tasks.await`, pașii din spec 7.5:
    1. Token prin `Identity.getAuthorizationClient(context).authorize(...)` cu scope-ul `gmail.readonly`. Dacă `hasResolution()`, salvează `needsReconnect = true` și întoarce `success`.
    2. `fetchExcerpts(token, lastScannedEmailDate, 600)`, cu `0` la prima rulare.
    3. `decide(...)`: la `RUN_AI` cheamă `tryConsumeAnalysis`, apoi `analyze`, apoi `build`. La `SKIP_AI` cheamă `build(emptyList, …)`, ca să ruleze doar verificarea expirării.
    4. Dacă propunerea nu e goală: `savePendingReview` și `NotificationHelper.showImportFound(context, newCount, inactiveCount)`. `syncLastCharges` rulează mereu.
    5. `updateSync(lastAutoCheck = acum, lastScannedEmailDate = max(vechi, newestEmailDate)` doar dacă analiza a reușit, `needsReconnect = false)`.
    6. La `AnalyzerException`, `lastAutoCheck` se actualizează, dar `lastScannedEmailDate` rămâne neschimbat; întoarce `success`.
  - `NotificationHelper.CHANNEL_IMPORT = "import_found"`: canalul „Abonamente găsite”, `IMPORTANCE_DEFAULT`. `showImportFound(Context, int newCount, int inactiveCount)` deschide `GmailImportActivity` cu `EXTRA_OPEN_PENDING = true`, peste `MainActivity`, ca la alerte (`TaskStackBuilder`).
  - `MainActivity`: în `onResume` cheamă `AutoImportScheduler.maybeSchedule(this)`. Primește două bannere noi în `activity_main.xml`, deasupra listei:
    - `pendingBanner`: textul „Am găsit X abonamente noi și Y inactive”, cu plurale, plus butonul „Verifică”. Apare când `observePendingReview()` nu e null.
    - `reconnectBanner`: „Accesul la Gmail a expirat”, plus butonul „Reconectează”, care deschide `GmailImportActivity`. Apare când `needsReconnect` e true.

- [ ] **Step 1: Testele**

```java
@Test public void runsWhenNeverChecked() { assertTrue(AutoImportPolicy.shouldRun(0, 1_000L)); }
@Test public void doesNotRunTwiceInSameDay() {
    long now = 1_800_000_000_000L;
    assertFalse(AutoImportPolicy.shouldRun(now - 3_600_000L, now));
    assertTrue(AutoImportPolicy.shouldRun(now - AutoImportPolicy.DAY_MS, now));
}
@Test public void noNewEmailsSkipsAi() {
    assertEquals(AutoImportPolicy.Decision.SKIP_AI, AutoImportPolicy.decide(false, true));
    assertEquals(AutoImportPolicy.Decision.RUN_AI, AutoImportPolicy.decide(true, true));
    assertEquals(AutoImportPolicy.Decision.LIMIT_REACHED, AutoImportPolicy.decide(true, false));
}
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*AutoImportPolicyTest'`. Expected: FAIL.
- [ ] **Step 3:** Implementează `AutoImportPolicy`, `AutoImportWorker`, `AutoImportScheduler`, notificarea și bannerele.
- [ ] **Step 4:** Run `./gradlew testDebugUnitTest installDebug`. Expected: PASS și `BUILD SUCCESSFUL`.
- [ ] **Step 5: Verificare manuală:**
  - Deschide aplicația de 3 ori la rând. `adb shell dumpsys jobscheduler | grep -c auto_import` arată cel mult o sarcină.
  - Forțează rularea (`adb shell cmd jobscheduler run -f com.example.proiectcmo <id>`). Apar bannerul și notificarea, iar „Verifică” deschide propunerea.
  - Revocă accesul aplicației din contul Google și forțează iar rularea. Apare „Reconectează Gmail”, iar aplicația nu se blochează.
- [ ] **Step 6:** Commit: `Verificarea automată zilnică în fundal, cu banner și notificare`.

### Task 9: Configurarea Firebase și verificarea completă

**Files:** niciun fișier de cod. Opțional, `docs/superpowers/specs/...` doar dacă se descoperă o abatere.

- [ ] **Step 1 (proprietarul proiectului):** consola Firebase → AI Logic → Get started → **Gemini Developer API**, pe planul Spark.
- [ ] **Step 2 (proprietarul proiectului):** App Check → aplicația Android → **Play Integrity**, cu SHA-256-ul cheii comune. Constructorul îl obține cu `./gradlew signingReport` și i-l dă proprietarului.
- [ ] **Step 3 (proprietarul proiectului):** App Check → Manage debug tokens → adaugă tokenul din Logcat (Task 1, Step 3).
- [ ] **Step 4:** Rulează importul manual pe contul autorului. Expected: Apple Music, iCloud+, Claude Pro și Netflix rămân active (fără propuneri, cel mult sincronizarea datei ultimei plăți), iar Prime Video, YouTube Premium și Google One apar la „Nu mai sunt active”.
- [ ] **Step 5:** Run `./gradlew testDebugUnitTest`. Expected: toate testele trec (6 vechi + 23 noi).
- [ ] **Step 6:** Commit pentru orice ajustare găsită: `Ajustări după testarea completă a importului cu Gemini`.

---

**Notă pentru executant:** `lastChargeSync` (Task 5 regula 4, Task 6) actualizează data ultimei plăți **fără confirmare**. E o decizie de implementare: e o informație internă, fără de care verificarea expirării ar marca greșit abonamentele active. Nu schimbă prețul, starea sau altceva vizibil.
