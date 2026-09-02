# Publicarea NecMat pe Google Play — pașii tăi

## 1. Contul de developer (o singură dată)
1. Intră pe https://play.google.com/console/signup cu contul tău Google.
2. Alege cont **personal**, plătește taxa unică de **25 USD** și finalizează
   verificarea identității (act de identitate; durează de la câteva ore la
   câteva zile).

## 2. Creează aplicația
1. Play Console → **Create app**.
2. Nume: `NecMat` · Limbă: Română · Tip: **App** · **Free**.
3. Bifează declarațiile și creează.

## 3. Fișa de magazin (Store listing)
- **Descriere scurtă**: `Necesarul de materiale pentru instalații electrice, cu PDF pentru magazin.`
- **Descriere lungă**: copiază lista de funcții din README-ul proiectului.
- **Iconiță**: `icon-512.png` (din acest folder).
- **Feature graphic**: `feature-graphic-1024x500.png`.
- **Capturi de ecran**: minim 2 — fă-le direct din aplicație pe telefonul tău
  (ecranul Materiale și PDF-ul deschis arată cel mai bine).

## 4. Chestionarele obligatorii (secțiunea *App content*)
- **Privacy policy**: `https://github.com/CristianCasapu/necmat/blob/main/PRIVACY.md`
- **Data safety**: alege „No data collected” peste tot (aplicația nu
  colectează nimic — vezi PRIVACY.md).
- **Content rating**: chestionar scurt → utilitate, fără conținut sensibil →
  rating „Everyone / PEGI 3”.
- **Target audience**: 18+ (aplicație profesională).
- **Ads**: No.

## 5. Încarcă aplicația
1. **Testing → Internal testing → Create release**.
2. La *App integrity* alege **Play App Signing** (recomandat: lasă Google să
   gestioneze cheia; la primul upload poți exporta/încărca cheia noastră dacă
   întreabă — sau pur și simplu continuă cu cheia generată de Google).
3. Încarcă fișierul **`NecMat-play.aab`** (din acest folder / din release-ul
   GitHub v1.6).
4. Release notes: copiază noutățile din release-ul GitHub.
5. Salvează → Review → **Start rollout to Internal testing**.
6. Adaugă-ți emailul ca tester, instalează de pe link-ul de testare, verifică
   totul, apoi **Promote release → Production**.

## 6. Versiunile viitoare
- Eu îți compilez la fiecare versiune și `NecMat-play.aab` (atașat la
  release-urile GitHub). Tu doar îl încarci în Play Console la
  **Production → Create release**. Play Store actualizează automat telefoanele.

## De reținut
- Varianta din Play **nu are** butonul „Caută actualizări” — Play face
  update-urile (politica Google interzice self-update-ul).
- Varianta de pe GitHub/electroprep.ro rămâne cu self-update.
- Cele două variante au aceeași semnătură doar dacă la Play App Signing
  încarci cheia noastră (`necmat-release.keystore`); altfel, cine trece de pe
  APK pe Play trebuie să dezinstaleze o dată aplicația.
