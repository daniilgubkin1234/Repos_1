package com.example.myweather.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myweather.data.WeatherResponse

class WeatherViewModel : ViewModel() {

    private val _weatherData = MutableLiveData<WeatherResponse>()
    val weatherData: LiveData<WeatherResponse> get() = _weatherData

    private val _cityName = MutableLiveData<String>()
    val cityName: LiveData<String> get() = _cityName

    fun setWeatherData(weatherResponse: WeatherResponse) {
        _weatherData.value = weatherResponse
    }

    fun setCityName(city: String) {
        _cityName.value = city
    }
}
