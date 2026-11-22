package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// Lint does not check this library module's translations and the existing
// SettingsTranslationsTest only guards common/src/main/res. These two strings are
// the title and message of MyCarAppScreen (a Car App Library screen), not a status
// line drivers see: mobile/src/main/AndroidManifest.xml removes MyCarAppService
// with tools:node="remove", so the released APK never displays them. The guard
// keeps shared's locale set consistent with common's, as requested in #1077.
class SharedTranslationsTest {
    private val res = File("src/main/res")
    private val localeDirName = Regex("values-[a-z]{2}(-r[A-Z]{2})?")
    // Positional (%1$s / %1$d) and non-positional (%s / %d) format specifiers.
    private val placeholder = Regex("%\\d+\\$[sd]|%[sd]")

    // The locales the project supports today (also shipped by the common module).
    // Keeping an explicit floor means the guard still fails loudly if every
    // values-xx directory is removed instead of silently iterating over nothing.
    private val supportedLocales = setOf(
        "values-ar", "values-es", "values-it", "values-ja", "values-ko",
        "values-ru", "values-tr", "values-uk", "values-zh-rCN", "values-zh-rTW"
    )

    private fun localeDirs(root: File): List<File> = root.listFiles { file ->
        file.isDirectory && file.name.matches(localeDirName)
    }?.toList().orEmpty().sortedBy { it.name }

    @Test fun sharedKeepsATranslationCopyForEveryProjectLocale() {
        // common is the canonical locale set (it is what the existing guard scans).
        // Keep shared in sync with it: a locale added to common but missing here would
        // make the two sets drift, which is what #1077 asks this guard to catch.
        val commonRes = File("../common/src/main/res")
        val projectLocales = localeDirs(commonRes).map { it.name }.toSet()
        val expected = supportedLocales + projectLocales
        val shared = localeDirs(res).map { it.name }.toSet()

        assertTrue("common res not found at ${commonRes.absolutePath}", projectLocales.isNotEmpty())
        assertTrue("shared has no values-xx directories, guard would pass vacuously", shared.isNotEmpty())
        assertTrue(
            "shared is missing a translation copy for: ${expected - shared} (found: $shared)",
            shared.containsAll(expected)
        )
    }

    @Test fun everySharedStringIsTranslatedWithTheSamePlaceholders() {
        val base = strings(File(res, "values"))
        assertTrue("values/strings.xml has no <string>, guard would pass vacuously", base.isNotEmpty())

        val locales = localeDirs(res)
        assertTrue("no values-xx directories, guard would pass vacuously", locales.isNotEmpty())
        for (locale in locales) {
            val translated = strings(locale)
            // Fails with the locale name and the list of missing keys.
            assertEquals(locale.name, emptySet<String>(), base.keys - translated.keys)
            for ((name, text) in base) {
                assertEquals(
                    "${locale.name}/$name",
                    placeholders(text),
                    placeholders(translated.getValue(name))
                )
            }
        }
    }

    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.toSet()

    private fun strings(folder: File): Map<String, String> = folder.listFiles { file -> file.extension == "xml" }!!
        .flatMap { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) }
                // translatable="false" strings are locale-independent and need no copy.
                .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
                .map { it.attributes.getNamedItem("name").nodeValue to it.textContent }
        }.toMap()
}
