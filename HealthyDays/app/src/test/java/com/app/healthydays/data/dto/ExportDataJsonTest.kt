package com.app.healthydays.data.dto

import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCompletion
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportDataJsonTest {

    private val sample = ExportData(
        habits = listOf(
            Habit(1, "Correr", "PHYSICAL_HEALTH", 0),
            Habit(2, "Leer \"mucho\" ñandú", "LEARNING", 1)
        ),
        days = listOf(Day("2026-11-01", 80), Day("2026-11-02", 0)),
        completions = listOf(
            HabitCompletion(1, "Correr", "2026-11-01", true, "PHYSICAL_HEALTH", 0),
            HabitCompletion(2, "Leer \"mucho\" ñandú", "2026-11-01", false, "LEARNING", 1)
        )
    )

    @Test
    fun toJson_containsTopLevelSections() {
        val json = ExportDataJson.toJson(sample)
        assertTrue(json.contains("\"habits\""))
        assertTrue(json.contains("\"days\""))
        assertTrue(json.contains("\"completions\""))
    }

    @Test
    fun toJson_isPrettyPrinted() {
        assertTrue(ExportDataJson.toJson(sample).contains("\n"))
    }

    @Test
    fun roundTrip_preservesAllData() {
        val restored = Gson().fromJson(ExportDataJson.toJson(sample), ExportData::class.java)
        assertEquals(sample, restored)
    }

    @Test
    fun roundTrip_emptyData() {
        val empty = ExportData(emptyList(), emptyList(), emptyList())
        val restored = Gson().fromJson(ExportDataJson.toJson(empty), ExportData::class.java)
        assertEquals(empty, restored)
    }

    @Test
    fun fromJson_invalidJson_throwsSyntaxException() {
        var thrown = false
        try {
            Gson().fromJson("esto no es json {", ExportData::class.java)
        } catch (e: JsonSyntaxException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun fromJson_emptyString_returnsNull() {
        // SettingsFragment.importDataFromFile lo trata como "Archivo vacío".
        assertNull(Gson().fromJson("", ExportData::class.java))
    }
}
