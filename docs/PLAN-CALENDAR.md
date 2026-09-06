# Plan funcții noi: Materiale existente la client · Clienți · Scanare buletin · Calendar și programări

Stare: **în lucru** — scrisă 2026-09-06 pornind de la v1.20; Etapa M livrată în v1.21 (împreună cu reproiectarea PDF-urilor); Etapa 0 livrată în v1.22.
Fiecare etapă = o versiune publicată separat (teste → build → bump → commit → release),
ca să poți folosi și testa pe teren fiecare bucată înainte de următoarea.

Bifează `[x]` pe măsură ce terminăm. Ordinea e gândită ca fiecare etapă să fie
utilă singură, iar cele opționale (3 și 4) să poată fi sărite.

Ordinea recomandată: **M → 0 → S → 1 → 2 → (3) → (4)**. Etapa M (materiale existente la client)
e independentă de calendar, e cea mai mică și aduce câștig imediat la fiecare PDF, de aceea e prima.

---

## Ce există azi (pe ce construim)

- Datele stau în fișiere JSON în `filesDir` (`Repo` din `Model.kt`), fără bază de date.
- Clientul **nu** e o entitate separată: `Work` are doar câmpurile text `client`, `address`, `phone`.
- Un singur `AppViewModel`, un singur `MainActivity.kt` (2 770 linii) cu 4 ecrane
  în bara de jos: Necesar · Sumar · Lucrări · Setări.
- Backup v3 = categorii + lucrări + mărci + manoperă + setări.
- Teste JUnit pure (fără Robolectric), un fișier `VxxTest.kt` per versiune.
- minSdk 26 → putem folosi `java.time` (LocalDate, YearMonth) fără biblioteci extra.
- Material3 din BOM 2025.09.00 include `DatePicker` și `TimePicker` — nu adăugăm dependențe.
- Lanțul de la editor la PDF (`AppViewModel.preparePdfWork`): `snapshot()` (doar cantități > 0)
  → `Work.withAutoAccessories()` (rame suport + măști per doză, obturatoare = sloturi − module folosite)
  → `Work.filterForPdf()` (scoate dozele/carcasele/corpurile informative) → ascundere prețuri.
  Manopera (`laborQuote`) și rezumatul de accesorii din Sumar se calculează separat, din `Work`-ul complet.

## Decizii de design (propuse; spune dacă vrei altfel)

| Subiect | Propunere | De ce |
|---|---|---|
| Unde apare calendarul | Al 5-lea tab în bara de jos: **Necesar · Sumar · Lucrări · Calendar · Setări** | Material3 permite 3–5 taburi; calendarul e folosit zilnic, merită acces direct |
| Unde apar clienții | **Pagină proprie „Clienți”**, tab în bara de jos | Cerință: pagină de administrare cu adăugare / editare / ștergere |
| Bara de jos cu 6 ecrane | Bara devine **Necesar · Sumar · Lucrări · Clienți · Calendar**; **Setări** se mută în meniul ⋮ din dreapta-sus (unde sunt deja acțiunile rare) | Material3 recomandă maxim 5 taburi; Setările se deschid rar, Clienții și Calendarul zilnic. Alternativă dacă nu-ți place: Calendar în meniul ⋮ în loc de Setări |
| Recunoaștere text (OCR) | **ML Kit Text Recognition** (Google, rulează pe telefon, model latin inclus în APK, ~4–5 MB în plus) | Singura opțiune matură fără server; funcționează offline; nu trimite imaginea nicăieri |
| CNP | Se salvează doar local, în fișa clientului; **nu** intră niciodată în PDF sau în textul de Copiază/Trimite; în listă apare mascat (`•••••••••1234`); setare „Salvează CNP-ul” (implicit pornit) | E dată cu caracter special în România; o ținem doar cât e nevoie și doar pe telefon |
| Ascundere | Setare „Arată calendarul” (implicit pornit) | Regula ta: nu ștergem funcții, doar ascundem din setări |
| Client separat sau text liber | Entitate `Client` separată, cu istoric | Altfel nu poți vedea „ce am făcut la Popescu” sau reprograma din 2 apăsări |
| Remindere | Întâi „Adaugă în calendarul telefonului” (fără permisiuni); notificări proprii doar dacă le vrei | Google Calendar îți dă deja alarme, sincronizare și widget gratis |
| Fișiere noi de cod | `Clients.kt`, `ClientsScreen.kt`, `IdCardParser.kt` (pur), `IdScanner.kt` (ML Kit), `Calendar.kt`, `CalendarScreen.kt` | `MainActivity.kt` e deja prea mare; logica pură separată = testabilă |

---

## Etapa M — Materiale existente la client (v1.21) — ✅ livrată 2026-09-06

**Problema**: clientul are deja o parte din materiale (module, siguranțe automate, uneori rame),
iar lista pentru magazin trebuie să conțină doar ce lipsește. Necesarul „tehnic” (ce se montează)
rămâne complet, pentru că manopera, ramele și obturatoarele depind de el.

**Regula de aur a calculului** (de aici vine tot designul):

```
necesar complet  →  accesorii automate (rame, obturatoare)  →  − materiale existente  →  filtrare PDF
```

Scăderea se face **după** calculul accesoriilor, niciodată înainte. Exemplu: 1 doză 4 module
cu 4 module planificate, clientul are deja 2 module → PDF: 2 module, 1 ramă suport, 1 mască,
**0 obturatoare**. Dacă am scădea întâi, ar rămâne 2 module pe 4 sloturi și ar apărea 2 obturatoare false.

**Model** (`Model.kt`)
- [x] `data class OwnedMaterial(name: String, qty: Int, category: String = "")` — potrivire după nume
      (fără diacritice/majuscule), categoria doar ca ajutor la afișare
- [x] `Work` primește `owned: List<OwnedMaterial> = emptyList()` (JSON: cheia `owned`, lipsă = listă goală,
      deci lucrările vechi și backup-urile v3 se citesc neschimbat)
- [x] `fun Work.subtractOwned(): Work` — funcție pură: pentru fiecare material existent scade cantitatea
      din linia cu același nume (inclusiv din categoria „Accesorii doze modulare (calcul automat)”,
      ca să poți bifa și rame/obturatoare pe care le are), `coerceAtLeast(0)`, liniile ajunse la 0 dispar,
      categoriile rămase goale dispar
- [x] `preparePdfWork` devine: `withAutoAccessories()` → **`subtractOwned()`** → `filterForPdf()`;
      `summaryText()` (Copiază/Trimite) trece prin același lanț
- [x] `laborQuote` și `accessorySummary` **nu** se ating: primesc necesarul complet (modulele clientului
      tot se montează, ramele tot se cumpără pe baza dozelor)

**Stare în editor** (`AppViewModel`)
- [x] `var owned by mutableStateOf(...)`, persistat în `necmat_owned.json` (ca să supraviețuiască închiderii
      aplicației, la fel ca lista curentă de cantități)
- [x] `snapshot()` include `owned`; `loadWork()` restaurează `owned` din lucrare; `duplicateWork` îl copiază;
      `resetQuantities()` (și „golește după salvare”) îl golește
- [x] Când scazi cantitatea unui material sub ce are clientul, `owned` se ajustează automat la noul maxim
      (nu poate „avea” mai mult decât e în necesar)

**UI — secțiune expandabilă în pagina Necesar** (fișier nou `OwnedSection.kt`)
- [x] Card la finalul listei de categorii: „Materiale existente la client” + badge cu numărul de linii;
      închis implicit, starea de expandare reținută ca la categorii
- [x] Deschis: lista materialelor bifate, fiecare cu `Necesar 10 · Are 4 · De cumpărat 6` și butoane + / −
      plafonate la cantitatea din necesar
- [x] Buton „Adaugă material existent” → dialog cu **doar materialele cu cantitate > 0** din necesarul curent,
      grupate pe categorii, plus liniile de accesorii calculate (rame, măști, obturatoare), cu căutare;
      alegi și pui cantitatea
- [x] Notă discretă în secțiune: „Verifică dacă materialele clientului sunt compatibile cu marca aleasă
      (ex. modulele altui producător nu intră în ramele alese)” — doar text, fără logică
- [x] În antetul aplicației (`N tipuri · M buc`) și în Sumar apare și „− K buc existente la client”
      când lista nu e goală; în Sumar, liniile afectate arată cantitatea tăiată și cea nouă (`10 → 6`)

**PDF și ofertă** (`PdfExporter.kt`)
- [x] Tabelul principal listează doar ce e de cumpărat (rezultatul lanțului de mai sus)
- [x] Secțiune finală opțională „Materiale puse la dispoziție de client” (nume + cantitate), ca beneficiarul
      să vadă că au fost luate în calcul; setare `ownedInPdf` (implicit pornit), în stilul „fără ștergeri,
      doar ascundere”
- [x] Oferta de manoperă rămâne pe necesarul complet; în desfășurător nu se schimbă nimic

**Setări**
- [x] „Arată secțiunea materiale existente” (implicit pornit) și „Listează în PDF materialele clientului”

**Teste** (`V21Test.kt`)
- [x] exemplul de aur: 1 doză 4 module + 4 module, client are 2 → 2 module, 0 obturatoare, rame intacte
- [x] client are 1 ramă suport 4 module → linia de ramă scade, obturatoarele nu se schimbă
- [x] client are mai mult decât necesarul (are 6, necesar 4) → linia dispare, fără cantități negative
- [x] material existent care nu se regăsește în necesar → ignorat fără eroare
- [x] potrivire insensibilă la majuscule/diacritice („Siguranta automata 16A” = „Siguranță automată 16A”)
- [x] `laborQuote` dă același total cu și fără `owned`
- [x] JSON dus-întors `Work` cu `owned`; lucrare veche fără cheia `owned` → listă goală
- [x] `summaryText()` reflectă scăderea (același rezultat ca PDF-ul)

---

## Etapa 0 — Clienți: entitate + pagină de administrare (v1.22) — ✅ livrată 2026-09-06

Fundația pentru scanare, programări și agenda telefonului. Fără ea, fiecare funcție ar duplica datele clientului.

**Model** (`Clients.kt`)
- [x] `data class Client(id, name, phone, address, email = "", cnp = "", notes = "", createdAt, updatedAt)`
      — `name` = „Nume Prenume” într-un singur câmp (ca acum în lucrare), `email` și `cnp` opționale
- [x] Fișier `necmat_clients.json` (load/save în `Repo`, același stil ca lucrările)
- [x] `Work` primește `clientId: Long? = null`; câmpurile text vechi rămân (compatibilitate + PDF).
      **CNP-ul și e-mailul nu se copiază în `Work`** — PDF-ul nu are de unde să le ia
- [x] Migrare aditivă la pornire: din lucrările existente se extrag clienți unici
      (cheie: telefon normalizat — doar cifre, fără prefixul +4; dacă lipsește, nume + adresă), lucrările primesc `clientId`
- [x] Backup **v4**: include `clients` (și, din etapa 1, `appointments`); backup v3 se restaurează în continuare
- [x] Funcții pure testabile: `normalizePhone`, `findDuplicate(client, list)`, `clientsFromWorks(works)`, `isValidCnp`, `isValidEmail`

**Pagina „Clienți”** (`ClientsScreen.kt`, tab nou în bara de jos)
- [x] Listă: nume, telefon, localitatea din adresă, număr de lucrări; sortare alfabetică; căutare după nume / telefon / e-mail
- [x] FAB „+” → **același formular** ca datele clientului din `WorkDetailsDialog`, extras într-un `ClientForm`
      refolosibil: nume, telefon (cu butonul existent „din agenda de contacte”), adresă (cu butonul existent
      „din locație”), e-mail (opțional, validat ca formă), CNP (opțional, validat cu cifra de control), notițe
- [x] Editare: apasă pe client → fișă cu toate datele; buton „Editează” deschide același `ClientForm`
- [x] Ștergere: din fișă, cu confirmare; lucrările clientului rămân (își păstrează textul, `clientId` devine null);
      undo prin snackbar, ca la lucrări
- [x] Avertisment la salvare dacă există deja un client cu același telefon („Există deja Ionescu Maria cu acest
      număr — deschide-l / salvează oricum”)
- [x] **Fișa clientului** cuprinde: date de contact cu acțiuni rapide (Sună · SMS · WhatsApp · E-mail · Navighează),
      istoricul lucrărilor (apasă → deschide lucrarea), iar din etapa 1 și programările
- [x] Buton **„Salvează în agenda telefonului”**: intent `ContactsContract.Intents.Insert` pre-completat cu
      nume, telefon, e-mail, adresă poștală și notă „Client NecMat” — se deschide aplicația Contacte, tu confirmi;
      **nu cere permisiunea de contacte** (scrierea o face aplicația de Contacte). CNP-ul nu se trimite în agendă
- [x] Buton „Lucrare nouă pentru acest client” → deschide Necesar cu datele pregătite pentru salvare

**Integrare cu lucrările** (`WorkDetailsDialog`)
- [x] Datele clientului din dialog folosesc același `ClientForm`
- [x] La scrierea numelui apar sugestii din clienții existenți; alegerea completează telefon + adresă și setează `clientId`
- [x] Adresa lucrării pornește de la adresa clientului (domiciliul), dar rămâne editabilă separat
      (lucrarea poate fi la altă adresă decât domiciliul)
- [x] La salvarea lucrării, un client nou (fără `clientId`) se creează automat în listă; unul existent
      (același telefon / nume + adresă) e legat automat, fără să-i modifice fișa (confirmarea la modificare
      nu a fost necesară: fișa se editează din pagina Clienți)

**Setări**
- [x] „Arată pagina Clienți” (implicit pornit), „Salvează CNP-ul” (implicit pornit)

**Teste** (`V22Test.kt`)
- [x] extragere clienți din lucrări: dedupe pe telefon (`0722…` = `+40722…`), telefon lipsă, nume diferit ca literă mare/mică
- [x] validare CNP: cifra de control, lungime, an/lună/zi imposibile, CNP-uri fictive valide și invalide
- [x] validare e-mail: forme acceptate / respinse
- [x] JSON dus-întors client (cu și fără e-mail / CNP) și lucrare cu `clientId`
- [x] backup v3 → v4: se restaurează, clienții apar după migrare
- [x] ștergerea unui client lasă lucrările intacte, cu `clientId = null`
- [x] `preparePdfWork` și `summaryText` nu conțin niciodată CNP sau e-mail

---

## Etapa S — Scanare act de identitate (v1.23)

**Scop**: dintr-o poză sau un fișier imagine cu buletinul, aplicația completează singură **nume, prenume, CNP**
și, la cartea veche, **adresa de domiciliu** (pusă în adresa lucrării / clientului). Tu verifici și confirmi.

**Cele două formate de acte** (ce trebuie să recunoască parserul):

| | Cartea electronică (2021+) | Cartea veche (format mare) |
|---|---|---|
| Nume | pe linia de sub eticheta `Nume / Surname` | sub `Nume/Nom/Last name` |
| Prenume | sub `Prenume / Given names` (poate avea cratimă) | sub `Prenume/Prenom/First name` |
| CNP | tipărit sub eticheta `CNP / PIN`, 13 cifre | tipărit lângă `CNP`, 13 cifre, **și** reconstruibil din MRZ |
| Adresă | **nu e pe față** (e în cip) — rămâne de completat manual sau din locație | sub `Domiciliu/Adresse/Address`, 1–2 linii, până la `Emisă de` |
| Loc naștere | nu | sub `Loc nastere` — **nu îl folosim** |
| MRZ | pe verso (3 linii) — opțional, dacă fotografiezi și spatele | 2 linii × 36 caractere pe față: linia 1 `IDROU` + NUME`<<`PRENUME`<`PRENUME; linia 2 conține data nașterii, sexul și cifrele CNP-ului |

**Strategia parserului** (`IdCardParser.kt`, funcție pură `parse(lines: List<String>): IdScanResult`)
- [ ] **Ancora principală = CNP-ul**: orice grup de 13 cifre care trece cifra de control (ponderi `279146358279`)
      e CNP-ul, indiferent unde apare; dacă apar mai multe, se preferă cel de lângă eticheta `CNP`
- [ ] **MRZ ca a doua sursă**: dacă există o linie care începe cu `IDROU`, numele = ce e înainte de `<<`,
      prenumele = ce e după (`<` → spațiu); din linia 2 se reconstruiește CNP-ul (cifra de sex + data nașterii +
      cele 6 cifre din câmpul opțional de la coadă) și se validează cu cifra de control. MRZ-ul e citit foarte bine
      de OCR (font monospațiat), deci confirmă sau corectează câmpurile tipărite
- [ ] Nume și prenume: linia imediat următoare etichetei (căutare tolerantă: `Nume`, `Surname`, `Prenume`,
      `Given`, `First name`); dacă tipăritul și MRZ-ul diferă, câștigă tipăritul pentru cratime și diacritice,
      MRZ-ul pentru litere lipsă
- [ ] Adresă: liniile dintre `Domiciliu` și `Emis` / `Valabilitate`; se curăță prefixele (`Jud.`, `Loc.`, `Mun.`, `Str.`)
      într-o formă lizibilă: `Str. Exemplu nr. 8, Loc. Exemplu (Mun. Exemplu), jud. DJ`
- [ ] Normalizare: numele ies din OCR cu majuscule → le transformăm în „Nume Prenume-Prenume” (prima literă mare,
      cratima păstrată); confuzii tipice OCR corectate în CNP (`O`→`0`, `I`/`l`→`1`, `S`→`5`, `B`→`8`) înainte de validare
- [ ] Rezultatul are și un **grad de încredere** per câmp (ex. CNP validat = sigur; nume doar din tipărit = de verificat),
      afișat în dialogul de confirmare

**Captura imaginii** (`IdScanner.kt`)
- [ ] Două butoane în `ClientForm` (deci și în lucrare, și în pagina Clienți): **„Fotografiază buletinul”**
      (`TakePicture` prin aplicația de cameră a telefonului, în fișier temporar prin `FileProvider`-ul existent —
      **fără permisiunea CAMERA**) și **„Din imagine”** (`PickVisualMedia` — fără permisiune de stocare)
- [ ] Imaginea se redimensionează la max. 2000 px pe latura lungă, se rotește după EXIF, se trimite la ML Kit,
      apoi **fișierul temporar se șterge**; imaginea nu se salvează în aplicație și nu pleacă de pe telefon
- [ ] Dialog de confirmare: miniatura pozei + câmpurile recunoscute editabile (nume, prenume, CNP, adresă), cu marcaj
      „✓ verificat” / „? verifică” per câmp; abia la „Folosește” se completează formularul
- [ ] Dacă nu se găsește niciun CNP valid: mesaj „Nu am putut citi actul — încearcă o poză mai dreaptă, fără reflexii”
      și posibilitatea de a păstra ce s-a citit parțial
- [ ] Sfaturi scurte în dialog: act pe fundal închis, fără blitz, cadru complet

**Dependență și build**
- [ ] `com.google.mlkit:text-recognition:16.0.1` (varianta cu model inclus; merge și pe telefoane fără Google Play,
      deci și pentru flavour-ul `github`); APK crește cu ~4–5 MB
- [ ] `PRIVACY.md` + declarația din Play Console: procesare pe dispozitiv, fără transmitere, imaginea nu e stocată

**Teste** (`V23Test.kt`) — cu **date fictive**, niciodată cu acte reale
- [ ] CNP: generator de CNP-uri fictive valide pentru teste; corecție `O→0`, `I→1`; respingere CNP cu cifra de control greșită
- [ ] format nou: liniile OCR simulate (`Nume / Surname`, `POPESCU`, `Prenume / Given names`, `ION-ANDREI`, `CNP / PIN`,
      `1xxxxxxxxxxxx`) → nume, prenume cu cratimă, CNP; adresă goală
- [ ] format vechi: liniile OCR simulate cu domiciliu pe două rânduri + MRZ → adresă curățată, CNP din tipărit = CNP din MRZ
- [ ] MRZ singur (tipăritul nu s-a citit): nume/prenume din linia 1, CNP reconstruit din linia 2, validat
- [ ] tipărit vs MRZ în conflict (o literă lipsă) → se alege varianta consistentă cu MRZ, câmpul marcat „verifică”
- [ ] ordine amestecată a liniilor (OCR-ul poate întoarce blocurile în altă ordine) → același rezultat
- [ ] text fără nicio ancoră → rezultat gol, fără excepție

---

## Etapa 1 — Programări + agendă zilnică (v1.24)

Aici apare valoarea reală: știi unde trebuie să fii și când.

**Model** (`Calendar.kt`)
- [ ] `data class Appointment(id, clientId?, workId?, title, start: Long, durationMin: Int,
      allDay: Boolean, type, status, notes, createdAt)`
- [ ] `enum AppointmentType { VIZITA, OFERTA, EXECUTIE, REVIZIE, ALTELE }` — cu culoare/iconiță
- [ ] `enum AppointmentStatus { PROGRAMAT, CONFIRMAT, FINALIZAT, ANULAT }`
- [ ] Fișier `necmat_appointments.json`; intră în backup v4
- [ ] Funcții pure: grupare pe zile, sortare, **detectare suprapuneri**, „următoarea programare”

**UI — tab Calendar, vizualizarea „Agendă”**
- [ ] Listă grupată: Azi · Mâine · restul săptămânii · mai târziu; cele trecute nefinalizate marcate
- [ ] FAB „+” → dialog programare: client (din listă sau nou), tip, dată (`DatePicker`),
      oră (`TimePicker`) sau „toată ziua”, durată (implicit din setări), notițe, lucrare legată (opțional)
- [ ] Avertisment (nu blocaj) la suprapunere cu altă programare
- [ ] Pe fiecare card: **Sună** (intent dial), **Navighează** (intent geo/maps),
      **Adaugă în calendarul telefonului** (`CalendarContract` INSERT — zero permisiuni),
      **Trimite confirmare** (SMS/WhatsApp cu text prestabilit: „Bună ziua, confirm vizita
      în data de … la ora …”), Finalizează / Anulează
- [ ] Din lucrare → buton „Programează” (client și adresă pre-completate)
- [ ] Din programare finalizată de tip Vizită/Ofertă → „Creează lucrare” (deschide Necesar
      cu datele clientului pregătite pentru salvare)

**Setări — secțiune „Calendar”**
- [ ] Arată calendarul (implicit da)
- [ ] Durată implicită programare (60 min)
- [ ] Textul mesajului de confirmare (editabil, cu {data} {ora} {nume})

**Teste** (`V24Test.kt`)
- [ ] suprapunere: două programări în aceeași oră, adiacente (nu se suprapun), toată ziua
- [ ] grupare Azi/Mâine la trecerea de miezul nopții și la schimbarea lunii
- [ ] text confirmare: înlocuirea corectă a {data} {ora} {nume}
- [ ] JSON dus-întors + backup cu programări

---

## Etapa 2 — Vizualizare lunară și săptămânală (v1.25)

**Logică pură** (`Calendar.kt`)
- [ ] `monthGrid(YearMonth, firstDayOfWeek = MONDAY)` → 6 rânduri × 7 zile, cu zilele din
      lunile vecine gri; număr programări per zi și tipul dominant (pentru buline colorate)
- [ ] `weekOf(LocalDate)` → 7 zile cu programările fiecăreia

**UI**
- [ ] Comutator sus în tab Calendar: **Lună · Săptămână · Agendă** (se reține ultima alegere)
- [ ] Lună: grilă 7 coloane, săgeți ‹ ›, buton „Azi”, buline per zi; apăsare pe zi → lista zilei
      dedesubt; apăsare lungă → programare nouă în ziua aceea
- [ ] Săptămână: 7 coloane cu carduri compacte (oră + client), scroll orizontal la nevoie
- [ ] Zilele libere / zilele cu program complet (peste X ore) evidențiate discret

**Teste** (`V25Test.kt`)
- [ ] grilă pentru februarie an bisect, lună care începe duminica, decembrie → ianuarie
- [ ] numărul de programări per zi în grilă, inclusiv cele „toată ziua”

---

## Etapa 3 — Notificări proprii (v1.26) — *opțională*

Doar dacă „Adaugă în calendarul telefonului” nu îți ajunge (de ex. vrei remindere
automate pentru **toate** programările fără să le adaugi manual).

- [ ] Canal de notificări + permisiune `POST_NOTIFICATIONS` (Android 13+), cerută la prima programare
- [ ] `AlarmManager` cu `setAndAllowWhileIdle` (inexact — evităm permisiunea de alarme exacte
      și justificarea ei în Play Store); reminder implicit 60 min înainte, per programare ajustabil
- [ ] Receiver `BOOT_COMPLETED` care reprogramează alarmele după repornirea telefonului
- [ ] Apăsarea notificării deschide aplicația direct pe programare
- [ ] Notificare de dimineață (opțional, 07:00): „Azi ai 3 programări, prima la 09:00 la …”
- [ ] Teste: calculul momentului reminderului, reprogramare după repornire, programări anulate nu notifică

---

## Etapa 4 — Legături, istoric și rafinări (v1.27) — *opțională*

- [ ] Detaliu client: cronologie unificată (lucrări + programări), buton „Programează revizie”
- [ ] Filtru în Agendă după tip/status; căutare
- [ ] Export ICS al programărilor (import în orice calendar) și PDF „Program săptămânal”
- [ ] Statistici mici în Calendar: programări luna asta, finalizate, anulate
- [ ] Culoare/iconiță per tip de programare configurabile (se leagă de ideea existentă
      „culoare per categorie”)

---

## Riscuri și lucruri de ținut minte

- **Compatibilitate backup**: v4 trebuie să citească v3 și v2; test explicit la fiecare etapă.
- **Ordinea calculului la materiale existente**: accesoriile se calculează pe necesarul complet, scăderea
  vine după. Orice funcție nouă care produce lista de cumpărături trebuie să treacă prin `preparePdfWork`,
  nu să refacă lanțul pe cont propriu.
- **Fusul orar / ora de vară**: stocăm `start` ca epoch millis și afișăm cu `ZoneId.systemDefault()`;
  testele de grupare pe zile folosesc o zonă fixă (`Europe/Bucharest`).
- **Mărimea `MainActivity.kt`**: tot UI-ul nou merge în fișiere separate; când atingem
  ecranul Lucrări pentru comutatorul Lucrări/Clienți, mutăm `WorksScreen` în `WorksScreen.kt`.
- **Date personale**: CNP-ul rămâne doar în `necmat_clients.json` și în backup (fișier pe care îl gestionezi tu);
  la partajarea backup-ului ține cont că acum conține CNP-uri. Fără CNP în PDF, în text, în jurnal (`AppLog`) sau
  în numele fișierelor. Poza actului nu se păstrează.
- **OCR pe teren**: poze strâmbe, reflexii de pe folia holografică, lumină slabă. De aceea CNP-ul cu cifră de control
  și MRZ-ul sunt ancorele, iar utilizatorul confirmă mereu înainte de salvare.
- **Play Store**: etapa 3 adaugă permisiuni (notificări, boot) → de actualizat `PRIVACY.md`
  și declarația din consolă; etapele M, 0, S, 1, 2 nu adaugă nicio permisiune (camera prin intent, contactele prin intent).
- **Jurnal**: fiecare acțiune (creare/mutare/anulare programare) intră în `AppLog` la nivel INFO,
  ca restul aplicației.

## Estimare orientativă

| Etapă | Cod nou (aprox.) | Teste noi |
|---|---|---|
| M Materiale existente | ~350 linii | ~8 |
| 0 Clienți + pagină + agendă telefon | ~900 linii | ~12 |
| S Scanare buletin | ~600 linii | ~10 |
| 1 Programări + Agendă | ~900 linii | ~10 |
| 2 Lună / Săptămână | ~500 linii | ~6 |
| 3 Notificări | ~350 linii | ~5 |
| 4 Rafinări | ~400 linii | ~4 |

## Cum lucrăm cu planul

Într-o sesiune nouă e suficient să spui „continuă etapa X din docs/PLAN-CALENDAR.md”.
La finalul fiecărei etape: bifăm punctele, actualizăm README (secțiunea Funcții)
și, dacă e cazul, `PRIVACY.md`.
