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

enum class AccessStatus{ACTIVE,PENDING,BLOCKED}
data class ServerAuthState(val loading:Boolean=true,val authenticated:Boolean=false,val accessStatus:AccessStatus=AccessStatus.ACTIVE,val email:String="",val displayName:String="",val role:String="user",val message:String?=null){val blocked:Boolean get()=accessStatus==AccessStatus.BLOCKED;val pending:Boolean get()=accessStatus==AccessStatus.PENDING;val isAdmin:Boolean get()=role=="admin"}

class ServerAuth(private val context:Context){
    private val prefs=EncryptedSharedPreferences.create(context,"roadrecord-server-auth",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private val _state=MutableStateFlow(ServerAuthState());val state=_state.asStateFlow()
    val installationId:String get()=prefs.getString("installationId",null)?:UUID.randomUUID().toString().also{prefs.edit().putString("installationId",it).apply()}
    val token:String? get()=prefs.getString("token",null)
    val syncPolicy:String get()=prefs.getString("syncPolicy","EVERY_STOP")?:"EVERY_STOP"
    val centralStopsChoiceMade:Boolean get()=prefs.getBoolean("centralStopsChoiceMade",false)
    val centralStopsConnected:Boolean get()=prefs.getBoolean("centralStopsConnected",false)
    fun markCentralStopsChoice(){prefs.edit().putBoolean("centralStopsChoiceMade",true).apply()}
    fun connectCentralStops(){prefs.edit().putBoolean("centralStopsConnected",true).apply()}

    suspend fun initialize(){
        val saved=token;if(saved.isNullOrBlank()){_state.value=ServerAuthState(loading=false);return}
        checkStatus()
    }
    suspend fun requestCode(email:String):Result<Unit> = runCatching{ServerApi.post("request-code.php",JSONObject().put("email",email.trim()).put("purpose","app"));_state.value=_state.value.copy(email=email.trim(),message="Az ellenőrzőkódot elküldtük.")}.onFailure{_state.value=_state.value.copy(message=it.message)}
    suspend fun verify(email:String,code:String):Result<Unit> = runCatching{
        val response=ServerApi.post("verify-code.php",JSONObject().put("email",email.trim()).put("code",code).put("purpose","app").put("installationId",installationId).put("deviceName","${Build.MANUFACTURER} ${Build.MODEL}").put("appVersion",BuildConfig.VERSION_NAME))
        val status=runCatching{AccessStatus.valueOf(response.optString("accessStatus","ACTIVE"))}.getOrDefault(AccessStatus.ACTIVE)
        prefs.edit().putString("token",response.getString("token")).putString("email",email.trim()).putString("syncPolicy",response.optString("syncPolicy","EVERY_STOP")).putLong("lastAllowedAt",System.currentTimeMillis()).apply()
        _state.value=ServerAuthState(loading=false,authenticated=status!=AccessStatus.BLOCKED,accessStatus=status,email=email.trim(),displayName=response.optString("displayName"),role=response.optString("role","user"))
    }.onFailure{_state.value=_state.value.copy(message=it.message)}
    suspend fun checkStatus():Boolean{
        val saved=token?:run{_state.value=ServerAuthState(loading=false);return false}
        return try{val response=ServerApi.get("status.php",saved);val status=runCatching{AccessStatus.valueOf(response.optString("accessStatus",if(response.optBoolean("allowed"))"ACTIVE" else "BLOCKED"))}.getOrDefault(AccessStatus.BLOCKED);val allowed=status!=AccessStatus.BLOCKED;if(allowed)prefs.edit().putLong("lastAllowedAt",System.currentTimeMillis()).putString("syncPolicy",response.optString("syncPolicy","EVERY_STOP")).apply();_state.value=ServerAuthState(loading=false,authenticated=allowed,accessStatus=status,email=response.optString("email",prefs.getString("email","").orEmpty()),displayName=response.optString("displayName"),role=response.optString("role","user"),message=when(status){AccessStatus.ACTIVE->null;AccessStatus.PENDING->"Várakozó állapot: munkát rögzíthetsz, de a Napló és az exportálás nem érhető el.";AccessStatus.BLOCKED->"A felhasználó le van tiltva."});allowed}catch(error:Exception){val grace=System.currentTimeMillis()-prefs.getLong("lastAllowedAt",0)<3*24*60*60*1000L;_state.value=ServerAuthState(loading=false,authenticated=grace,accessStatus=if(grace)AccessStatus.ACTIVE else AccessStatus.BLOCKED,email=prefs.getString("email","").orEmpty(),message=if(grace)"Offline mód – az engedély később újra ellenőrződik." else "Az engedély ellenőrzéséhez internetkapcsolat szükséges.");grace}
    }
    fun signOut(){prefs.edit().remove("token").remove("email").remove("lastAllowedAt").apply();_state.value=ServerAuthState(loading=false)}
    fun expireSession(){
        val email=prefs.getString("email","").orEmpty()
        prefs.edit().remove("token").remove("lastAllowedAt").apply()
        _state.value=ServerAuthState(loading=false,email=email,message="A munkamenet lejárt. Kérj új ellenőrzőkódot.")
    }
}

class SessionExpiredException(message:String):IllegalStateException(message)

object ServerApi{
    const val BASE="http://roadrecord.widor.nhely.hu/api/"
    suspend fun post(path:String,body:JSONObject,token:String?=null)=request(path,"POST",body,token)
    suspend fun get(path:String,token:String?=null)=request(path,"GET",null,token)
    private suspend fun request(path:String,method:String,body:JSONObject?,token:String?):JSONObject=withContext(Dispatchers.IO){
        val connection=(URL(BASE+path).openConnection() as HttpURLConnection).apply{requestMethod=method;connectTimeout=20_000;readTimeout=30_000;setRequestProperty("Accept","application/json");setRequestProperty("Content-Type","application/json; charset=utf-8");token?.let{setRequestProperty("Authorization","Bearer $it")};if(body!=null){doOutput=true;outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}}
        val status=connection.responseCode
        val text=(if(status in 200..299)connection.inputStream else connection.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
        val json=runCatching{JSONObject(text)}.getOrElse{throw IllegalStateException("A RoadRecord szerver hibás választ adott.")}
        if(status==HttpURLConnection.HTTP_UNAUTHORIZED)throw SessionExpiredException(json.optString("error","A munkamenet lejárt."))
        if(status !in 200..299||!json.optBoolean("ok"))throw IllegalStateException(json.optString("error","A szerverkérés nem sikerült."))
        json
    }
}
