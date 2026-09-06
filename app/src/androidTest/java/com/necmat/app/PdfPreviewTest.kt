package com.necmat.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Generează PDF-uri de verificare vizuală cu date FICTIVE, în
 * Android/data/com.necmat.app/files/preview/ pe dispozitiv:
 *   adb pull /sdcard/Android/data/com.necmat.app/files/preview .
 * Rulare: ./gradlew connectedGithubDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class PdfPreviewTest {

    private fun sampleWork(): Work {
        // necesar realist: primele materiale din fiecare categorie, cantități variate
        var seed = 3
        val cats = Repo.defaultCatalog().mapIndexed { ci, c ->
            val take = when {
                c.name.startsWith("Module") -> 7
                c.name.contains("Tablou") -> 6
                else -> 3
            }
            val mats = c.materials.mapIndexed { i, m ->
                seed = (seed * 7 + 3) % 11
                if (i < take) m.copy(qty = 1 + seed) else m
            }
            val brand = if (c.name.startsWith("Module") || c.name.startsWith("Doze modulare"))
                c.copy(materials = mats, brand = "Gewiss", model = "Chorus")
            else c.copy(materials = mats)
            if (ci == 0) brand.copy(phase = "") else brand
        }.map { c -> c.copy(materials = c.materials.filter { it.qty > 0 }) }
            .filter { it.materials.isNotEmpty() }

        val moduleCat = cats.first { it.name.startsWith("Module") }
        val owned = listOf(
            OwnedMaterial(moduleCat.materials[0].name, 2, moduleCat.name),
            OwnedMaterial(moduleCat.materials[1].name, 1, moduleCat.name),
            OwnedMaterial("Obturator (modul fals)", 1, "Accesorii doze modulare (calcul automat)")
        )
        return Work(
            id = 1L,
            name = "Apartament 3 camere — instalație electrică completă, etaj 2",
            date = System.currentTimeMillis(),
            categories = cats,
            client = "Maria Exemplu",
            address = "Str. Exemplului nr. 8, bl. A2, ap. 14, Craiova, jud. Dolj",
            phone = "0733 000 111",
            owned = owned
        )
    }

    private fun copyOut(ctx: android.content.Context, r: PdfExporter.Result, dest: File) {
        File(File(ctx.cacheDir, "pdfs"), r.fileName).copyTo(dest, overwrite = true)
    }

    @Test
    fun generatePreviewPdfs() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(ctx.getExternalFilesDir(null), "preview").apply { mkdirs() }
        val work = sampleWork()
        val inst = Triple("Ion Exemplu", "0722 000 000", "Electro Exemplu SRL")

        // 1. necesar fără prețuri, cu materiale existente la client
        val prepared = work.copy(name = work.name + " [1]").shoppingList(autoAccessories = true, includeBoxes = false)
        copyOut(ctx, PdfExporter.export(ctx, prepared, inst.first, inst.second, inst.third),
            File(out, "1-necesar.pdf"))

        // 2. necesar cu prețuri parțiale, fără date instalator
        val priced = prepared.copy(categories = prepared.categories.map { c ->
            c.copy(materials = c.materials.mapIndexed { i, m ->
                m.copy(price = if (i % 2 == 0) 12.5 + i else 0.0)
            })
        }, owned = emptyList(), name = work.name + " [2]")
        copyOut(ctx, PdfExporter.export(ctx, priced), File(out, "2-necesar-preturi.pdf"))

        // 3. ofertă manoperă cu cheltuieli detaliate
        val quote = laborQuote(work, Repo.defaultLaborConfig())
            .copy(days = 3, helperPerDay = 250.0, travel = 120.0, food = 60.0, consumables = 90.0)
        copyOut(ctx, PdfExporter.exportLaborQuote(ctx, work.copy(name = work.name + " [3]"), quote, inst.first, inst.second, inst.third,
            detailExpenses = true), File(out, "3-oferta-detaliata.pdf"))

        // 4. ofertă manoperă cu cheltuieli incluse în preț
        copyOut(ctx, PdfExporter.exportLaborQuote(ctx, work.copy(name = work.name + " [4]"), quote, inst.first, inst.second, inst.third,
            detailExpenses = false), File(out, "4-oferta-simpla.pdf"))
    }
}
