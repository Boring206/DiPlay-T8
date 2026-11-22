package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// Lint does not check this library module's translations, so Settings copy is guarded here.
class SettingsTranslationsTest {
    private val res = File("src/main/res")
    private val placeholder = Regex("%\\d+\\$[sd]")

    @Test fun everySettingsStringIsTranslatedWithTheSamePlaceholders() {
        val base = strings(File(res, "values")).filterKeys { it.startsWith("settings_") }
        val locales = res.listFiles { file -> file.name.matches(Regex("values-[a-z]{2}(-r[A-Z]{2})?")) }!!
        for (locale in locales) {
            val translated = strings(locale)
            assertEquals(locale.name, emptySet<String>(), base.keys - translated.keys)
            for ((name, text) in base) {
                assertEquals("${locale.name}/$name", placeholders(text), placeholders(translated.getValue(name)))
            }
        }
    }

    // AGENTS.md ("Adding or moving a setting") requires an experimental title to carry
    // " (experimental)" in English and the locale's own form in every translation. The ten
    // forms are listed there and mirrored by the table below. No locale uses a leading marker,
    // so a translated title must END with its form; the single non-title string (a description
    // with "(experimental)" mid-sentence) only has to contain it.
    private val experimentalSuffix = mapOf(
        "values-ar" to "(تجريبي)",
        "values-es" to "(experimental)",
        "values-it" to "(sperimentale)",
        "values-ja" to "（実験的機能）",
        "values-ko" to "(실험 기능)",
        "values-ru" to "(экспериментально)",
        "values-tr" to "(deneysel)",
        "values-uk" to "(експериментально)",
        "values-zh-rCN" to "（实验性）",
        "values-zh-rTW" to "（實驗性）",
    )

    @Test fun experimentalTitlesUseTheirLocaleSuffix() {
        // Use every base string containing "(experimental)", not only settings_* ones: many
        // experimental titles (adb_cluster_activity_mode, iphone_corner_mask_mode, ...) have
        // no settings_ prefix and were not covered by the placeholder guard above.
        val base = strings(File(res, "values")).filterValues { "(experimental)" in it }
        val locales = res.listFiles { file -> file.name.matches(Regex("values-[a-z]{2}(-r[A-Z]{2})?")) }!!
        for (locale in locales) {
            val suffix = experimentalSuffix[locale.name]
                ?: error("No experimental suffix for ${locale.name}: add the locale's form here and to AGENTS.md")
            val translated = strings(locale)
            for ((name, text) in base) {
                // A string not yet translated falls back to the English base, which already
                // has the correct " (experimental)" suffix.
                val value = translated[name] ?: continue
                val message = "${locale.name}/$name: $value"
                if (text.endsWith("(experimental)")) {
                    assertTrue(message, value.trim().endsWith(suffix))
                } else {
                    assertTrue(message, suffix in value)
                }
            }
        }
    }

    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.toSet()

    private fun strings(folder: File): Map<String, String> = folder.listFiles { file -> file.extension == "xml" }!!
        .flatMap { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) }.map { it.attributes.getNamedItem("name").nodeValue to it.textContent }
        }.toMap()
}
