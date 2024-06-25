package com.example.myweather.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myweather.R
import com.example.myweather.databinding.FragmentHomeBinding
import com.example.myweather.data.WeatherResponse
import com.example.myweather.ui.network.WeatherApiService
import com.google.android.gms.location.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.lifecycle.Observer
import androidx.fragment.app.viewModels
import com.example.myweather.data.AppDatabase
import com.example.myweather.data.City
import com.example.myweather.data.CityDao

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val weatherViewModel: WeatherViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openweathermap.org/data/2.5/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val weatherApiService = retrofit.create(WeatherApiService::class.java)

    private lateinit var cityDao: CityDao
    private var cities: List<City> = listOf()
    private val TAG = "HomeFragment"
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        weatherViewModel.weatherData.observe(viewLifecycleOwner, Observer { weatherResponse ->
            updateWeatherUI(weatherResponse)
        })
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult
                for (location in locationResult.locations) {
                    handleNewLocation(location)
                }
            }
        }

        val db = AppDatabase.getDatabase(requireContext())
        cityDao = db.cityDao()

        CoroutineScope(Dispatchers.IO).launch {
            cities = cityDao.getAllCities()
            Log.d(TAG, "Loaded cities: ${cities.joinToString(", ") { it.name }}")
            withContext(Dispatchers.Main) {
                setupSearchCity()
            }
        }

        val searchCityView = binding.searchCity // используем binding.searchCity напрямую
        val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, cities)

        searchCityView.setAdapter(adapter)
        // Пример добавления OnClickListener для иконки отправки
        binding.iconSend.setOnClickListener {
            getCurrentLocation()
        }

        // Пример добавления OnClickListener для иконки поиска
        binding.iconSearch.setOnClickListener {

            // Добавьте здесь код для обработки нажатия на иконку поиска
        }
        binding.searchCity.setOnEditorActionListener(TextView.OnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                hideKeyboard()
                return@OnEditorActionListener true
            }
            false
        })
        binding.iconSearch.setOnClickListener {
            val city = binding.searchCity.text.toString().trim()
            if (city.isNotEmpty()) {
                fetchWeather(city)
            } else {
                Toast.makeText(context, "Введите название города!", Toast.LENGTH_SHORT).show()
            }
        }
        weatherViewModel.cityName.value?.let {
            fetchWeather(it)
        }

    }
    private fun setupSearchCity() {
        binding.searchCity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s.toString()
                if (text.isNotEmpty()) {
                    val filteredCities = cities.filter { it.name.startsWith(text, ignoreCase = true) }
                    val newAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, filteredCities.map { it.name })
                    binding.searchCity.setAdapter(newAdapter)
                    newAdapter.notifyDataSetChanged()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }
    private fun getCurrentLocation() {
        Log.d(TAG, "getCurrentLocation called")
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Permissions not granted, requesting permissions")
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Log.d(TAG, "Location services are disabled")
            showLocationErrorDialog()
            return
        }

        Log.d(TAG, "Requesting location updates")
        val locationRequest = LocationRequest.Builder(10000L)
            .setIntervalMillis(10000L)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    private fun handleNewLocation(location: android.location.Location) {
        Log.d(TAG, "New location received: ${location.latitude}, ${location.longitude}")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val cityName = addresses[0].locality ?: "Unknown city"
                Log.d(TAG, "City name: $cityName")
                Toast.makeText(context, "Current city: $cityName", Toast.LENGTH_SHORT).show()
                fetchWeather(cityName)
            } else {
                Log.d(TAG, "Unable to get city name")
                Toast.makeText(context, "Unable to get city name", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "Geocoder service failed: ${e.message}")
            Toast.makeText(context, "Geocoder service failed", Toast.LENGTH_SHORT).show()
        }
    }



    private fun fetchWeather(city: String) {
        lifecycleScope.launch {
            try {
                val response = weatherApiService.getWeather(city, "58f6f2701b3c01446fd3b0a8293a9e53", "metric")
                if (response.isSuccessful) {
                    response.body()?.let { weatherResponse ->
                        weatherViewModel.setWeatherData(weatherResponse)
                        weatherViewModel.setCityName(city)
                    }
                } else {
                    Toast.makeText(context, "Failed to get weather data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error fetching weather data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateWeatherUI(weather: WeatherResponse) {
        binding.apply {
            weatherIcon.setImageResource(getWeatherIcon(weather.weather[0].icon))
            temperature.text = "${weather.main.temp}°"
            weatherDescription.text = weather.weather[0].description.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            weatherDetails.text = getString(
                R.string.weather_details_text,
                weather.main.feels_like,
                weather.wind.speed,
                weather.main.pressure,
                weather.main.humidity
            )

            weatherBackground.setBackgroundResource(getWeatherBackgroundImage(weather.weather[0].icon))
        }
    }

    private fun getWeatherIcon(icon: String): Int {
        return when (icon) {
            "01d" -> R.drawable.sunny
            "01n" -> R.drawable.clear_night
            "02d", "02n" -> R.drawable.partly_cloudy
            "03d", "03n", "04d", "04n" -> R.drawable.cloudy
            "09d", "09n", "10d", "10n" -> R.drawable.rainy
            "11d", "11n" -> R.drawable.thunderstorm
            "13d", "13n" -> R.drawable.snow
            "50d", "50n" -> R.drawable.mist
            else -> R.drawable.default_weather
        }
    }

    private fun getWeatherBackgroundImage(icon: String): Int {
        return when (icon) {
            "01d" -> R.drawable.sunny_background_main
            "01n" -> R.drawable.clear_night_background2
            "02d", "02n" -> R.drawable.partly_cloudy_background
            "03d", "03n", "04d", "04n" -> R.drawable.cloudy_background
            "09d", "09n", "10d", "10n" -> R.drawable.rainy_background
            "11d", "11n" -> R.drawable.thunderstorm_background
            "13d", "13n" -> R.drawable.snow_background
            "50d", "50n" -> R.drawable.mist_background
            else -> R.drawable.default_background
        }
    }

    private fun showLocationErrorDialog() {
        Log.d(TAG, "Showing location error dialog")
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Location Error")
            .setMessage("Unable to get location. Please make sure location services are enabled and try again.")
            .setPositiveButton("Retry") { _, _ ->
                getCurrentLocation()
            }
            .setNegativeButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivityForResult(intent, LOCATION_SETTINGS_REQUEST_CODE)
            }
            .create()
        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOCATION_SETTINGS_REQUEST_CODE) {
            getCurrentLocation()
        }
    }

    companion object {
        private const val TAG = "HomeFragment"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val LOCATION_SETTINGS_REQUEST_CODE = 2
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Log.d(TAG, "Location permission granted")
                getCurrentLocation()
            } else {
                Log.d(TAG, "Location permission denied")
                Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _binding = null
    }
    private fun hideKeyboard() {
        val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }
}
