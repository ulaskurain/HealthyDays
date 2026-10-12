package com.app.healthydays.data.dto

import com.google.gson.GsonBuilder

object ExportDataJson {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    fun toJson(data: ExportData): String {
        return gson.toJson(data)
    }
}