package com.necmat.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Clientul ca entitate separată (Etapa 0 din docs/PLAN-CALENDAR.md).
 * `name` = „Nume Prenume” într-un singur câmp, ca în lucrare. E-mailul și CNP-ul
 * sunt opționale și NU se copiază niciodată în lucrare / PDF / text.
 */
data class Client(
    val id: Long,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val email: String = "",
    val cnp: String = "",
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** v1.36: [KIND_PF] (persoană fizică, implicit) sau [KIND_PJ] (persoană juridică). */
    val kind: String = KIND_PF,
    /** v1.36: CUI-ul firmei (doar la persoană juridică; opțional, validat). */
    val cui: String = ""
) {
    /** Localitatea (ultimul segment după virgulă) — pentru listă. */
    val locality: String
        get() = address.split(",").map { it.trim() }.lastOrNull { it.isNotBlank() } ?: ""

    val isCompany: Boolean get() = kind == KIND_PJ
    val kindLabel: String get() = if (isCompany) "Persoană juridică" else "Persoană fizică"

    companion object {
        const val KIND_PF = "pf"
        const val KIND_PJ = "pj"
    }
}

private const val CUI_WEIGHTS = "753217532"

/** CUI curățat: majuscule, fără spații / puncte; prefixul RO e păstrat dacă există. */
fun normalizeCui(cui: String): String = cui.uppercase().filter { it.isLetterOrDigit() }

/**
 * Validează un CUI românesc: prefix „RO” opțional, 2–10 cifre, ultima fiind cifra de
 * control (ponderile 7 5 3 2 1 7 5 3 2 pe cifrele completate la 9 cu zerouri în față,
 * suma × 10 mod 11, iar 10 → 0). Gol = valid (câmp opțional).
 */
fun isValidCui(cui: String): Boolean {
    val n = normalizeCui(cui).removePrefix("RO")
    if (n.isEmpty()) return true
    if (n.length !in 2..10 || !n.all { it.isDigit() }) return false
    val body = n.dropLast(1).padStart(9, '0')
    var sum = 0
    for (i in 0 until 9) sum += (body[i] - '0') * (CUI_WEIGHTS[i] - '0')
    var control = (sum * 10) % 11
    if (control == 10) control = 0
    return control == n.last() - '0'
}

/** Cifra de control pentru un CUI (fără RO, fără ultima cifră) — pentru CUI-uri fictive în teste. */
fun cuiControlDigit(digits: String): Int {
    val body = digits.padStart(9, '0')
    var sum = 0
    for (i in 0 until 9) sum += (body[i] - '0') * (CUI_WEIGHTS[i] - '0')
    val c = (sum * 10) % 11
    return if (c == 10) 0 else c
}

/** Doar cifrele; prefixul internațional românesc (+40 / 0040) devine 0. */
fun normalizePhone(phone: String): String {
    var d = phone.filter { it.isDigit() }
    if (d.startsWith("0040")) d = "0" + d.drop(4)
    else if (d.startsWith("40") && d.length == 11) d = "0" + d.drop(2)
    return d
}

/** Numărul în format internațional pentru WhatsApp (fără +). */
fun phoneForWhatsApp(phone: String): String {
    val d = normalizePhone(phone)
    return if (d.startsWith("0") && d.length == 10) "4$d" else d
}

private const val CNP_WEIGHTS = "279146358279"

/**
 * Validează un CNP: 13 cifre, sex 1–9, dată plauzibilă și cifra de control
 * (ponderi 279146358279, rest 10 → 1).
 */
fun isValidCnp(cnp: String): Boolean {
    val s = cnp.trim()
    if (s.length != 13 || !s.all { it.isDigit() }) return false
    val sex = s[0] - '0'
    if (sex == 0) return false
    val month = s.substring(3, 5).toInt()
    val day = s.substring(5, 7).toInt()
    if (month !in 1..12 || day !in 1..31) return false
    var sum = 0
    for (i in 0 until 12) sum += (s[i] - '0') * (CNP_WEIGHTS[i] - '0')
    var control = sum % 11
    if (control == 10) control = 1
    return control == s[12] - '0'
}

/** Cifra de control pentru primele 12 cifre (folosit la generarea CNP-urilor fictive din teste). */
fun cnpControlDigit(first12: String): Int {
    var sum = 0
    for (i in 0 until 12) sum += (first12[i] - '0') * (CNP_WEIGHTS[i] - '0')
    val c = sum % 11
    return if (c == 10) 1 else c
}

/** Validare de formă a adresei de e-mail (câmp opțional: gol = valid). */
fun isValidEmail(email: String): Boolean {
    val e = email.trim()
    if (e.isEmpty()) return true
    return Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(e)
}

/** CNP mascat pentru listă: doar ultimele 4 cifre vizibile. */
fun maskCnp(cnp: String): String =
    if (cnp.length < 4) "" else "•".repeat(cnp.length - 4) + cnp.takeLast(4)

private fun nameKey(name: String) = normalizeName(name)

/**
 * Clientul din listă care „e același” cu cel dat: același telefon normalizat
 * (dacă există) sau același nume + aceeași adresă. Se ignoră clientul cu același id.
 */
fun findDuplicateClient(client: Client, list: List<Client>): Client? {
    val phone = normalizePhone(client.phone)
    val name = nameKey(client.name)
    val addr = normalizeName(client.address)
    return list.firstOrNull { c ->
        c.id != client.id && (
            (phone.isNotEmpty() && normalizePhone(c.phone) == phone) ||
                (name.isNotEmpty() && nameKey(c.name) == name && normalizeName(c.address) == addr)
            )
    }
}

/**
 * Extrage clienți unici din lucrările existente (migrare aditivă): cheia e
 * telefonul normalizat; fără telefon, numele + adresa. Lucrările fără client
 * și fără telefon sunt sărite. Întoarce clienții și lucrările cu `clientId` setat.
 */
fun clientsFromWorks(
    works: List<Work>,
    existing: List<Client>,
    newId: () -> Long,
    now: Long = System.currentTimeMillis()
): Pair<List<Client>, List<Work>> {
    val clients = existing.toMutableList()
    val outWorks = works.map { w ->
        if (w.clientId != null && clients.any { it.id == w.clientId }) return@map w
        if (w.client.isBlank() && w.phone.isBlank()) return@map w
        val probe = Client(0L, w.client.trim(), w.phone.trim(), w.address.trim())
        val match = findDuplicateClient(probe, clients)
        val target = match ?: Client(
            newId(), probe.name.ifBlank { "Client fără nume" }, probe.phone, probe.address,
            createdAt = w.date, updatedAt = now
        ).also { clients += it }
        w.copy(clientId = target.id)
    }
    return clients.toList() to outWorks
}

/** La ștergerea unui client, lucrările lui rămân, doar legătura dispare. */
fun unlinkClient(works: List<Work>, clientId: Long): List<Work> =
    works.map { if (it.clientId == clientId) it.copy(clientId = null) else it }

/** Lucrările unui client: legate prin id sau, pentru cele vechi, prin telefon. */
fun worksOfClient(works: List<Work>, client: Client): List<Work> {
    val phone = normalizePhone(client.phone)
    return works.filter { w ->
        w.clientId == client.id ||
            (w.clientId == null && phone.isNotEmpty() && normalizePhone(w.phone) == phone)
    }
}

/** Sugestii pentru completarea automată după ce s-a tastat numele. */
fun suggestClients(query: String, list: List<Client>, limit: Int = 5): List<Client> {
    val q = normalizeName(query)
    if (q.length < 2) return emptyList()
    val digits = query.filter { it.isDigit() }
    return list.filter { c ->
        nameKey(c.name).contains(q) ||
            (digits.length >= 3 && normalizePhone(c.phone).contains(digits))
    }.take(limit)
}

object ClientsRepo {
    private const val FILE = "necmat_clients.json"

    fun toJson(c: Client): JSONObject = JSONObject()
        .put("id", c.id).put("name", c.name).put("phone", c.phone)
        .put("address", c.address).put("email", c.email).put("cnp", c.cnp)
        .put("notes", c.notes).put("createdAt", c.createdAt).put("updatedAt", c.updatedAt)
        .put("kind", c.kind).put("cui", c.cui)

    fun fromJson(o: JSONObject): Client = Client(
        id = o.getLong("id"),
        name = o.optString("name", ""),
        phone = o.optString("phone", ""),
        address = o.optString("address", ""),
        email = o.optString("email", ""),
        cnp = o.optString("cnp", ""),
        notes = o.optString("notes", ""),
        createdAt = o.optLong("createdAt", 0L),
        updatedAt = o.optLong("updatedAt", 0L),
        kind = if (o.optString("kind", "") == Client.KIND_PJ) Client.KIND_PJ else Client.KIND_PF,
        cui = o.optString("cui", "")
    )

    fun listToJson(list: List<Client>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        return arr
    }

    fun listFromJson(arr: JSONArray?): List<Client> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { runCatching { fromJson(it) }.getOrNull() }
        }
    }

    fun load(context: Context): List<Client> {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return try {
            listFromJson(JSONArray(f.readText()))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, list: List<Client>) {
        File(context.filesDir, FILE).writeText(listToJson(list).toString())
    }
}
