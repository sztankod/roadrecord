package hu.roadrecord.app.repository

import hu.roadrecord.app.data.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.Instant
import android.content.Context
import hu.roadrecord.app.backup.BackupManager
import hu.roadrecord.app.service.TrackingService
import android.content.Intent

class RoadRecordRepository(private val dao:RoadRecordDao,private val context:Context){
 val days=dao.observeDays(); val periods=dao.observePeriods(); val places=dao.observePlaces(); val settings=dao.observeSettings().map{it?:AppSettings()}
 suspend fun ensureDefaults(){val current=dao.settings();if(current==null)dao.saveSettings(AppSettings())else{var updated=current;if(updated.dataResetVersion<1){dao.clearAllWorkDays();updated=updated.copy(dataResetVersion=1)};if(updated.overnightRepairVersion<1){repairAugustOvernightSession();updated=updated.copy(overnightRepairVersion=1)};if(updated!=current)dao.saveSettings(updated)};if(dao.activePeriod()==null)dao.insertPeriod(WorkPeriod(startDate=LocalDate.now().toString()))}
 suspend fun activeDay()=dao.openDay()
 suspend fun startWork(now:Long=System.currentTimeMillis()):Long {ensureDefaults();val existing=activeDay();if(existing!=null)return existing.day.id;val previous=dao.latestClosedDay();val p=dao.activePeriod()?:error("Nincs aktív időszak");val date=Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();val id=dao.insertDay(WorkDay(periodId=p.id,date=date));dao.insertEvent(WorkEvent(workDayId=id,type=EventType.WORK_START,timestamp=now));previous?.let{old->val copied=dao.plansNow(old.day.id).filter{val place=dao.place(it.placeId);place?.active==true&&place.type==PlaceType.CLIENT}.sortedBy{it.sortHint?:Int.MAX_VALUE};copied.forEachIndexed{index,plan->dao.upsertPlan(plan.copy(workDayId=id,visited=false,sortHint=index,lockedPosition=plan.lockedPosition?.let{index}))};dao.routeConfigNow(old.day.id)?.let{dao.saveRouteConfig(it.copy(workDayId=id))}};return id}
 private suspend fun repairAugustOvernightSession(){
  val zone=java.time.ZoneId.systemDefault()
  fun at(day:Int,hour:Int,minute:Int):Long = java.time.LocalDateTime.of(2026,8,day,hour,minute).atZone(zone).toInstant().toEpochMilli()
  dao.deleteWorkDaysByDates(listOf("2026-08-24","2026-08-25"))
  var period=dao.activePeriod()
  if(period==null){dao.insertPeriod(WorkPeriod(startDate="2026-08-24"));period=dao.activePeriod()!!}
  val id=dao.insertDay(WorkDay(periodId=period.id,date="2026-08-24",createdAt=at(24,22,0)))
  addEvent(id,EventType.WORK_START,at(24,22,0));addEvent(id,EventType.TRIP_START,at(24,23,40));addEvent(id,EventType.TRIP_END,at(25,6,18));addEvent(id,EventType.WORK_END,at(25,6,46))
 }
 suspend fun nextAction(dayId:Long,now:Long=System.currentTimeMillis()):EventType { val e=dao.events(dayId); val next=when(e.lastOrNull()?.type){null->EventType.WORK_START;EventType.WORK_START,EventType.TRIP_END->EventType.TRIP_START;EventType.TRIP_START->EventType.TRIP_END;EventType.WORK_END->throw IllegalStateException("A munkanap már lezárult")}; addEvent(dayId,next,now); return next }
 suspend fun endWork(dayId:Long,now:Long=System.currentTimeMillis()){addEvent(dayId,EventType.WORK_END,now)}
 suspend fun addEvent(dayId:Long,type:EventType,time:Long):Long { val candidate=dao.events(dayId)+WorkEvent(workDayId=dayId,type=type,timestamp=time); validate(candidate); val eventId=dao.insertEvent(WorkEvent(workDayId=dayId,type=type,timestamp=time)); when(type){EventType.TRIP_START->dao.insertTrip(Trip(workDayId=dayId,startEventId=eventId));EventType.TRIP_END->{dao.activeTrip(dayId)?.let{dao.updateTrip(it.copy(endEventId=eventId))}}EventType.WORK_END->runCatching{BackupManager.create(context,automatic=true)};else->Unit}; return eventId }
 suspend fun updateEvent(event:WorkEvent){val list=dao.events(event.workDayId).map{if(it.id==event.id)event else it};validate(list);dao.updateEvent(event)}
 suspend fun deleteEvent(event:WorkEvent){if(event.type==EventType.WORK_START){context.startService(Intent(context,TrackingService::class.java).setAction(TrackingService.ACTION_STOP));dao.deleteDay(event.workDayId)}else dao.deleteEvent(event)}
 private fun validate(events:List<WorkEvent>){val sorted=events.sortedBy{it.timestamp}; if(sorted!=events.sortedBy{it.timestamp})Unit; var working=false;var travelling=false; sorted.forEach{when(it.type){EventType.WORK_START->{require(!working){"Már van munkakezdés"};working=true};EventType.TRIP_START->{require(working&&!travelling){"Indulás csak aktív munkában lehetséges"};travelling=true};EventType.TRIP_END->{require(travelling){"A visszaérkezés nem előzheti meg az indulást"};travelling=false};EventType.WORK_END->{require(working&&!travelling){"Munka vége csak visszaérkezés után rögzíthető"};working=false}}}}
 fun observeDay(id:Long)=dao.observeDay(id); fun plans(id:Long)=dao.observePlans(id); fun points(id:Long)=dao.observePoints(id)
 suspend fun saveSettings(v:AppSettings)=dao.saveSettings(v)
 suspend fun savePlace(v:LocationPlace)=if(v.id==0L)dao.insertPlace(v) else {dao.updatePlace(v);v.id}
 suspend fun deletePlace(v:LocationPlace)=dao.deletePlace(v)
 suspend fun togglePlan(dayId:Long,placeId:Long,selected:Boolean){if(selected)dao.upsertPlan(DailyPlacePlan(dayId,placeId))else dao.deletePlan(dayId,placeId)}
 suspend fun savePlanOrder(dayId:Long,placeIds:List<Long>){val plans=dao.plansNow(dayId).associateBy{it.placeId};placeIds.forEachIndexed{i,id->plans[id]?.let{dao.upsertPlan(it.copy(sortHint=i))}}}
 suspend fun setPlanLock(dayId:Long,placeId:Long,position:Int?){dao.plansNow(dayId).firstOrNull{it.placeId==placeId}?.let{dao.upsertPlan(it.copy(lockedPosition=position))}}
 suspend fun setPlanVisited(dayId:Long,placeId:Long,visited:Boolean)=dao.setPlanVisited(dayId,placeId,visited)
 suspend fun recognizePlannedStops(dayId:Long,latitude:Double,longitude:Double,time:Long,accuracy:Float):LocationPlace?{var current:LocationPlace?=null;dao.plansNow(dayId).forEach{plan->val place=dao.place(plan.placeId)?.takeIf{it.active}?:return@forEach;val lat=place.latitude?:return@forEach;val lon=place.longitude?:return@forEach;val earth=6371000.0;val dLat=Math.toRadians(lat-latitude);val dLon=Math.toRadians(lon-longitude);val a=kotlin.math.sin(dLat/2).let{it*it}+kotlin.math.cos(Math.toRadians(latitude))*kotlin.math.cos(Math.toRadians(lat))*kotlin.math.sin(dLon/2).let{it*it};val distance=2*earth*kotlin.math.atan2(kotlin.math.sqrt(a),kotlin.math.sqrt(1-a));if(distance<=place.recognitionRadiusMeters){current=place;if(!plan.visited){dao.setPlanVisited(dayId,place.id,true);if(dao.visitCount(dayId,place.id)==0)dao.insertVisit(PlaceVisit(workDayId=dayId,placeId=place.id,arrivalTime=time,distanceMeters=distance))}}};dao.settings()?.let{if(it.currentPlaceId!=current?.id)dao.saveSettings(it.copy(currentPlaceId=current?.id))};return current}
 suspend fun bakeryPresence(latitude:Double,longitude:Double,accuracy:Float):Boolean?{if(dao.settings()?.automaticBakeryTrips!=true)return null;val place=dao.placeByName("Vekni pékség")?:return null;val lat=place.latitude?:return null;val lon=place.longitude?:return null;val earth=6371000.0;val dLat=Math.toRadians(lat-latitude);val dLon=Math.toRadians(lon-longitude);val a=kotlin.math.sin(dLat/2).let{it*it}+kotlin.math.cos(Math.toRadians(latitude))*kotlin.math.cos(Math.toRadians(lat))*kotlin.math.sin(dLon/2).let{it*it};val distance=2*earth*kotlin.math.atan2(kotlin.math.sqrt(a),kotlin.math.sqrt(1-a));return distance<=place.recognitionRadiusMeters+accuracy.coerceAtMost(35f)}
 suspend fun automaticBakeryTransition(dayId:Long,left:Boolean,time:Long){val last=dao.events(dayId).lastOrNull()?.type?:return;if(left&&(last==EventType.WORK_START||last==EventType.TRIP_END))addEvent(dayId,EventType.TRIP_START,time)else if(!left&&last==EventType.TRIP_START)addEvent(dayId,EventType.TRIP_END,time)}
 fun routeConfig(dayId:Long)=dao.observeRouteConfig(dayId)
 suspend fun saveRouteConfig(v:RoutePlanConfig)=dao.saveRouteConfig(v)
 suspend fun setImportant(p:DailyPlacePlan)=dao.upsertPlan(p.copy(priority=if(p.priority==Priority.NORMAL)Priority.IMPORTANT else Priority.NORMAL))
 suspend fun closePeriod(date:String){val p=dao.activePeriod()?:return;dao.updatePeriod(p.copy(endDate=date,closedAt=System.currentTimeMillis()));dao.insertPeriod(WorkPeriod(startDate=LocalDate.parse(date).plusDays(1).toString()))}
 suspend fun addGpsPoint(v:GpsPoint)=dao.insertGpsPoint(v)
 suspend fun activeTrip(dayId:Long)=dao.activeTrip(dayId)
 suspend fun seedDemo(){
  ensureDefaults()
  listOf("Budapesti telephely","Váci partner","Szokolyai ügyfél","Verőcei megálló","Kismarosi partner","Nagymarosi ügyfél").forEach{name->dao.placeByName(name)?.let{dao.deletePlace(it)}}
  val places=listOf(
   LocationPlace(name="Kismaros CBA",officialAddress="2623 Kismaros, Szokolyai út 3.",latitude=47.8263645,longitude=19.0133502),
   LocationPlace(name="Verőce CBA",officialAddress="2621 Verőce, Rákóczi út 33.",latitude=47.8251062,longitude=19.0357996),
   LocationPlace(name="Kis-Vác CBA",officialAddress="2600 Vác, Árpád út 87.",latitude=47.7911684,longitude=19.1170618),
   LocationPlace(name="Deákvári Főtér CBA",officialAddress="2600 Vác, Deákvári főtér 31.",latitude=47.7911285,longitude=19.1343748),
   LocationPlace(name="Újdeákvár CBA",officialAddress="2600 Vác, Radnóti Miklós út 9.",latitude=47.7946239,longitude=19.1284689),
   LocationPlace(name="Tizes CBA",officialAddress="2600 Vác, Széchenyi utca 9–11.",latitude=47.7794831,longitude=19.1295676),
   LocationPlace(name="Földváry CBA",officialAddress="2600 Vác, Kölcsey Ferenc utca 1.",latitude=47.7715718,longitude=19.1427245),
   LocationPlace(name="Nagymaros CBA",officialAddress="2626 Nagymaros, Magyar utca 21.",latitude=47.7904,longitude=18.9594),
   LocationPlace(name="Faludy Family",officialAddress="2627 Zebegény, Kossuth Lajos út 14.",latitude=47.8028199,longitude=18.9129961),
   LocationPlace(name="Kővér és Hal",officialAddress="2627 Zebegény, Kossuth Lajos út 2. (Mókus Söröző)",latitude=47.8016126,longitude=18.9116356),
   LocationPlace(name="Ipolygyöngye CBA",officialAddress="2635 Vámosmikola, Kossuth Lajos utca 4.",latitude=47.9782645,longitude=18.785902),
   LocationPlace(name="Zöld Paradicsom",officialAddress="2625 Kóspallag, Kálvária utca 6.",latitude=47.8799485,longitude=18.9319907),
   LocationPlace(name="Malomkert Fogadó",officialAddress="2628 Szob, Malomkert telep 09/8 hrsz.",latitude=47.8348025,longitude=18.8534678),
   LocationPlace(name="Saját bolt",officialAddress="1048 Budapest, Bőröndös utca 6.",latitude=47.5844316,longitude=19.1133149),
   LocationPlace(name="Kárpát Csemege",officialAddress="1133 Budapest, Dráva utca 22.",latitude=47.5256912,longitude=19.0576618),
   LocationPlace(name="Törökvészi Csemege",officialAddress="1025 Budapest, Törökvészi út 1/B.",latitude=47.5203253,longitude=19.0220787),
   LocationPlace(name="Mágnáskert Csemege",officialAddress="1025 Budapest, Csatárka út 58.",latitude=47.5298368,longitude=19.0099584),
   LocationPlace(name="Zugligeti Csemege",officialAddress="1121 Budapest, Zugligeti út 58.",latitude=47.5176752,longitude=18.9820225),
   LocationPlace(name="Szarvas Csemege",officialAddress="1125 Budapest, Szarvas Gábor út 8.",latitude=47.5128487,longitude=18.9945114),
   LocationPlace(name="Naphegy Csemege",officialAddress="1016 Budapest, Naphegy tér 3.",latitude=47.49287,longitude=19.031965),
   LocationPlace(name="Kányakapu Csemege",officialAddress="1116 Budapest, Budaörsi út 115.",latitude=47.4672293,longitude=19.0164961),
   LocationPlace(name="Sasadi Csemege",officialAddress="1118 Budapest, Sasadi út 83.",latitude=47.47343,longitude=19.0082498),
   LocationPlace(name="Sárkányölő öregotthon",officialAddress="2624 Szokolya, Fő út 44–46.",latitude=47.8680547,longitude=19.0058502),
   LocationPlace(name="Buzik",officialAddress="1065 Budapest, Nagymező utca 64.",latitude=47.5060452,longitude=19.0560832),
   LocationPlace(type=PlaceType.BAKERY,name="Vekni pékség",officialAddress="2624 Szokolya, Fő út 110.",latitude=47.8705247,longitude=19.00066,note="Home – a napi túraterv fix kiindulási és érkezési pontja")
  )
  places.forEach{sample->val existing=dao.placeByName(sample.name);if(existing==null)dao.insertPlace(sample)else dao.updatePlace(existing.copy(type=sample.type,officialAddress=sample.officialAddress,latitude=sample.latitude,longitude=sample.longitude))}
 }
}

data class DaySummary(val workMillis:Long,val travelMillis:Long,val localMillis:Long,val distanceMeters:Double,val earnings:Long)
fun DayWithEvents.summary(settings:AppSettings,now:Long=System.currentTimeMillis()):DaySummary{val sorted=events.sortedBy{it.timestamp};val start=sorted.firstOrNull{it.type==EventType.WORK_START}?.timestamp;val end=sorted.lastOrNull{it.type==EventType.WORK_END}?.timestamp?:if(start!=null)now else null;val work=if(start!=null&&end!=null)(end-start).coerceAtLeast(0) else 0;var travel=0L;var tripStart:Long?=null;sorted.forEach{when(it.type){EventType.TRIP_START->tripStart=it.timestamp;EventType.TRIP_END->{tripStart?.let{s->travel+=(it.timestamp-s).coerceAtLeast(0)};tripStart=null}else->Unit}};if(tripStart!=null)travel+=(now-tripStart!!).coerceAtLeast(0);val paid=if(settings.includeTravelInEarnings)work else (work-travel).coerceAtLeast(0);return DaySummary(work,travel,(work-travel).coerceAtLeast(0),trips.sumOf{it.distanceMeters},paid*settings.hourlyRate/3600000)}
