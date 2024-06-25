package com.example.myweather.data

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object FileUtils {
    private const val TAG = "FileUtils"

    fun readCitiesFromFile(context: Context, fileName: String): List<String> {
        val cities = mutableListOf<String>()
        try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.forEachLine { line ->
                val city = line.trim()
                if (city.isNotEmpty()) {
                    cities.add(city)
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Error reading cities from file: ${e.message}")
        }
        Log.d(TAG, "Cities loaded: $cities")
        return cities
    }
}
