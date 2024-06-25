package com.example.myweather.data

import android.content.Context
import android.util.Log

class InitialData {
    companion object {
        private const val TAG = "InitialData"

        fun populateDatabase(context: Context, cityDao: CityDao) {
            val cityNames = FileUtils.readCitiesFromFile(context, "database_main.txt")
            val chunkSize = 1000  // Разбиваем на части по 100 городов
            cityNames.chunked(chunkSize).forEach { chunk ->
                val cities = chunk.map { City(0, it) }.toTypedArray()
                cityDao.insertAll(*cities)
                Log.d(TAG, "Inserted cities: ${cities.joinToString(", ") { it.name }}")
            }
        }
    }
}
