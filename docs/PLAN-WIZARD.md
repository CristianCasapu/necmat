# Plan — Asistent pentru necesar nou (v1.35) — ✅ livrat 2026-09-07

Scop: la „+” în **Lucrări** (și din meniul ⋮ de pe Materiale) pornește un asistent în
pași care produce un necesar de start, apoi utilizatorul continuă manual, exact ca până acum.

## Pași și întrebări

1. **Tip proiect**: Instalație electrică / Sistem fotovoltaic.
2. **Electric**
   - Tip: rezidențial / comercial / industrial.
   - Branșament: monofazic / trifazic; putere aprobată ATR (kW, opțional, informativ).
   - Casă / apartament.
   - Încăperi: dormitoare, băi, bucătării, salon-living, alte încăperi (± butoane).
   - Aparataj: modular / clasic încastrat / aplicat (+ marcă și model opțional → setate pe categorii).
   - Starea instalației: **nouă de la zero** (dozele, tuburile, carcasa tabloului intră în PDF)
     sau existentă; bife independente: „dozele sunt deja montate”, „cablurile sunt deja trase”,
     „tabloul există și se păstrează”.
3. **Fotovoltaic**
   - kW panouri, putere panou (W), mono/trifazic, putere invertor, baterii (kWh), tip acoperiș,
     dificultate (130 / 140 / 150 €/kW), curs €→lei.
4. **Rezumat**: lista completă cu cantități + note; „Înlocuiește necesarul” sau „Adaugă peste”.

## Reguli de estimare (`WizardEstimator`, pur, testat)

- Pe încăpere: 2 prize (+2 pe bucătărie), 1 întrerupător, 1 punct de lumină; living primește și o lustră.
- Modular: doză 2 module per aparat; clasic: doze aparat separate priză/întrerupător; aplicat: fără doze.
- Doze de legături: una mică pe încăpere + una medie la casă/comercial; cleme 2/încăpere.
- Cabluri: 3x2.5 (20 m/încăpere, 30 la comercial), 3x1.5 (12 / 15 m), 3x4 pentru bucătărie,
  3x6 sau 5x6 pentru alimentarea tabloului; tuburi copex PVC (încastrat) sau copex metalic
  și jgheab (aparent/comercial). Modul de montaj al cablurilor se setează pe categorie
  (încastrat 5 lei/m, aparent 3 lei/m în ofertă).
- Tablou: circuite = prize (1/încăpere) + lumină (1/2 încăperi) + dedicate (2/bucătărie, 1 la băi);
  carcasa după numărul de module; MCB pe circuite, diferențial general, SPD; la trifazic
  separator 4P, diferențial 4P, MCB 3P, releu tensiune.
- Ce e bifat ca existent (doze / cabluri / tablou) nu se adaugă deloc.
- Comercial/industrial cu „modular” → trece automat pe aplicat (notă în rezumat).

## Fotovoltaic

- Categorie nouă **Sistem fotovoltaic** (migrare v12, aditivă): panouri, invertoare mono/tri
  pe trepte, baterii 5 kWh, structură (țiglă/tablă/terasă), șine, cleme, tablou DC + protecții
  DC, MC4, tablou AC + protecții AC, SPD AC, smart meter, împământare; cabluri solare,
  împământare și baterie în categoria de cabluri.
- Manoperă: linie proprie pe lucrare `kW × €/kW × curs` → câmp nou `Work.extraLabor`,
  adăugată la `laborQuote` și deci în oferta PDF.

## Model / persistență

- `Work.pdfIncludeBoxes: Boolean?` — null = după setarea globală; true la instalații noi.
- `Work.extraLabor: List<LaborLine>` — linii de manoperă fixe (PV).
- `Work.kind: String` — "electric" / "pv" (informativ).
- Toate în JSON-ul lucrărilor și în backup, cu valori implicite pentru fișierele vechi.
- În editor (ViewModel): aceleași trei valori, persistate în prefs, incluse la salvare,
  restaurate la încărcarea lucrării, golite la „Golește cantitățile”.

## UI

- Buton „+ Necesar nou” în Lucrări (FAB) și în meniul ⋮ pe Materiale.
- `WizardDialog.kt` — dialog în pași cu Înapoi / Continuă, rezumat cu totaluri.
- Necesar: panglică informativă când lucrarea are reguli speciale (doze în PDF, manoperă PV)
  cu buton de anulare.

## Teste (V35Test)

- Electric: 2 prize/1 întrerupător/1 bec per încăpere; bife existente elimină categoriile;
  trifazic aduce componente 4P/3P; comercial → aplicat + jgheab; carcasa după module.
- PV: număr panouri și string-uri, baterii, manoperă 130/140/150 × curs, mono vs tri.
- JSON roundtrip pentru câmpurile noi; `laborQuote` include `extraLabor`; `effectiveIncludeBoxes`.
