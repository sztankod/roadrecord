package hu.roadrecord.app.auth

import android.content.Context
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import hu.roadrecord.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class ServerAuthState(val loading:Boolean=true,val authenticated:Boolean=false,val blocked:Boolean=false,val email:String="",val message:String?=null)

class ServerAuth(private val context:Context){
    private val prefs=EncryptedSharedPreferences.create(context,"roadrecord-server-auth",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private val _state=MutableStateFlow(ServerAuthState());val state=_state.asStateFlow()
    val installationId:String get()=prefs.getString("installationId",null)?:UUID.randomUUID().toString().also{prefs.edit().putString("installationId",it).apply()}
    val token:String? get()=prefs.getString("token",null)

    suspend fun initialize(){
        val saved=token;if(saved.isNullOrBlank()){_state.value=ServerAuthState(loading=false);return}
        checkStatus()
    }
    suspend fun requestCode(email:String):Result<Unit> = runCatching{ServerApi.post("request-code.php",JSONObject().put("email",email.trim()).put("purpose","app"));_state.value=_state.value.copy(email=email.trim(),message="Az ellenőrzőkódot elküldtük.")}.onFailure{_state.value=_state.value.copy(message=it.message)}
    suspend fun verify(email:String,code:String):Result<Unit> = runCatching{
        val response=ServerApi.post("verify-code.php",JSONObject().put("email",email.trim()).put("code",code).put("purpose","app").put("installationId",installationId).put("deviceName","${Build.MANUFACTURER} ${Build.MODEL}").put("appVersion",BuildConfig.VERSION_NAME))
        prefs.edit().putString("token",response.getString("token")).putString("email",email.trim()).putLong("lastAllowedAt",System.currentTimeMillis()).apply()
        _state.value=ServerAuthState(loading=false,authenticated=true,email=email.trim())
    }.onFailure{_state.value=_state.value.copy(message=it.message)}
    suspend fun checkStatus():Boolean{
        val saved=token?:run{_state.value=ServerAuthState(loading=false);return false}
        return try{val response=ServerApi.get("status.php",saved);val allowed=response.optBoolean("allowed");if(allowed)prefs.edit().putLong("lastAllowedAt",System.currentTimeMillis()).apply();_state.value=ServerAuthState(loading=false,authenticated=allowed,blocked=!allowed,email=response.optString("email",prefs.getString("email","").orEmpty()),message=if(allowed)null else "Ezt a RoadRecord-példányt letiltották.");allowed}catch(error:Exception){val grace=System.currentTimeMillis()-prefs.getLong("lastAllowedAt",0)<3*24*60*60*1000L;_state.value=ServerAuthState(loading=false,authenticated=grace,blocked=!grace,email=prefs.getString("email","").orEmpty(),message=if(grace)"Offline mód – az engedély később újra ellenőrződik." else "Az engedély ellenőrzéséhez internetkapcsolat szükséges.");grace}
    }
    fun signOut(){prefs.edit().remove("token").remove("email").remove("lastAllowedAt").apply();_state.value=ServerAuthState(loading=false)}
}

object ServerApi{
    const val BASE="http://roadrecord.widor.nhely.hu/api/"
    suspend fun post(path:String,body:JSONObject,token:String?=null)=request(path,"POST",body,token)
    suspend fun get(path:String,token:String?=null)=request(path,"GET",null,token)
    private suspend fun request(path:String,method:String,body:JSONObject?,token:String?):JSONObject=withContext(Dispatchers.IO){
        val connection=(URL(BASE+path).openConnection() as HttpURLConnection).apply{requestMethod=method;connectTimeout=20_000;readTimeout=30_000;setRequestProperty("Accept","application/json");setRequestProperty("Content-Type","application/json; charset=utf-8");token?.let{setRequestProperty("Authorization","Bearer $it")};if(body!=null){doOutput=true;outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}}
        val text=(if(connection.responseCode in 200..299)connection.inputStream else connection.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty();val json=runCatching{JSONObject(text)}.getOrElse{throw IllegalStateException("A RoadRecord szerver hibás választ adott.")};if(connection.responseCode !in 200..299||!json.optBoolean("ok"))throw IllegalStateException(json.optString("error","A szerverkérés nem sikerült."));json
    }
}
