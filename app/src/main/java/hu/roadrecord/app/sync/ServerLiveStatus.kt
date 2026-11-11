package hu.roadrecord.app.sync

import android.content.Context
import hu.roadrecord.app.BuildConfig
import hu.roadrecord.app.RoadRecordApplication
import hu.roadrecord.app.auth.ServerApi
import hu.roadrecord.app.data.EventType
import hu.roadrecord.app.repository.PlanOrdering
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ServerLiveStatus{
 suspend fun send(context:Context,dayId:Long)=withContext(Dispatchers.IO){
  val app=context.applicationContext as RoadRecordApplication;val token=app.auth.token?:return@withContext
  val dao=app.database.dao();val day=dao.daysNow().firstOrNull{it.day.id==dayId};val places=dao.placesNow();val byId=places.associateBy{it.id};val settings=dao.settings()
  val plans=PlanOrdering.ordered(dao.plansNow(dayId),places,dao.tourOrderGroupsNow());val events=day?.events.orEmpty();val next=plans.firstOrNull{!it.visited}?.let{byId[it.placeId]}
  val activeVisit=dao.activeVisit(dayId)
  val body=JSONObject().put("appVersion",BuildConfig.VERSION_NAME).put("workOpen",events.any{it.type==EventType.WORK_START}&&!events.any{it.type==EventType.WORK_END}).put("workDayId",dayId).put("workDate",day?.day?.date).put("tripOpen",events.lastOrNull()?.type==EventType.TRIP_START).put("workStartedAt",events.firstOrNull{it.type==EventType.WORK_START}?.timestamp).put("tripStartedAt",events.firstOrNull{it.type==EventType.TRIP_START}?.timestamp).put("completedStops",plans.count{it.visited}).put("totalStops",plans.size).put("distanceMeters",day?.trips?.sumOf{it.distanceMeters}?:0.0).put("currentPlaceName",settings?.currentPlaceId?.let{byId[it]?.name}).put("currentPlaceArrivedAt",activeVisit?.arrivalTime).put("nextPlaceName",next?.name).put("nextPlaceAddress",next?.officialAddress)
  runCatching{ServerApi.post("heartbeat.php",body,token)}
 }
}
