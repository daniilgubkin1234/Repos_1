package com.example.myweather.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CityDao {
    @Query("SELECT * FROM cities")
    fun getAllCities(): List<City>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg cities: City)
}
