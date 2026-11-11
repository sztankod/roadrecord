package hu.roadrecord.app.sync

import android.content.Context
import android.util.Base64
import hu.roadrecord.app.RoadRecordApplication
import hu.roadrecord.app.auth.ServerApi
import hu.roadrecord.app.data.LocationPlace
import hu.roadrecord.app.data.PlaceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object CentralStopsManager{
    suspend fun upload(context:Context):Int=withContext(Dispatchers.IO){
        val app=context.applicationContext as RoadRecordApplication
        val token=app.auth.token?:error("Nincs bejelentkezve.")
        if(!app.auth.state.value.isAdmin)error("Csak adminisztrátor frissítheti a központi tárat.")
        val stops=app.database.dao().placesNow().map{it.jsonWithPreviews()}
        ServerApi.post("central-stops.php",JSONObject().put("stops",JSONArray(stops)),token).getInt("count")
    }

    suspend fun download(context:Context,central:Boolean):Int=withContext(Dispatchers.IO){
        val app=context.applicationContext as RoadRecordApplication
        val token=app.auth.token?:error("Nincs bejelentkezve.")
        val response=ServerApi.get(if(central)"central-stops.php" else "my-stops.php",token)
        val values=response.optJSONArray("stops")?:JSONArray()
        val existing=app.database.dao().placesNow()
        var changed=0
        for(index in 0 until values.length()){
            val item=values.optJSONObject(index)?:continue
            val name=item.optString("name").trim();if(name.isBlank())continue
            val address=item.optString("officialAddress").trim()
            val old=existing.firstOrNull{it.name.equals(name,true)&&it.officialAddress.equals(address,true)}
            val place=(old?:LocationPlace(name=name)).copy(
                type=runCatching{PlaceType.valueOf(item.optString("type","CLIENT"))}.getOrDefault(PlaceType.CLIENT),name=name,officialAddress=address,
                latitude=item.optNullableDouble("latitude"),longitude=item.optNullableDouble("longitude"),encryptedGateCode=item.optNullableString("gateCode"),note=item.optString("note"),active=item.optBoolean("active",true),gpsManuallyConfirmed=item.optBoolean("gpsManuallyConfirmed",false),recognitionRadiusMeters=item.optInt("recognitionRadiusMeters",40),defaultTourAnchor=item.optString("defaultTourAnchor","NONE"),defaultTourOrder=item.optInt("defaultTourOrder",0),
                photoPath=savePreview(context,item.optString("photoPreview"),old?.photoPath,"key"),entrancePhotoPath=savePreview(context,item.optString("entrancePhotoPreview"),old?.entrancePhotoPath,"entrance")
            )
            app.repository.savePlace(place);changed++
        }
        app.auth.markCentralStopsChoice();changed
    }

    private fun savePreview(context:Context,value:String,current:String?,kind:String):String?{if(!value.startsWith("data:image/"))return current;return runCatching{val bytes=Base64.decode(value.substringAfter(','),Base64.DEFAULT);val dir=File(context.filesDir,"photos").apply{mkdirs()};File(dir,"server_${kind}_${System.nanoTime()}.jpg").apply{writeBytes(bytes)}.absolutePath}.getOrDefault(current)}
    private fun JSONObject.optNullableDouble(key:String)=if(has(key)&&!isNull(key))optDouble(key)else null
    private fun JSONObject.optNullableString(key:String)=if(has(key)&&!isNull(key))optString(key).takeIf{it.isNotBlank()}else null
}
