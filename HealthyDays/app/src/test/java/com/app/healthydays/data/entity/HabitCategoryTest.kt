package com.app.healthydays.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitCategoryTest {

    @Test
    fun hasNineCategories() {
        assertEquals(9, HabitCategory.entries.size)
    }

    @Test
    fun idsMatchEnumNames() {
        HabitCategory.entries.forEach { assertEquals(it.name, it.id) }
    }

    @Test
    fun idsAreUnique() {
        val ids = HabitCategory.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun nameResourcesAreUnique() {
        val res = HabitCategory.entries.map { it.nameResId }
        assertEquals(res.size, res.toSet().size)
    }

    @Test
    fun otherCategoryIsTheEntityDefault() {
        assertEquals(HabitCategory.OTHER.id, Habit(name = "x").categoryId)
        assertEquals(HabitCategory.OTHER.id, HabitCompletion(1, "x", "2026-01-01", false).categoryId)
    }

    @Test
    fun otherIsLast() {
        assertTrue(HabitCategory.entries.last() == HabitCategory.OTHER)
    }
}
