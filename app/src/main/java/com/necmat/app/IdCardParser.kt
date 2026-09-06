package com.necmat.app

/**
 * Rezultatul citirii unui act de identitate. Câmpurile `*Sure` spun dacă
 * valoarea a fost confirmată (CNP validat prin cifra de control, nume din MRZ
 * sau tipărit = MRZ, adresă cu structură recunoscută).
 */
data class IdScanResult(
    val surname: String = "",
    val givenNames: String = "",
    val cnp: String = "",
    val address: String = "",
    val surnameSure: Boolean = false,
    val givenSure: Boolean = false,
    val cnpSure: Boolean = false,
    val addressSure: Boolean = false
) {
    val fullName: String get() = listOf(surname, givenNames).filter { it.isNotBlank() }.joinToString(" ")
    val isEmpty: Boolean get() = surname.isBlank() && givenNames.isBlank() && cnp.isBlank() && address.isBlank()
}

/**
 * Parser pur pentru textul recunoscut de OCR pe cărțile de identitate românești
 * (formatul electronic 2021+ și formatul vechi cu MRZ). Ancora principală e
 * CNP-ul (cifră de control), a doua sursă e zona MRZ; numele și adresa se iau
 * de pe liniile de sub etichete. Nu depinde de Android — testabil pe JVM.
 */
object IdCardParser {

    private val labelWords = listOf(
        "nume", "surname", "nom", "last name", "prenume", "prenom", "given", "first name",
        "cnp", "pin", "sex", "cetatenie", "cetățenie", "nationality", "nationalite",
        "data nasterii", "data nașterii", "date of birth", "loc nastere", "loc naștere",
        "lieu de naissance", "place of birth", "domiciliu", "adresse", "address",
        "emis", "delivree", "issued", "valabilitate", "validite", "validity",
        "nr. document", "document no", "semnatura", "semnătura", "signature",
        "carte de identitate", "identity card", "carte d'identite", "romania", "românia",
        "roumanie", "seria", "data expirarii", "data expirării", "date of expiry"
    )

    private fun norm(s: String) = normalizeName(s)

    private fun isLabel(line: String): Boolean {
        val n = norm(line)
        return labelWords.any { n.contains(it) }
    }

    private fun isSurnameLabel(line: String): Boolean {
        val n = norm(line)
        val surname = n.contains("nume") || n.contains("surname") || n.contains("nom") || n.contains("last name")
        val given = n.contains("prenume") || n.contains("prenom") || n.contains("given") || n.contains("first")
        return surname && !given
    }

    private fun isGivenLabel(line: String): Boolean {
        val n = norm(line)
        return n.contains("prenume") || n.contains("prenom") || n.contains("given") || n.contains("first name")
    }

    private fun isAddressLabel(line: String): Boolean {
        val n = norm(line)
        return n.contains("domiciliu") || n.contains("adresse") || n.contains("address")
    }

    private fun isAddressEnd(line: String): Boolean {
        val n = norm(line)
        return n.contains("emis") || n.contains("issued") || n.contains("delivree") ||
            n.contains("valabil") || n.contains("validit") || n.contains("semnat") ||
            n.contains("signature") || isMrzLine(line)
    }

    /** Confuzii tipice OCR literă→cifră, aplicate doar pe secvențe numerice. */
    fun fixDigits(s: String): String = buildString {
        s.forEach { ch ->
            append(
                when (ch) {
                    'O', 'o', 'Q', 'D' -> '0'
                    'I', 'l', '|', 'i', '!' -> '1'
                    'Z', 'z' -> '2'
                    'S', 's' -> '5'
                    'B' -> '8'
                    'G' -> '6'
                    else -> ch
                }
            )
        }
    }

    /** Toate secvențele de 13 cifre valide ca CNP din text (după corectarea confuziilor). */
    private fun cnpCandidates(line: String): List<String> {
        val out = mutableListOf<String>()
        // tokenuri „aproape numerice”: cel puțin 9 cifre reale
        Regex("[0-9OoQDIl|i!ZzSsBG]{13,}").findAll(line.replace(" ", "")).forEach { m ->
            val raw = m.value
            if (raw.count { it.isDigit() } < 5) return@forEach
            val fixed = fixDigits(raw)
            for (i in 0..fixed.length - 13) {
                val c = fixed.substring(i, i + 13)
                if (isValidCnp(c)) out += c
            }
        }
        return out
    }

    private fun mrzNorm(line: String): String = line.uppercase()
        .replace(" ", "")
        .replace('«', '<').replace('‹', '<').replace('(', '<').replace('[', '<')

    private fun isMrzLine(line: String): Boolean {
        val n = mrzNorm(line)
        return n.length >= 20 && (n.contains("<<") || Regex("^[I1]D[R][O0]U").containsMatchIn(n) ||
            Regex("ROU[0-9OISB]{7}[MF<][0-9OISB]{7}").containsMatchIn(n))
    }

    /** (nume, prenume) din prima linie MRZ: IDROU + NUME<<PRENUME<PRENUME. */
    fun namesFromMrz(line: String): Pair<String, String>? {
        val n = mrzNorm(line)
        val m = Regex("^[I1]D[R][O0]U([A-Z<]+)$").find(n) ?: return null
        val body = m.groupValues[1]
        val parts = body.split("<<")
        if (parts.isEmpty() || parts[0].isBlank()) return null
        val surname = parts[0].replace('<', ' ').trim()
        val given = parts.getOrNull(1)?.replace('<', ' ')?.trim().orEmpty()
        return surname to given
    }

    /** CNP reconstruit din a doua linie MRZ: cifra de sex + data nașterii + câmpul opțional. */
    fun cnpFromMrz(line: String): String? {
        val n = mrzNorm(line)
        val m = Regex(
            "([A-Z0-9<]{9})([0-9OISB])ROU([0-9OISB]{6})([0-9OISB])([MF<])([0-9OISB]{6})([0-9OISB])([0-9OISB<]{7})([0-9OISB])?$"
        ).find(n) ?: return null
        val dob = fixDigits(m.groupValues[3])
        val opt = fixDigits(m.groupValues[8]).replace("<", "")
        if (opt.length < 7) return null
        val cnp = opt.substring(0, 1) + dob + opt.substring(1, 7)
        return if (isValidCnp(cnp)) cnp else null
    }

    /** Confuzii tipice OCR cifră→literă, aplicate pe nume. */
    private fun fixLetters(s: String): String = buildString {
        s.forEach { ch ->
            append(
                when (ch) {
                    '0' -> 'O'
                    '1' -> 'I'
                    '5' -> 'S'
                    '8' -> 'B'
                    '2' -> 'Z'
                    '6' -> 'G'
                    else -> ch
                }
            )
        }
    }

    private fun cleanName(raw: String): String = fixLetters(raw)
        .replace(Regex("[^\\p{L}\\-' ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun lettersOnly(s: String) = norm(s).replace(Regex("[^a-z]"), "")

    /** „CRISTIAN-COSTINEL” → „Cristian-Costinel”; „ADINA GEORGIANA” → „Adina Georgiana”. */
    fun titleCase(name: String): String = name.trim().lowercase().split(" ").filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.split("-").joinToString("-") { p -> p.replaceFirstChar { it.uppercaseChar() } }
        }

    /** Valoarea de sub o etichetă: prima linie care nu e etichetă și arată a nume. */
    private fun valueAfter(lines: List<String>, labelIdx: Int): String? {
        for (j in labelIdx + 1..minOf(labelIdx + 2, lines.lastIndex)) {
            val cand = lines[j]
            if (isLabel(cand) || isMrzLine(cand)) continue
            // cel mult 2 cifre (o literă confundată), altfel e o dată sau un număr, nu un nume
            if (cand.count { it.isDigit() } > 2) continue
            val cleaned = cleanName(cand)
            if (cleaned.count { it.isLetter() } >= 2) return cleaned
        }
        return null
    }

    /**
     * „Jud.DJ Loc.Exemplu (Mun.Calafat) Str.Exemplului nr.8” →
     * „Str. Exemplului nr. 8, Loc. Exemplu (Mun. Calafat), jud. DJ”.
     * Întoarce (adresă, structuratOk).
     */
    fun cleanAddress(raw: String): Pair<String, Boolean> {
        var s = raw.replace(Regex("\\s+"), " ").trim()
        if (s.isEmpty()) return "" to false
        // spațiu după prefixele abreviate lipite: Jud.DJ, Loc.X, Str.X, nr.8
        s = s.replace(Regex("(?i)\\b(Jud|Loc|Mun|Str|Bd|Nr|Bl|Sc|Et|Ap|Com|Sat|Or[sș]?)\\.(?=\\S)"), "$1. ")
        val jud = Regex("(?i)\\bJud\\.\\s*([A-Z]{1,2})\\b").find(s)?.groupValues?.get(1)?.uppercase()
        val paren = Regex("\\(([^)]*)\\)").find(s)?.groupValues?.get(1)?.trim()
        var body = s
        if (jud != null) body = body.replace(Regex("(?i)\\bJud\\.\\s*[A-Z]{1,2}\\b"), " ")
        if (paren != null) body = body.replace(Regex("\\([^)]*\\)"), " ")
        body = body.replace(Regex("\\s+"), " ").trim()
        val streetMatch = Regex(
            "(?i)\\b((?:Str|Bd|B-dul|Bulevardul|Calea|Aleea|Șos|Sos|Drum|Pia[țt]a|Intr)\\.?\\s.*)$"
        ).find(body)
        val street = streetMatch?.groupValues?.get(1)?.trim()
        val locality = (if (streetMatch != null) body.substring(0, streetMatch.range.first) else body).trim()
        if (jud == null && street == null && locality.isEmpty()) return s to false
        val locPart = buildString {
            append(locality.trim().trimEnd(','))
            if (paren != null && paren.isNotBlank()) append(" (").append(paren).append(")")
        }.trim()
        val parts = listOfNotNull(
            street?.takeIf { it.isNotBlank() },
            locPart.takeIf { it.isNotBlank() },
            jud?.let { "jud. $it" }
        )
        val ok = street != null && (locPart.isNotBlank() || jud != null)
        return parts.joinToString(", ") to ok
    }

    /**
     * Combină două citiri (cadre / treceri diferite): pe fiecare câmp câștigă
     * valoarea confirmată; dacă niciuna nu e confirmată, cea mai completă.
     */
    fun merge(a: IdScanResult, b: IdScanResult): IdScanResult {
        fun pick(av: String, aSure: Boolean, bv: String, bSure: Boolean): Pair<String, Boolean> = when {
            aSure && av.isNotBlank() -> av to true
            bSure && bv.isNotBlank() -> bv to true
            av.isBlank() -> bv to false
            bv.isBlank() -> av to false
            else -> (if (bv.length > av.length) bv else av) to false
        }
        val (s, ss) = pick(a.surname, a.surnameSure, b.surname, b.surnameSure)
        val (g, gs) = pick(a.givenNames, a.givenSure, b.givenNames, b.givenSure)
        val (c, cs) = pick(a.cnp, a.cnpSure, b.cnp, b.cnpSure)
        val (ad, ads) = pick(a.address, a.addressSure, b.address, b.addressSure)
        return IdScanResult(s, g, c, ad, ss, gs, cs, ads)
    }

    fun parse(rawLines: List<String>): IdScanResult {
        val lines = rawLines.map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return IdScanResult()

        // ---- MRZ ----
        var mrzSurname: String? = null
        var mrzGiven: String? = null
        var mrzCnp: String? = null
        lines.forEach { l ->
            if (mrzSurname == null) namesFromMrz(l)?.let { (s, g) -> mrzSurname = s; mrzGiven = g }
            if (mrzCnp == null) mrzCnp = cnpFromMrz(l)
        }

        // ---- CNP tipărit (preferat cel de lângă eticheta CNP) ----
        var printedCnp: String? = null
        val cnpLabelIdx = lines.indexOfFirst { norm(it).contains("cnp") || norm(it).contains("pin") }
        if (cnpLabelIdx >= 0) {
            for (j in cnpLabelIdx..minOf(cnpLabelIdx + 2, lines.lastIndex)) {
                cnpCandidates(lines[j]).firstOrNull()?.let { printedCnp = it }
                if (printedCnp != null) break
            }
        }
        if (printedCnp == null) {
            for (l in lines) {
                if (isMrzLine(l)) continue
                cnpCandidates(l).firstOrNull()?.let { printedCnp = it }
                if (printedCnp != null) break
            }
        }
        val cnp = printedCnp ?: mrzCnp ?: ""

        // ---- nume / prenume tipărite ----
        val surnameIdx = lines.indexOfFirst { isSurnameLabel(it) }
        val givenIdx = lines.indexOfFirst { isGivenLabel(it) }
        val printedSurname = if (surnameIdx >= 0) valueAfter(lines, surnameIdx) else null
        val printedGiven = if (givenIdx >= 0) valueAfter(lines, givenIdx) else null

        fun pick(printed: String?, mrz: String?): Pair<String, Boolean> {
            val p = printed?.takeIf { it.isNotBlank() }
            val m = mrz?.takeIf { it.isNotBlank() }
            return when {
                p != null && m != null ->
                    if (lettersOnly(p) == lettersOnly(m)) p to true   // tipăritul păstrează cratimele
                    else m to false                                    // MRZ-ul e mai sigur la litere
                p != null -> p to false
                m != null -> m to true
                else -> "" to false
            }
        }
        val (surname, surnameSure) = pick(printedSurname, mrzSurname)
        val (given, givenSure) = pick(printedGiven, mrzGiven)

        // ---- adresa (doar cartea veche) ----
        var address = ""
        var addressSure = false
        val addrIdx = lines.indexOfFirst { isAddressLabel(it) }
        if (addrIdx >= 0) {
            val collected = mutableListOf<String>()
            for (j in addrIdx + 1..minOf(addrIdx + 3, lines.lastIndex)) {
                val l = lines[j]
                if (isAddressEnd(l) || (isLabel(l) && !isAddressLabel(l))) break
                collected += l
            }
            if (collected.isNotEmpty()) {
                val (a, ok) = cleanAddress(collected.joinToString(" "))
                address = a
                addressSure = ok
            }
        }

        return IdScanResult(
            surname = titleCase(surname),
            givenNames = titleCase(given),
            cnp = cnp,
            address = address,
            surnameSure = surnameSure && surname.isNotBlank(),
            givenSure = givenSure && given.isNotBlank(),
            cnpSure = cnp.isNotBlank() && isValidCnp(cnp),
            addressSure = addressSure
        )
    }
}
