# Plan — Chei stabile, grupuri de materiale, client PF / PJ (v1.36)

Cerere (2026-09-08): (1) în chenarul clientului: persoană fizică / juridică + CUI la firme;
(2) secțiunea „Sistem fotovoltaic” ascunsă deocamdată din Materiale și Necesar; (3) materialele
grupate pe tipuri (doze încastrate → aparataj încastrat, doze modulare → aparataj modular) în
Materiale, Necesar și PDF; (4) marca și modelul obligatorii; (5) redenumirea unei categorii nu
trebuie să afecteze actualizările — fiecare categorie și fiecare material are o cheie stabilă;
(6) secțiunea „Materiale existente la client” îmbunătățită. Prioritate: aplicația nativă.

## 1. Chei stabile (`Catalog.kt`, `Model.kt`)

- `Category.key` — id-ul categoriei standard (`doze_aparat`, `aparataj_incastrat`, `doze_modulare`,
  `module`, `accesorii_modulare`, `aparataj_aplicat`, `tablou`, `doze_legaturi`, `cabluri`,
  `iluminat`, `pv`). Categoriile create de utilizator au cheia goală.
- `Material.key` — numele de catalog (implicit) la creare; redenumirea schimbă doar `name`.
  `catalogName = key.ifBlank { name }` intră în toate regulile (doze modulare, carcase, cabluri,
  prețuri de manoperă), deci un material redenumit e recunoscut în continuare.
- `catalogKey` (strict: cheia setată sau numele canonic) → folosit de migrări. `kind` (permisiv:
  și deducere după cuvinte-cheie) → folosit la grupare, manoperă, UM, PDF.
- Migrare `assignCatalogKeys` (aditivă, idempotentă, rulează prima în `applyAllMigrations` și la
  restaurarea backup-ului): categoriile cu nume canonic primesc cheia; materialele care se potrivesc
  cu catalogul implicit primesc `key`. Toate migrările v3–v12 caută după cheie, nu după nume.
- Lucrările salvate păstrează cheile; la reîncărcare potrivirea e după cheie, apoi după nume.
- Asistentul (`Wizard.kt`) lucrează pe chei; categoriile lipsă se creează cu numele canonic.
- JSON: `key` pe categorie și material (absent = gol), `catKey`/`key` pe materialele clientului,
  `cui` pe lucrare, `kind`/`cui` pe client. Backup v4 rămâne compatibil.

## 2. Grupuri (`CategoryGroup`)

| Grup | Categorii (ordine fixă) | Marcă + model |
|---|---|---|
| Aparataj clasic încastrat | Doze aparat încastrate, Aparataj încastrat | obligatoriu |
| Aparataj modular | Doze modulare, Module, Accesorii (calcul automat) | obligatoriu |
| Aparataj aplicat | Aparataj aplicat | obligatoriu |
| Tablou electric | Tablou electric | obligatoriu |
| Doze de legături | Doze legături | opțional |
| Cabluri, tuburi și jgheaburi | Cabluri și tuburi (m) | opțional |
| Corpuri de iluminat | Corpuri de iluminat (montaj) | opțional |
| Sistem fotovoltaic | Sistem fotovoltaic | opțional |
| Alte materiale | categoriile proprii (după cuvinte-cheie intră în grupul potrivit) | opțional |

- `sortGrouped`: grup → ordinea canonică în grup → ordinea utilizatorului. Se aplică la fiecare
  modificare a listei, la snapshot-ul lucrării și la pregătirea PDF-ului (și pentru lucrări vechi).
- Antetul grupului apare doar când grupul are ≥ 2 categorii (Materiale, Necesar, PDF, text).
- „Mută mai sus / jos” rămâne doar pentru categoriile proprii; dialogul de ordonare explică.

## 3. Marcă și model obligatorii

- Dialogul Marcă / model: la grupurile de aparataj și tablou, „Salvează” cere ambele câmpuri.
- Antetul categoriei arată „⚠ Marcă și model — obligatoriu” cât timp lipsesc.
- Necesar: card de avertizare cu categoriile bifate fără marcă/model (apăsare → dialog);
  „Salvează lucrarea”, „PDF materiale”, „Copiază” și „Trimite” refuză până se completează.
  Oferta de manoperă nu e blocată (nu depinde de marcă).

## 4. Client PF / PJ + CUI

- `Client.kind` („pf” / „pj”), `Client.cui`; `isValidCui` (prefix RO opțional, 2–10 cifre, cifră
  de control cu ponderile 7 5 3 2 1 7 5 3 2). CUI-ul e opțional, dar validat.
- Formularul de client (pagina Clienți și formularul lucrării): chip-uri „Persoană fizică” /
  „Persoană juridică”; la PJ apare câmpul CUI, iar CNP-ul se ascunde.
- Fișa clientului și lista arată tipul și CUI-ul; căutarea include CUI-ul.
- Lucrarea reține `cui`; PDF-urile (necesar + ofertă) îl afișează în cardul Beneficiar.

## 5. Fotovoltaic ascuns

- Setare `showPv` (implicit oprită): categoria „Sistem fotovoltaic” nu apare în Materiale,
  Necesar, dialoguri de gestionare, totaluri, snapshot; opțiunea „Sistem fotovoltaic” lipsește din
  asistent. Nimic nu se șterge — activarea din Setări readuce totul.

## 6. Materiale existente la client

- Liniile sunt grupate pe categorii (antet cu numele categoriei), în ordinea grupurilor.
- Selectorul afișează grupurile și categoriile; potrivirea se face după cheie (rezistă la redenumiri).
- PDF / text: lista „puse la dispoziție de client” e grupată pe categorii, cu UM corectă (m / buc).
- Buton „Golește” în secțiune.

## 7. Teste (`V36Test.kt`)

Chei (exact / loose), `assignCatalogKeys`, migrări pe categorii redenumite, `sortGrouped`,
`groupSections`, `missingBrandCategories`, `visibleCategories`, JSON roundtrip (key / cui / kind),
`mergeWorkIntoCatalog`, `applyPlanToCatalog`, `subtractOwned` cu redenumire, `isValidCui`.

Flutter: NU e actualizat în această etapă (prioritate nativ).
