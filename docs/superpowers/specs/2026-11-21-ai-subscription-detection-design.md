# Detectarea abonamentelor cu AI din Gmail: design

Data: 2026-11-21 · Proiect: SubTracker (Android, Java) · Stare: în așteptarea aprobării

## 1. Intenție și motiv

Autorul folosește iPhone, iar multe abonamente îi sunt facturate prin App Store, așa că nu mai știe sigur ce plătește. Importul actual din Gmail caută doar cele 20 de servicii din `assets/catalog.json` și propune și abonamente care nu mai sunt active. Exemplul real: Prime Video, găsit pe baza unui email din septembrie, deși abonamentul nu mai e activ.

Scopul este ca importul să găsească **orice abonament activ** din emailuri, inclusiv aplicații și site-uri pe care utilizatorul le-a uitat, ca să le poată anula. În plus, importul trebuie să **corecteze lista existentă**: abonamentele salvate care nu mai sunt active sau au alt preț trebuie actualizate, pentru că lista de acum conține intrări greșite.

Autorul nu vrea **niciun cost în plus**. Abonamentele lui Claude Pro și Google AI Plus nu includ accesul prin API, așa că soluția folosește varianta gratuită a API-ului Gemini, prin Firebase AI Logic, pe planul Spark.

## 2. Obiective

1. Detectează abonamente de la orice expeditor, fără limita catalogului.
2. Desparte chitanțele Apple (și Google Play) pe aplicații: fiecare aplicație plătită prin App Store devine un abonament separat.
3. Afișează la import doar abonamentele **active**. Un abonament e inactiv dacă există un email de anulare sau expirare după ultima plată, ori dacă ultima plată e mai veche decât un ciclu plus o toleranță.
4. Actualizează abonamentele deja salvate: le marchează ca anulate, le reactivează și le actualizează prețul, moneda, ciclul și data următoarei plăți, după confirmarea utilizatorului.
5. Fiecare abonament găsit are un mod de anulare: un link din catalog, pagina de abonamente Apple sau Google Play, ori site-ul expeditorului.
6. Cost zero: fără plan Blaze, fără card, fără server propriu.
7. Verificare automată la deschiderea aplicației, cel mult o dată pe zi, în fundal. Un abonament făcut azi apare la prima deschidere de după. Deschiderile repetate în aceeași zi nu repetă analiza.

## 3. Non-obiective

- Aplicația nu anulează singură abonamente. Nu există un API pentru asta.
- Nu citește extrase bancare și nu folosește alți furnizori de email decât Gmail.
- Nu rulează după un program fix când aplicația nu e deschisă. Verificarea pornește la deschiderea aplicației, apoi continuă în fundal.
- Nu aplică singură modificările găsite automat. Utilizatorul le confirmă.
- Nu adaugă o interfață nouă pentru gestionarea catalogului.

## 4. Anti-obiective (ce ar însemna eșec chiar dacă „merge”)

- Șterge automat abonamente ale utilizatorului. Se face doar marcare ca anulat, care e reversibilă.
- Schimbă abonamente existente fără să arate înainte ce schimbă, inclusiv la verificarea automată.
- Consumă cota gratuită Gemini când n-a venit nicio chitanță nouă.
- Trimite la AI emailuri întregi, atașamente sau emailuri care nu sunt chitanțe.
- Ascunde utilizatorului că fragmentele ajung la Google și pot fi citite de evaluatori umani.
- Generează costuri: proiectul rămâne pe planul Spark, fără cont de facturare.

## 5. Constrângeri

- Aplicația rămâne Java + XML, MVVM, cu textele în `strings.xml` (cerințele cursului).
- Repo-ul GitHub e public.
- Proiectul Firebase este `subtraker-61765`, pe planul **Spark**.
- Accesul la Gmail este `gmail.readonly`, cu ecranul de consimțământ OAuth în modul Testing (maximum 100 de utilizatori de test).
- Varianta gratuită Gemini are limite de cereri pe proiect, resetate zilnic la miezul nopții, ora Pacificului. Limitele exacte pentru modelele Flash nu apar în documentație; se văd în AI Studio. Când limita e atinsă, importul eșuează cu un mesaj clar și se poate relua a doua zi.
- Pe varianta gratuită, Google folosește conținutul trimis pentru a-și îmbunătăți produsele, iar evaluatori umani îl pot citi (termenii Gemini API, „Unpaid Services”).

## 6. Abordări luate în calcul

| Abordare | Pe scurt | De ce nu / de ce da |
|---|---|---|
| 1. Reguli pe telefon | Abonament = plăți repetate de la același expeditor, plus cuvinte-cheie. Totul rămâne pe telefon. | Gratuit și privat, dar fragil la formate și limbi, slab la abonamente noi și la anulări. Respinsă: nu rezolvă problema principală (App Store, abonamente necunoscute). |
| 2. AI pe emailuri întregi | Emailurile sunt trimise la un model AI, care decide tot. | Precisă, dar trimite mult text personal. Respinsă în această formă. |
| 3a. Hibrid cu Claude, printr-o funcție Firebase | Fragmente filtrate pe telefon, analizate de Claude pe server. | Cea mai bună calitate, dar cere API plătit Anthropic, planul Blaze și card. Respinsă: autorul nu vrea costuri în plus. |
| **3b. Hibrid cu Gemini gratuit (aleasă)** | Telefonul caută chitanțele și extrage fragmentele, apoi le trimite direct la Gemini prin Firebase AI Logic. Gemini decide ce e abonament, starea și detaliile. | Fără cost și fără server. Compromisul e confidențialitatea pe varianta gratuită, acceptat de autor. |

## 7. Arhitectură

```
Telefon (Android)
─────────────────
Gmail API (gmail.readonly)
  → caută chitanțe (400 de zile, max 300 de emailuri)
  → extrage fragmente (≤ 1.200 caractere/email)
  → Firebase AI Logic (Gemini Developer API, App Check)  ───►  Gemini (varianta gratuită)
       prompt + fragmente + abonamentele existente          ◄───  JSON structurat
  → potrivește cu lista existentă
  → ecran „Rezultatul importului”
  → scrie în Firestore (batch), după confirmare
```

Nu există server propriu. Cheia API Gemini e gestionată de Firebase și protejată de **Firebase App Check**, obligatoriu pentru AI Logic.

### 7.1 Pe telefon: căutare și extragere

- **Interogare Gmail** (o singură listă, pe pagini, până la **600** de emailuri, de la cele mai noi):
  `newer_than:400d -category:promotions -category:social (receipt OR chitanță OR chitanta OR factură OR factura OR invoice OR subscription OR abonament OR renew OR renews OR reînnoire OR "trial" OR canceled OR cancelled OR anulat OR anulare OR expired OR expirat OR "payment received" OR "plată")`
  Fereastra de 400 de zile acoperă un ciclu anual întreg, plus toleranța.
- **Fereastră completă:** `windowComplete` e `true` doar dacă limita de 600 de emailuri nu a fost atinsă. Dacă e `false`, promptul îi interzice modelului să întoarcă `LAPSED`, și poate întoarce doar `CANCELED` (email explicit) sau `UNCERTAIN`.
- **Fragmentul unui email** (construit pe telefon, din care nu pleacă altceva):
  - `id` (id-ul Gmail), `date` (din `internalDate`), `from` (adresa și numele afișat), `subject`;
  - din corp, după scoaterea blocurilor `<style>` și conversia HTML în text: liniile care conțin o sumă (folosind `AmountParser`), un cuvânt de abonament sau anulare din lista de mai sus, ori o dată de reînnoire sau expirare. Fiecare linie păstrează și linia de dinainte, pentru context. Totalul e limitat la 1.200 de caractere per email.
  - Emailurile care rămân fără nicio linie relevantă nu se trimit.
- Paralelizarea folosește firele comune existente din `GmailRepository`.

### 7.2 Apelul AI (pe telefon, prin Firebase AI Logic)

- **Dependențe noi:**
  - Firebase BoM urcă de la `34.0.0` la `35.2.0`;
  - `com.google.firebase:firebase-ai`;
  - `com.google.firebase:firebase-appcheck-playintegrity`;
  - `com.google.firebase:firebase-appcheck-debug`, doar pentru build-ul de debug;
  - `com.google.guava:guava:31.0.1-android`, pentru `ListenableFuture` din Java.
- **API-ul Java:** `FirebaseAI.getInstance(GenerativeBackend.googleAI()).generativeModel(MODEL, generationConfig)`, apoi `GenerativeModelFutures.from(...)`, apoi `generateContent(...)` (un `ListenableFuture`). Apelul stă într-o clasă nouă de tip repository, `SubscriptionAnalyzer`, ca restul accesului la date.
- **Model:** `gemini-3.5-flash-lite` (gratuit, fără facturare). E o singură constantă, ca să poată fi schimbat ușor.
- **Ieșire structurată:** `responseMimeType = "application/json"` și un `responseSchema` construit cu `Schema.obj/array/str/numInt/enumeration`. Constructorul verifică semnătura exactă a `Schema.obj` în versiunea SDK-ului.
- **Intrare (în prompt):**
  - `excerpts[]` (cel mult 150 per apel; un import complet de 600 de emailuri înseamnă până la 4 apeluri, rulate unul după altul, cu rezultatele combinate pe telefon după `matchesExistingId` și nume);
  - `existing[]`: abonamentele salvate ale utilizatorului, inclusiv cele anulate, cu `{ id, name, price, currency, billingCycle, source, cancelled }`;
  - `today` (data de azi, ISO);
  - `windowComplete`.
- **Ieșire** (validată de schemă): `subscriptions[]`, fiecare cu:
  - `name` (numele produsului; pentru Apple, numele aplicației, nu „Apple”);
  - `vendor` (cine încasează: Apple, Google Play sau domeniul expeditorului);
  - `price`, `currency`;
  - `billingCycle` (`MONTHLY` | `YEARLY` | `WEEKLY` | `UNKNOWN`);
  - `lastChargeDate`, `nextPaymentDate` (ISO sau gol);
  - `status`: `ACTIVE`, `CANCELED` (email explicit de anulare sau expirare după ultima plată), `LAPSED` (nicio plată în ultimul ciclu plus toleranță) sau `UNCERTAIN`;
  - `evidenceIds[]`;
  - `matchesExistingId` (gol dacă e un abonament nou);
  - `reason` (o propoziție scurtă, în română, afișată utilizatorului).
- **Ce poate ține aplicația:** rezultatele cu ciclu `WEEKLY` sau `UNKNOWN`, fără `nextPaymentDate` sau cu altă monedă decât RON, EUR sau USD **nu se importă**. Apar la „De verificat” (7.4). Modelul de date (lunar/anual, lei/euro/dolari) rămâne neschimbat, ca totalurile și alertele să rămână corecte.
- **Promptul** cere explicit:
  - distinge plățile unice (bilete, comenzi, livrări) de abonamente;
  - tratează perioadele de probă: abonamentul e activ, iar prima plată e la sfârșitul probei;
  - desparte chitanțele Apple și Google Play pe produse;
  - aplică toleranța de 7 zile (lunar) și 30 de zile (anual) pentru `LAPSED`;
  - nu inventa prețuri;
  - respectă `windowComplete`.
- **Mărime:** un apel are cel mult 150 × 1.200 de caractere, adică ~50.000 de tokeni, mult sub limita de 1M tokeni și de 20 MB per cerere. Apelurile pornesc unul după altul, ca să rămână sub limita gratuită de tokeni pe minut. La eroare de cotă, se reîncearcă după un minut, o singură dată.
- **Limită pe utilizator:** cel mult 5 analize pe zi (automate și manuale la un loc; o analiză = un import, oricâte apeluri ar avea), numărate în Firestore la `users/{uid}/meta/usage`. Protejează cota gratuită comună a proiectului. Cost nu poate exista, pentru că proiectul n-are cont de facturare.
- **Erori:** cota gratuită depășită, App Check respins sau rețea indisponibilă duc la un mesaj clar în `strings.xml`. Abonamentele existente nu se modifică.

### 7.3 Potrivirea cu lista existentă

- Se folosește `matchesExistingId` de la model. Ca verificare pe telefon, se acceptă doar dacă numele normalizate (litere mici, fără diacritice, fără „premium/plus/pro”) se potrivesc parțial, ori dacă sursa e Gmail cu același vendor. Altfel e tratat ca abonament nou.
- Câmp nou opțional pe `Subscription`: `vendor` (string), salvat la importurile viitoare pentru o potrivire mai sigură.
- Un abonament existent **fără nicio dovadă în emailuri** (de exemplu, adăugat manual) nu se modifică.
- Abonamentele cu sursa `MANUAL` sau `CATALOG` pot fi propuse ca anulate doar pe baza unui email explicit de anulare (`CANCELED`), nu pe baza lipsei plăților (`LAPSED`). Prețul lor se actualizează doar dacă potrivirea de nume e exactă după normalizare.

### 7.4 Ecranul „Rezultatul importului” (înlocuiește „Abonamente găsite”)

```
Abonamente noi (3)
 [x] Apple · Calm             49,99 lei / lună    următoarea plată 3 dec.
 [x] notion.so · Notion Plus  $10,00 / lună       următoarea plată 9 dec.
Actualizări (2)
 [x] Claude Pro   99,99 lei → 109,99 lei   „preț nou în chitanța din 11 nov.”
 [x] Netflix      reactivează              „plată nouă din 3 nov.”
Nu mai sunt active (2)
 [x] YouTube Premium  marchează ca anulat  „email de anulare din 2 oct.”
 [ ] Prime Video      marchează ca anulat  „nicio plată din 15 sept.”
De verificat (1)
     Medium · 5,00 $ / săptămână           [ Anulează ]

            [ Aplică selecția (5) ]
```

- **Noi** și **Actualizări** (inclusiv reactivările): bifate implicit.
- **Nu mai sunt active**: bifate implicit doar cele `CANCELED`. Cele `LAPSED` apar nebifate, cu motivul afișat.
- **De verificat**: rezultatele pe care aplicația nu le poate ține, fără bife, doar cu buton de anulare.
- Abonamentele `UNCERTAIN` nu apar nicăieri. Cele `ACTIVE` care există deja și nu s-au schimbat nu apar.
- La „Aplică” se face un singur `WriteBatch` cu: adăugări (`source = GMAIL`, `vendor`, `cancelUrl`), actualizări de câmpuri și `cancelled = true/false`. Nu se șterge nimic.
- **Textul de confidențialitate**, afișat pe ecranul de import înainte de „Conectează Gmail”: „Căutăm chitanțele în Gmail pe telefon și trimitem la Google Gemini doar fragmente scurte din ele (expeditor, subiect, sume, date), pentru analiză. Pe varianta gratuită, Google poate folosi aceste fragmente pentru a-și îmbunătăți serviciile și le pot citi evaluatori umani. Emailurile întregi nu pleacă de pe telefon.”

### 7.5 Verificarea automată zilnică

- **Pornire:** la fiecare deschidere a ecranului principal, dacă au trecut cel puțin 24 de ore de la ultima verificare (`lastAutoCheck`, în `users/{uid}/meta/sync`), se pune în coadă un `OneTimeWorkRequest` WorkManager cu nume unic (`auto_import`, politica `KEEP`) și constrângerea „rețea disponibilă”. Deschiderile repetate nu adaugă altă sarcină.
- **Pașii sarcinii din fundal:**
  1. Obține tokenul Gmail cu `AuthorizationClient`, fără ecran. Dacă Google cere din nou consimțământul, salvează `needsReconnect = true` și se oprește. Ecranul principal afișează atunci „Reconectează Gmail”.
  2. **Verificare ieftină, fără AI:** caută în Gmail emailurile care se potrivesc interogării din 7.1 și sunt mai noi decât `lastScannedEmailDate`. Dacă nu există, sare direct la pasul 4.
  3. Dacă există, construiește fragmentele doar pentru emailurile noi și apelează Gemini (7.2) cu ele, plus lista existentă. Fiecare abonament din listă e trimis cu `lastChargeDate`. Prima rulare, fără `lastScannedEmailDate`, face un import complet de 600.
  4. **Verificarea expirării, pe telefon, fără AI:** pentru abonamentele cu sursa `GMAIL` și `lastChargeDate` cunoscut, dacă `azi > lastChargeDate + ciclu + toleranță` (7 zile lunar, 30 anual), abonamentul e propus la „Nu mai sunt active” (nebifat, ca orice `LAPSED`).
  5. Salvează rezultatul ca **propunere în așteptare** (`users/{uid}/meta/pendingReview`), actualizează `lastAutoCheck` și `lastScannedEmailDate`, iar dacă propunerea nu e goală afișează o notificare.
- **Câmp nou pe `Subscription`:** `lastChargeDate` (long, opțional), completat la import și actualizat la fiecare chitanță nouă potrivită.
- **Banner pe ecranul principal:** dacă există o propunere în așteptare, apare „Am găsit 1 abonament nou și 1 inactiv · Verifică”. Apăsarea deschide ecranul din 7.4 cu propunerea. „Aplică” sau „Ignoră” golește propunerea.
- **Notificare:** canal separat, „Abonamente găsite”, cu importanță normală. Atingerea ei deschide același ecran.
- **Limitarea zilnică:** o verificare automată care ajunge la Gemini consumă una din cele 5 analize pe zi. Una oprită la pasul 2 nu consumă nimic.

### 7.6 Linkul de anulare

1. Dacă numele se potrivește cu catalogul, se ia `cancelUrl` din catalog.
2. Altfel, dacă vendor-ul e Apple: `https://apps.apple.com/account/subscriptions`.
3. Altfel, dacă vendor-ul e Google Play: `https://play.google.com/store/account/subscriptions`.
4. Altfel: `https://<domeniul expeditorului>`, iar utilizatorul îl poate corecta din Editează.

### 7.7 Categorie

Categoria vine din catalog dacă numele se potrivește, altfel e `OTHER`. Utilizatorul o poate schimba din Editează.

## 8. Ce rămâne în cod, ce dispare

- Rămân: `AmountParser` (pentru extragerea fragmentelor), `SubscriptionCatalog` (doar pentru linkuri și categorii), firele comune din `GmailRepository`, autorizarea Gmail din `GmailImportActivity`.
- Dispar: căutarea pe serviciu din catalog (`searchService`), `DetectedSubscription` în forma actuală (înlocuit de rezultatul AI), câmpurile `domains` și `keyword` din catalog (rămân `name`, `price`, `currency`, `cycle`, `category`, `cancelUrl`).

## 9. Testare

- **Teste unitare (JUnit):** construirea fragmentelor (o chitanță Apple cu două produse, un email de anulare, un email fără nimic relevant), normalizarea numelor și potrivirea cu lista existentă, regulile din 7.3, alegerea linkului de anulare, transformarea răspunsului JSON în cele patru grupuri din 7.4 (cu un răspuns JSON scris de mână, fără apel real la AI).
- **Teste unitare pentru verificarea automată:** regula „cel mult o dată la 24 de ore”, regula de expirare de la pasul 4 și trecerea peste AI când nu sunt emailuri noi (cu Gmail simulat).
- **Manual, pe emulator:** cu contul real al autorului și cu App Check în modul debug. Rezultatul așteptat: Apple Music, iCloud+, Claude Pro și Netflix active; Prime Video, YouTube Premium și Google One propuse ca inactive.

## 10. Configurare necesară (făcută de proprietarul proiectului, în consola Firebase)

1. **AI Logic** → Get started → furnizorul **Gemini Developer API**, pe planul Spark, fără facturare.
2. **App Check** → aplicația Android → furnizorul **Play Integrity**, cu amprenta SHA-256 a cheii comune de debug.
3. Pentru emulator: tokenul de debug afișat în Logcat la prima rulare se adaugă în App Check → Manage debug tokens. Fiecare calculator și emulator are tokenul lui.

## 11. Decizii luate de mine (de verificat de autor)

- Modelul `gemini-3.5-flash-lite`, ușor de schimbat.
- Maximum 600 de emailuri per import complet, în apeluri de câte 150; 5 analize pe zi per utilizator (cerute de autor).
- Propunerile verificării automate se confirmă din banner sau notificare și nu se aplică singure (cerut de autor).
- Propunerea în așteptare și datele verificării stau în Firestore, sub `users/{uid}/meta/`.
- Verificarea automată pornește doar la deschiderea aplicației, fără sarcină periodică atunci când aplicația nu e deschisă.
- Fereastra de 400 de zile; `LAPSED` doar cu fereastră completă; propunerile `LAPSED` vin nebifate.
- Toleranțele pentru `LAPSED`: 7 zile la lunar, 30 de zile la anual.
- Ciclurile săptămânale sau necunoscute și monedele străine nu se importă; apar la „De verificat”.
- Abonamentele anulate sunt trimise modelului și pot fi reactivate, la „Actualizări”.
- Abonamentele `MANUAL` și `CATALOG` se anulează doar pe email explicit de anulare.
- Abonamentele fără dovezi în email nu sunt atinse. Categoria vine doar din catalog, altfel `OTHER`.

## 12. Lăsat la latitudinea constructorului

Formularea exactă a promptului, numele claselor noi, aranjarea vizuală exactă a ecranului de rezultat (după stilurile existente), paginarea Gmail, împărțirea în mai multe apeluri dacă e nevoie, mesajele de eroare (în `strings.xml`).
