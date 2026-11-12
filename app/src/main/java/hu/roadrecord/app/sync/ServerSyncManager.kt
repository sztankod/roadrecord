package hu.roadrecord.app.sync

import android.content.Context
import androidx.work.*
import hu.roadrecord.app.RoadRecordApplication
import hu.roadrecord.app.auth.ServerApi
import hu.roadrecord.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

object ServerSyncManager{
    private const val WORK_NAME="roadrecord-server-sync"
    fun enqueue(context:Context){val request=OneTimeWorkRequestBuilder<ServerSyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME,ExistingWorkPolicy.REPLACE,request)}
    suspend fun sync(context:Context):Boolean=withContext(Dispatchers.IO){
        val app=context.applicationContext as RoadRecordApplication;val token=app.auth.token?:return@withContext false
        val status=runCatching{ServerApi.get("status.php",token)}.getOrElse{return@withContext false};if(!status.optBoolean("allowed"))return@withContext false
        val dao=app.database.dao();val days=dao.daysNow();val places=dao.placesNow();val plans=dao.allPlansNow();val visits=dao.allVisitsNow();val fuelings=dao.allFuelingsNow();val periods=dao.periodsNow();val settings=dao.settings()?:AppSettings()
        val snapshot=JSONObject().put("schema",1).put("generatedAt",System.currentTimeMillis()).put("settings",JSONObject().put("hourlyRate",settings.hourlyRate).put("includeTravelInEarnings",settings.includeTravelInEarnings)).put("periods",JSONArray(periods.map{it.json()})).put("days",JSONArray(days.map{it.json()})).put("places",JSONArray(places.map{it.jsonWithPreviews()})).put("plans",JSONArray(plans.map{it.json()})).put("visits",JSONArray(visits.map{it.json()})).put("fuelings",JSONArray(fuelings.map{it.json()}))
        val gps=JSONArray();days.flatMap{it.trips}.forEach{trip->val points=dao.gpsPointsNow(trip.id);val step=maxOf(1,points.size/400);val sampled=points.filterIndexed{index,_->index%step==0||index==points.lastIndex};gps.put(JSONObject().put("tripId",trip.id).put("points",JSONArray(sampled.map{JSONObject().put("timestamp",it.timestamp).put("latitude",it.latitude).put("longitude",it.longitude).put("accuracy",it.accuracy.toDouble())})))};snapshot.put("gpsRoutes",gps)
        runCatching{ServerApi.post("sync.php",JSONObject().put("snapshot",snapshot),token)}.isSuccess
    }
}

class ServerSyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){override suspend fun doWork():Result{val app=applicationContext as RoadRecordApplication;if(app.auth.token.isNullOrBlank())return Result.success();return if(ServerSyncManager.sync(applicationContext))Result.success()else Result.retry()}}

private fun WorkPeriod.json()=JSONObject().put("id",id).put("startDate",startDate).put("endDate",endDate?:JSONObject.NULL).put("closedAt",closedAt?:JSONObject.NULL)
private fun DayWithEvents.json()=JSONObject().put("id",day.id).put("periodId",day.periodId).put("date",day.date).put("createdAt",day.createdAt).put("events",JSONArray(events.map{JSONObject().put("id",it.id).put("type",it.type.name).put("timestamp",it.timestamp)})).put("trips",JSONArray(trips.map{JSONObject().put("id",it.id).put("startEventId",it.startEventId).put("endEventId",it.endEventId?:JSONObject.NULL).put("distanceMeters",it.distanceMeters)}))
internal fun LocationPlace.jsonWithPreviews()=JSONObject().put("id",id).put("type",type.name).put("name",name).put("officialAddress",officialAddress).put("latitude",latitude?:JSONObject.NULL).put("longitude",longitude?:JSONObject.NULL).put("gateCode",encryptedGateCode?:JSONObject.NULL).put("note",note).put("active",active).put("gpsManuallyConfirmed",gpsManuallyConfirmed).put("recognitionRadiusMeters",recognitionRadiusMeters).put("defaultTourAnchor",defaultTourAnchor).put("defaultTourOrder",defaultTourOrder).put("photoPreview",photoPath.preview()).put("entrancePhotoPreview",entrancePhotoPath.preview())
private fun String?.preview():Any{val path=this?.takeIf{it.isNotBlank()}?:return JSONObject.NULL;val file=File(path);if(!file.isFile)return JSONObject.NULL;return runCatching{val source=BitmapFactory.decodeFile(path)?:return@runCatching JSONObject.NULL;val scale=minOf(1.0,640.0/maxOf(source.width,source.height));val bitmap=if(scale<1.0)Bitmap.createScaledBitmap(source,(source.width*scale).toInt().coerceAtLeast(1),(source.height*scale).toInt().coerceAtLeast(1),true)else source;val out=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,65,out);if(bitmap!==source)bitmap.recycle();source.recycle();"data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)}.getOrDefault(JSONObject.NULL)}
private fun DailyPlacePlan.json()=JSONObject().put("workDayId",workDayId).put("placeId",placeId).put("priority",priority.name).put("visited",visited).put("sortHint",sortHint?:JSONObject.NULL).put("lockedPosition",lockedPosition?:JSONObject.NULL).put("completionMode",completionMode?:JSONObject.NULL).put("completedAt",completedAt?:JSONObject.NULL).put("closestApproachMeters",closestApproachMeters?:JSONObject.NULL).put("recognitionDiagnostic",recognitionDiagnostic?:JSONObject.NULL)
private fun PlaceVisit.json()=JSONObject().put("id",id).put("workDayId",workDayId).put("placeId",placeId).put("arrivalTime",arrivalTime?:JSONObject.NULL).put("departureTime",departureTime?:JSONObject.NULL).put("distanceMeters",distanceMeters?:JSONObject.NULL).put("dwellDurationMillis",dwellDurationMillis?:JSONObject.NULL)
private fun Fueling.json()=JSONObject().put("id",id).put("workDayId",workDayId).put("timestamp",timestamp).put("latitude",latitude?:JSONObject.NULL).put("longitude",longitude?:JSONObject.NULL).put("locationName",locationName)
