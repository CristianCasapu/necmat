# NecMat ⚡

Aplicație Android pentru electricieni: calculezi rapid necesarul de materiale
pentru o instalație electrică rezidențială și generezi PDF-ul pentru magazin.

## Funcții

- Listă de materiale organizată pe categorii (doze modulare, doze aparat,
  module, tablou electric, cabluri etc.), complet editabilă: adaugi, redenumești,
  ștergi, reordonezi — totul se salvează automat pe telefon
- Cantități cu butoane **+ / −** sau introducere directă
- Prețuri opționale per material, cu valoare totală calculată
- **Lucrări salvate** cu nume, dată și date client (client / adresă / telefon),
  cu duplicare și reîncărcare în editor
- **PDF profesional** cu tabel (Nr. / Denumire / Cant. / UM / P.U. / Valoare),
  salvat automat în Descărcări
- **Calcul automat de accesorii** la PDF: rame suport + rame ornament pentru
  fiecare doză modulară și obturatoare (priza dublă ocupă 2 module)
- Backup / restaurare a tuturor datelor (fișier JSON)
- Temă luminoasă / întunecată / după sistem
- **Actualizare din aplicație**: verifică GitHub Releases și instalează noua
  versiune direct

## Instalare

Descarcă cel mai recent `NecMat-vX.Y.apk` din
[Releases](https://github.com/CristianCasapu/necmat/releases) și deschide-l pe
telefon (acceptă „Instalare din surse necunoscute”). Actualizările ulterioare
se fac direct din aplicație (meniu ⋮ → „Caută actualizări”).

## Compilare

Proiect standard Android (Kotlin + Jetpack Compose, minSdk 26):

```
./gradlew assembleRelease
```

Necesită un `keystore.properties` + keystore propriu pentru semnare
(nu sunt incluse în repo) și `local.properties` cu `sdk.dir`.
