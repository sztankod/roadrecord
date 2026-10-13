package hu.roadrecord.app.weather

import hu.roadrecord.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherData(
    val tempC:Double,
    val feelsLikeC:Double,
    val conditionCode:Int,
    val conditionText:String,
    val locationName:String,
    val providerUpdated:String,
    val fetchedAt:Long=System.currentTimeMillis(),
)

object WeatherClient {
    const val MAX_AGE_MS=10*60_000L

    suspend fun current(latitude:Double,longitude:Double):WeatherData=withContext(Dispatchers.IO){
        val proxy=BuildConfig.WEATHER_PROXY_URL.trim()
        val key=BuildConfig.WEATHER_API_KEY.trim()
        require(proxy.isNotBlank()||key.isNotBlank()){"Az időjárás-szolgáltatás nincs konfigurálva."}
        val query=URLEncoder.encode("$latitude,$longitude","UTF-8")
        val endpoint=if(proxy.isNotBlank())"${proxy.trimEnd('/')}?q=$query" else "https://api.weatherapi.com/v1/current.json?key=${URLEncoder.encode(key,"UTF-8")}&q=$query&aqi=no&lang=hu"
        val connection=(URL(endpoint).openConnection() as HttpURLConnection).apply{connectTimeout=8_000;readTimeout=10_000;useCaches=false;setRequestProperty("Accept","application/json");setRequestProperty("User-Agent","RoadRecord-Android/${BuildConfig.VERSION_NAME}")}
        try{
            if(connection.responseCode !in 200..299)error("WeatherAPI HTTP ${connection.responseCode}")
            val root=JSONObject(connection.inputStream.bufferedReader().use{it.readText()})
            val current=root.getJSONObject("current");val location=root.getJSONObject("location");val condition=current.getJSONObject("condition")
            WeatherData(current.getDouble("temp_c"),current.getDouble("feelslike_c"),condition.getInt("code"),condition.optString("text"),location.optString("name"),current.optString("last_updated"))
        }finally{connection.disconnect()}
    }
}
