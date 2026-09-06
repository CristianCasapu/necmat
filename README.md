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
- **Clienți**: pagină proprie cu căutare, adăugare, editare, ștergere (cu
  anulare), e-mail și CNP opționale (CNP doar local, mascat, niciodată în PDF),
  fișă cu acțiuni rapide (sună / SMS / WhatsApp / e-mail / hartă), istoricul
  lucrărilor, „Lucrare nouă pentru acest client” și „Salvează în agenda
  telefonului”; același formular e folosit și la salvarea lucrării, cu sugestii
  din clienții existenți
- **Scanarea buletinului** (poză sau imagine din galerie): recunoaștere de
  text offline pe telefon (ML Kit), CNP validat prin cifra de control, nume și
  prenume de sub etichete sau din zona MRZ, adresa de domiciliu de pe cartea
  veche; totul apare într-un dialog de confirmare, editabil, înainte de a
  completa formularul. Poza nu se păstrează
- **Calendar de lucru și programări**: vizite, oferte, zile de execuție,
  revizii, cu client (din listă), adresă, dată, oră sau „toată ziua”, durată,
  notițe și legătură cu o lucrare; agendă grupată (Azi / Mâine / săptămâna
  aceasta / mai târziu / trecute), avertisment la suprapuneri, stări
  (programat / confirmat / finalizat / anulat); pe fiecare programare: sună,
  confirmare prin SMS sau WhatsApp cu mesaj configurabil, navighează, adaugă
  în calendarul telefonului, creează lucrare; din orice lucrare: „Programează”;
  vizualizări **Lună** (grilă cu buline pe tip, zilele pline evidențiate,
  apăsare lungă = programare nouă), **Săptămână** (7 zile cu numărul
  programărilor și orele ocupate) și **Agendă**
- **Remindere locale**: notificare înainte de fiecare programare (implicit
  60 min, reglabil per programare), rezumat opțional dimineața („Azi ai 3
  programări, prima la 09:00 la …”), reprogramate automat după repornirea
  telefonului; apăsarea notificării deschide programarea
- **Rafinări**: istoricul clientului combină lucrările și programările
  („Programează revizie” din fișă), agenda are căutare și filtre pe tip /
  active, statistica lunii, **PDF „Program săptămânal”** și **export .ics**
  al programărilor, culori configurabile pe tip de programare
- **PDF profesional** pentru furnizor și client: antet cu dată și referință,
  carduri Solicitant (instalator) / Beneficiar (client), tabel
  (Nr. / Denumire / Cant. / UM / P.U. / Valoare) cu marca pe fiecare categorie,
  paginare cu antet de continuare; salvat automat în Descărcări
- **Ofertă de manoperă (PDF)** cu prestator / beneficiar, desfășurător pe grupuri,
  cheltuieli detaliate sau incluse, total și spații de semnătură
- **Calcul automat de accesorii** la PDF: rame suport + rame ornament pentru
  fiecare doză modulară și obturatoare (priza dublă ocupă 2 module)
- **Materiale existente la client**: secțiune în pagina Necesar unde treci ce
  are deja clientul (module, siguranțe, rame); se scad din lista de cumpărături
  *după* calculul accesoriilor, deci nu apar obturatoare false; oferta de
  manoperă rămâne pe necesarul complet, iar PDF-ul le listează separat
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
