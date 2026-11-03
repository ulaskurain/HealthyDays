package com.app.healthydays

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Comprueba que todas las traducciones están completas y que los textos con formato no se rompen.
 * Se ejecuta en JVM puro leyendo los XML de recursos (el directorio de trabajo es el módulo `app`).
 */
class StringResourcesTest {

    private val resDir = File("src/main/res")
    private val locales = listOf("values-es", "values-eu", "values-fr", "values-en")

    private data class Entry(val value: String, val translatable: Boolean)

    private fun load(folder: String): Map<String, Entry> {
        val file = File(resDir, "$folder/strings.xml")
        assertTrue("No existe ${file.path}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val el = nodes.item(i) as Element
            el.getAttribute("name") to Entry(el.textContent, el.getAttribute("translatable") != "false")
        }
    }

    private val default by lazy { load("values") }

    @Test
    fun everyLocaleContainsAllTranslatableDefaultStrings() {
        val required = default.filter { it.value.translatable }.keys
        locales.forEach { locale ->
            val missing = required - load(locale).keys
            assertTrue("Faltan en $locale: $missing", missing.isEmpty())
        }
    }

    @Test
    fun noLocaleHasEmptyStrings() {
        locales.forEach { locale ->
            val empty = load(locale).filter { it.value.value.isBlank() }.keys
            assertTrue("Cadenas vacías en $locale: $empty", empty.isEmpty())
        }
    }

    @Test
    fun aboutMessageKeepsVersionPlaceholderInEveryLanguage() {
        (listOf("values") + locales).forEach { folder ->
            val message = load(folder).getValue("about_healthy_days_message").value
            assertTrue("Falta %1\$s en $folder", message.contains("%1\$s"))
        }
    }

    @Test
    fun placeholdersMatchBetweenDefaultAndTranslations() {
        val regex = Regex("%\\d\\$[sd]|%[sd]")
        locales.forEach { locale ->
            val translated = load(locale)
            default.forEach { (key, entry) ->
                val t = translated[key] ?: return@forEach
                assertEquals(
                    "Placeholders distintos en $locale/$key",
                    regex.findAll(entry.value).map { it.value }.sorted().toList(),
                    regex.findAll(t.value).map { it.value }.sorted().toList()
                )
            }
        }
    }

    @Test
    fun categoryNamesExistInEveryLanguage() {
        val keys = listOf(
            "category_physical_health", "category_nutrition", "category_mental_wellbeing",
            "category_learning", "category_social", "category_productivity",
            "category_home", "category_hobbies", "category_other"
        )
        (listOf("values") + locales).forEach { folder ->
            val strings = load(folder)
            keys.forEach { assertTrue("Falta $it en $folder", strings.containsKey(it)) }
        }
    }

    @Test
    fun chosenLanguageMatchesEachLocale() {
        assertEquals("English", load("values-en").getValue("chosen_language").value)
        assertEquals("Español", load("values-es").getValue("chosen_language").value)
        assertEquals("Euskara", load("values-eu").getValue("chosen_language").value)
        assertEquals("Français", load("values-fr").getValue("chosen_language").value)
    }
}
