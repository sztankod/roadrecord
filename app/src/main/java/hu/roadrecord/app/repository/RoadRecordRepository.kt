package hu.roadrecord.app.repository

import hu.roadrecord.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.Instant
import android.content.Context
import hu.roadrecord.app.backup.BackupManager
import hu.roadrecord.app.backup.StopPdfManager
import hu.roadrecord.app.service.TrackingService
import android.content.Intent

class RoadRecordRepository(private val dao:RoadRecordDao,private val context:Context){
 private val planMutex=Mutex()
 val days=dao.observeDays(); val periods=dao.observePeriods(); val places=dao.observePlaces(); val tourOrderGroups=dao.observeTourOrderGroups(); val visits=dao.observeAllVisits(); val settings=dao.observeSettings().map{it?:AppSettings()}
 suspend fun ensureDefaults(){val current=dao.settings()?:AppSettings().also{dao.saveSettings(it)};var updated=current;if(updated.dataResetVersion<1){dao.clearAllWorkDays();updated=updated.copy(dataResetVersion=1)};if(updated.overnightRepairVersion<1){repairAugustOvernightSession();updated=updated.copy(overnightRepairVersion=1)};if(updated.historicalWorkImportVersion<1){importAugustHistoricalWork();updated=updated.copy(historicalWorkImportVersion=1)};dao.reopenDanglingTripsOnOpenDays();repairTripDistances();if(updated!=current)dao.saveSettings(updated);BackupManager.schedule(context,updated);if(dao.activePeriod()==null)dao.insertPeriod(WorkPeriod(startDate=LocalDate.now().toString()))}
 suspend fun activeDay()=dao.openDay()
 private fun workDate(timestamp:Long):String {
  val local=Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault())
  val date=if(local.hour<2)local.toLocalDate().minusDays(1) else local.toLocalDate()
  return date.toString()
 }
 suspend fun startWork(now:Long=System.currentTimeMillis()):Long {ensureDefaults();val existing=activeDay();if(existing!=null)return existing.day.id;dao.settings()?.let{if(it.currentPlaceId!=null||it.currentPlaceDistanceMeters!=null)dao.saveSettings(it.copy(currentPlaceId=null,currentPlaceDistanceMeters=null))};val previous=dao.latestClosedDay();val p=dao.activePeriod()?:error("Nincs aktív időszak");val date=workDate(now);val id=dao.insertDay(WorkDay(periodId=p.id,date=date));dao.insertEvent(WorkEvent(workDayId=id,type=EventType.WORK_START,timestamp=now));previous?.let{old->dao.routeConfigNow(old.day.id)?.let{dao.saveRouteConfig(it.copy(workDayId=id))}};return id}
 private suspend fun repairAugustOvernightSession(){
  val zone=java.time.ZoneId.systemDefault()
  fun at(day:Int,hour:Int,minute:Int):Long = java.time.LocalDateTime.of(2026,8,day,hour,minute).atZone(zone).toInstant().toEpochMilli()
  dao.deleteWorkDaysByDates(listOf("2026-08-24","2026-08-25"))
  var period=dao.activePeriod()
  if(period==null){dao.insertPeriod(WorkPeriod(startDate="2026-08-24"));period=dao.activePeriod()!!}
  val id=dao.insertDay(WorkDay(periodId=period.id,date="2026-08-24",createdAt=at(24,22,0)))
  addEvent(id,EventType.WORK_START,at(24,22,0));addEvent(id,EventType.TRIP_START,at(24,23,40));addEvent(id,EventType.TRIP_END,at(25,6,18));addEvent(id,EventType.WORK_END,at(25,6,46))
 }
 private suspend fun importAugustHistoricalWork(){
  val zone=java.time.ZoneId.systemDefault();fun at(day:Int,hour:Int,minute:Int)=java.time.LocalDateTime.of(2026,8,day,hour,minute).atZone(zone).toInstant().toEpochMilli()
  val periodId=dao.insertPeriod(WorkPeriod(startDate="2026-08-14",endDate="2026-08-15",closedAt=System.currentTimeMillis()))
  suspend fun work(workDate:String,start:Long,end:Long){val id=dao.insertDay(WorkDay(periodId=periodId,date=workDate,createdAt=start));dao.insertEvent(WorkEvent(workDayId=id,type=EventType.WORK_START,timestamp=start));dao.insertEvent(WorkEvent(workDayId=id,type=EventType.WORK_END,timestamp=end))}
  work("2026-08-14",at(15,0,45),at(15,8,30));work("2026-08-15",at(15,20,30),at(16,0,0))
 }
 private suspend fun repairTripDistances(){dao.allTrips().forEach{trip->val points=dao.gpsPointsNow(trip.id);var total=0.0;points.zipWithNext().forEach{(a,b)->val result=FloatArray(1);android.location.Location.distanceBetween(a.latitude,a.longitude,b.latitude,b.longitude,result);if(result[0] in 0f..1000f)total+=result[0]};if(kotlin.math.abs(total-trip.distanceMeters)>1.0)dao.updateTrip(trip.copy(distanceMeters=total))}}
 suspend fun nextAction(dayId:Long,now:Long=System.currentTimeMillis()):EventType { val e=dao.events(dayId); val next=when(e.lastOrNull()?.type){null->EventType.WORK_START;EventType.WORK_START->EventType.TRIP_START;EventType.TRIP_START->EventType.TRIP_END;EventType.TRIP_END->throw IllegalStateException("Egy munkában csak egy út rögzíthető");EventType.WORK_END->throw IllegalStateException("A munkanap már lezárult")}; addEvent(dayId,next,now); return next }
 suspend fun endWork(dayId:Long,now:Long=System.currentTimeMillis()){addEvent(dayId,EventType.WORK_END,now)}
 suspend fun addEvent(dayId:Long,type:EventType,time:Long):Long { val candidate=dao.events(dayId)+WorkEvent(workDayId=dayId,type=type,timestamp=time); validate(candidate);if(type==EventType.TRIP_START)optimizePlanAtDeparture(dayId);val eventId=dao.insertEvent(WorkEvent(workDayId=dayId,type=type,timestamp=time)); when(type){EventType.TRIP_START->dao.insertTrip(Trip(workDayId=dayId,startEventId=eventId));EventType.TRIP_END->{dao.activeTrip(dayId)?.let{dao.updateTrip(it.copy(endEventId=eventId))}}EventType.WORK_END->{runCatching{BackupManager.create(context,automatic=true,workDayId=dayId)};runCatching{StopPdfManager.request(context,StopPdfManager.TRIGGER_WORK_END)}};else->Unit}; return eventId }
 private suspend fun optimizePlanAtDeparture(dayId:Long)=planMutex.withLock{
  val places=dao.placesNow();val groups=dao.tourOrderGroupsNow();val byId=places.associateBy{it.id};val plans=PlanOrdering.ordered(dao.plansNow(dayId),places,groups);val stops=plans.filter{!it.visited}.mapNotNull{byId[it.placeId]}.filter{it.active&&it.type!=PlaceType.BAKERY&&it.type!=PlaceType.HOME}.toMutableList();if(stops.size<2)return@withLock
  val base=dao.routeConfigNow(dayId)?.startPlaceId?.let{byId[it]}?:places.firstOrNull{it.active&&(it.type==PlaceType.BAKERY||it.type==PlaceType.HOME)}?:return@withLock
  val fixed=plans.mapNotNull{p->p.lockedPosition?.let{p.placeId to it}}.toMap();val result=MutableList<LocationPlace?>(stops.size){null};fixed.forEach{(id,pos)->stops.firstOrNull{it.id==id}?.let{if(pos in result.indices)result[pos]=it}}
  val free=stops.filter{p->result.none{it?.id==p.id}}.toMutableList();var current=base;result.indices.forEach{i->if(result[i]==null){val next=free.minByOrNull{p->val out=FloatArray(1);android.location.Location.distanceBetween(current.latitude?:0.0,current.longitude?:0.0,p.latitude?:0.0,p.longitude?:0.0,out);out[0]}?:return@forEach;result[i]=next;free.remove(next);current=next}else current=result[i]!!}
  dao.updatePlanPositions(PlanOrdering.reorder(plans,places,result.filterNotNull().map{it.id},groups=groups))
 }
 suspend fun updateEvent(event:WorkEvent){val list=dao.events(event.workDayId).map{if(it.id==event.id)event else it};validate(list);dao.updateEvent(event)}
 suspend fun deleteEvent(event:WorkEvent){if(event.type==EventType.WORK_START){context.startService(Intent(context,TrackingService::class.java).setAction(TrackingService.ACTION_STOP));dao.deleteDay(event.workDayId)}else{dao.deleteEventAndReopenTrip(event);if(event.type==EventType.TRIP_END&&activeDay()?.day?.id==event.workDayId)context.startForegroundService(Intent(context,TrackingService::class.java).setAction(TrackingService.ACTION_START).putExtra(TrackingService.EXTRA_DAY,event.workDayId))}}
 private fun validate(events:List<WorkEvent>){val sorted=events.sortedBy{it.timestamp};require(sorted.count{it.type==EventType.TRIP_START}<=1){"Egy munkában csak egy út rögzíthető"};var working=false;var travelling=false; sorted.forEach{when(it.type){EventType.WORK_START->{require(!working){"Már van munkakezdés"};working=true};EventType.TRIP_START->{require(working&&!travelling){"Indulás csak aktív munkában lehetséges"};travelling=true};EventType.TRIP_END->{require(travelling){"A visszaérkezés nem előzheti meg az indulást"};travelling=false};EventType.WORK_END->{require(working&&!travelling){"Munka vége csak visszaérkezés után rögzíthető"};working=false}}}}
 fun observeDay(id:Long)=dao.observeDay(id); fun plans(id:Long)=combine(dao.observePlans(id),dao.observePlaces(),dao.observeTourOrderGroups()){plans,places,groups->PlanOrdering.ordered(plans,places,groups)}; fun points(id:Long)=dao.observePoints(id)
 suspend fun saveSettings(v:AppSettings)=dao.saveSettings(v)
 suspend fun savePlace(v:LocationPlace):Long{val previous=if(v.id==0L)null else dao.place(v.id);val normalized=if(v.tourOrderGroupId!=null&&previous?.tourOrderGroupId!=v.tourOrderGroupId){val next=dao.placesNow().filter{it.tourOrderGroupId==v.tourOrderGroupId}.maxOfOrNull{it.defaultTourOrder}?.plus(1)?:0;v.copy(defaultTourOrder=next)}else v;val id=if(normalized.id==0L)dao.insertPlace(normalized)else{dao.updatePlace(normalized);normalized.id};activeDay()?.let{applyDefaultTourAnchors(it.day.id)};runCatching{StopPdfManager.request(context,StopPdfManager.TRIGGER_CHANGE)};return id}
 suspend fun saveDefaultTourOrder(startIds:List<Long>,endIds:List<Long>)=planMutex.withLock{
  val starts=startIds.withIndex().associate{it.value to it.index}
  val ends=endIds.withIndex().associate{it.value to it.index}
  dao.placesNow().forEach{place->
   val anchor=when(place.id){in starts->"START";in ends->"END";else->"NONE"}
   val order=starts[place.id]?:ends[place.id]?:0
   if(place.defaultTourAnchor!=anchor||place.defaultTourOrder!=order)dao.updatePlace(place.copy(defaultTourAnchor=anchor,defaultTourOrder=order))
  }
  activeDay()?.let{applyDefaultTourAnchors(it.day.id)}
 }
 suspend fun createTourOrderGroup(name:String){val groups=dao.tourOrderGroupsNow();dao.insertTourOrderGroup(TourOrderGroup(name=name.trim(),sortOrder=groups.size));runCatching{StopPdfManager.request(context,StopPdfManager.TRIGGER_CHANGE)}}
 suspend fun renameTourOrderGroup(group:TourOrderGroup,name:String){dao.updateTourOrderGroup(group.copy(name=name.trim()));runCatching{StopPdfManager.request(context,StopPdfManager.TRIGGER_CHANGE)}}
 suspend fun deleteTourOrderGroup(group:TourOrderGroup)=planMutex.withLock{dao.detachTourOrderGroup(group.id);dao.deleteTourOrderGroup(group);dao.tourOrderGroupsNow().forEachIndexed{i,g->if(g.sortOrder!=i)dao.updateTourOrderGroup(g.copy(sortOrder=i))};activeDay()?.let{applyDefaultTourAnchors(it.day.id)};runCatching{StopPdfManager.request(context,StopPdfManager.TRIGGER_CHANGE)}}
 suspend fun moveTourOrderGroup(groupId:Long,delta:Int){val groups=dao.tourOrderGroupsNow().toMutableList();val from=groups.indexOfFirst{it.id==groupId};val to=from+delta;if(from>=0&&to in groups.indices){val item=groups.removeAt(from);groups.add(to,item);groups.forEachIndexed{i,g->dao.updateTourOrderGroup(g.copy(sortOrder=i))};activeDay()?.let{applyDefaultTourAnchors(it.day.id)};runCatching{StopPdfManager.request(context,StopPdfManager.TRIGGER_CHANGE)}}}
 suspend fun saveTourOrderGroups(assignments:Map<Long,List<Long>>)=planMutex.withLock{val positions=assignments.flatMap{(group,ids)->ids.mapIndexed{i,id->id to (group to i)}}.toMap();dao.placesNow().forEach{place->val value=positions[place.id];val updated=place.copy(tourOrderGroupId=value?.first,defaultTourAnchor="NONE",defaultTourOrder=value?.second?:0);if(updated!=place)dao.updatePlace(updated)};activeDay()?.let{applyDefaultTourAnchors(it.day.id)};runCatching{StopPdfManager.request(context,StopPdfManager.TRIGGER_CHANGE)}}
 suspend fun deletePlace(v:LocationPlace){dao.deletePlace(v);runCatching{StopPdfManager.request(context,StopPdfManager.TRIGGER_CHANGE)}}
 suspend fun togglePlan(dayId:Long,placeId:Long,selected:Boolean)=planMutex.withLock{
  if(selected){val existing=dao.plansNow(dayId);dao.upsertPlan(DailyPlacePlan(dayId,placeId,sortHint=existing.size));reapplyPreviousLocks(dayId);applyDefaultTourAnchors(dayId)}
  else{dao.deletePlan(dayId,placeId);reapplyPreviousLocks(dayId);applyDefaultTourAnchors(dayId)}
 }
 private suspend fun applyDefaultTourAnchors(dayId:Long){
  val plans=dao.plansNow(dayId);if(plans.isEmpty())return
  val places=dao.placesNow();val placeById=places.associateBy{it.id};val groups=dao.tourOrderGroupsNow();val arranged=PlanOrdering.ordered(plans,places,groups)
  dao.updatePlanPositions(arranged.mapIndexed{i,plan->val fixed=placeById[plan.placeId]?.tourOrderGroupId!=null||placeById[plan.placeId]?.defaultTourAnchor in setOf("START","END");plan.copy(sortHint=i,lockedPosition=if(fixed||plan.lockedPosition!=null)i else null)})
 }
 private suspend fun reapplyPreviousLocks(dayId:Long){
  val previous=dao.previousDayPlans(dayId).sortedBy{it.sortHint?:Int.MAX_VALUE};if(previous.isEmpty())return
  val current=dao.plansNow(dayId).sortedBy{it.sortHint?:Int.MAX_VALUE};if(current.isEmpty())return
  val previousLocked=previous.filter{it.lockedPosition!=null};if(previousLocked.isEmpty())return
  val suffixStart=(previous.size-previousLocked.size).coerceAtLeast(0)
  val suffixIds=previousLocked.filter{(it.lockedPosition?:-1)>=suffixStart}.sortedBy{it.lockedPosition}.map{it.placeId}
  val selectedSuffix=suffixIds.filter{id->current.any{it.placeId==id}}
  val assigned=mutableMapOf<Long,Int>();selectedSuffix.forEachIndexed{i,id->assigned[id]=current.size-selectedSuffix.size+i}
  val occupied=assigned.values.toMutableSet()
  previousLocked.filter{it.placeId !in assigned}.sortedBy{it.lockedPosition}.forEach{old->
   if(current.none{it.placeId==old.placeId})return@forEach
   val desired=(old.lockedPosition?:return@forEach).coerceIn(0,current.lastIndex)
   val free=(0..current.lastIndex).filterNot{it in occupied}.minByOrNull{kotlin.math.abs(it-desired)}?:return@forEach
   assigned[old.placeId]=free;occupied+=free
  }
  val reordered=MutableList<DailyPlacePlan?>(current.size){null}
  current.forEach{plan->assigned[plan.placeId]?.let{reordered[it]=plan}}
  val free=current.filter{it.placeId !in assigned}.iterator()
  reordered.indices.forEach{i->if(reordered[i]==null&&free.hasNext())reordered[i]=free.next()}
  dao.updatePlanPositions(reordered.filterNotNull().mapIndexed{i,plan->plan.copy(sortHint=i,lockedPosition=assigned[plan.placeId])})
 }
 suspend fun savePlanOrder(dayId:Long,placeIds:List<Long>,unlockedPlaceId:Long?=null)=planMutex.withLock{dao.updatePlanPositions(PlanOrdering.reorder(dao.plansNow(dayId),dao.placesNow(),placeIds,unlockedPlaceId,dao.tourOrderGroupsNow()))}
 suspend fun setPlanLock(dayId:Long,placeId:Long,position:Int?)=planMutex.withLock{val ordered=PlanOrdering.ordered(dao.plansNow(dayId),dao.placesNow(),dao.tourOrderGroupsNow());val fullIndex=ordered.indexOfFirst{it.placeId==placeId};if(fullIndex>=0){val updated=ordered.mapIndexed{i,p->p.copy(sortHint=i,lockedPosition=if(p.placeId==placeId){if(position==null)null else i}else if(p.lockedPosition!=null)i else null)};dao.updatePlanPositions(updated)}}
 suspend fun setPlanVisited(dayId:Long,placeId:Long,visited:Boolean){
  val existing=dao.plansNow(dayId).firstOrNull{it.placeId==placeId}
  if(visited){
   val completedAt=System.currentTimeMillis()
   dao.setPlanVisited(dayId,placeId,true,"MANUAL",completedAt)
   if(dao.visitCount(dayId,placeId)==0)dao.insertVisit(PlaceVisit(workDayId=dayId,placeId=placeId,arrivalTime=completedAt,departureTime=completedAt,dwellDurationMillis=0))
  }else{
   if(existing?.completionMode=="MANUAL")existing.completedAt?.let{dao.deleteManualVisit(dayId,placeId,it)}
   dao.setPlanVisited(dayId,placeId,false,null,null)
  }
 }
 suspend fun nextPlannedStop(dayId:Long,excludePlaceId:Long?=null):LocationPlace?{val places=dao.placesNow();val byId=places.associateBy{it.id};return PlanOrdering.ordered(dao.plansNow(dayId),places,dao.tourOrderGroupsNow()).filter{!it.visited&&it.placeId!=excludePlaceId}.firstNotNullOfOrNull{byId[it.placeId]?.takeIf{place->place.active}}}
 suspend fun automaticVisitDelayMillis():Long=(dao.settings()?.automaticVisitDelaySeconds?:30).coerceIn(0,300)*1000L
 suspend fun recordRecognitionDiagnostic(dayId:Long,placeId:Long,diagnostic:String)=dao.recordRecognitionDiagnostic(dayId,placeId,diagnostic)
 suspend fun previewCurrentStop(placeId:Long?,distanceMeters:Double?){dao.settings()?.let{if(it.currentPlaceId!=placeId||it.currentPlaceDistanceMeters!=distanceMeters)dao.saveSettings(it.copy(currentPlaceId=placeId,currentPlaceDistanceMeters=distanceMeters))}}
 suspend fun detectPlannedStop(dayId:Long,latitude:Double,longitude:Double,accuracy:Float):StopDetection?{
  var nearest:StopDetection?=null
  dao.plansNow(dayId).forEach{plan->
   if(plan.visited)return@forEach
   val place=dao.place(plan.placeId)?.takeIf{it.active}?:return@forEach
   val lat=place.latitude?:return@forEach;val lon=place.longitude?:return@forEach
   val earth=6371000.0;val dLat=Math.toRadians(lat-latitude);val dLon=Math.toRadians(lon-longitude)
   val a=kotlin.math.sin(dLat/2).let{it*it}+kotlin.math.cos(Math.toRadians(latitude))*kotlin.math.cos(Math.toRadians(lat))*kotlin.math.sin(dLon/2).let{it*it}
   val distance=2*earth*kotlin.math.atan2(kotlin.math.sqrt(a),kotlin.math.sqrt(1-a))
   val boundedAccuracy=accuracy.coerceIn(0f,30f).toDouble()
   val threshold=if(place.gpsManuallyConfirmed)place.recognitionRadiusMeters+boundedAccuracy*.5 else maxOf(place.recognitionRadiusMeters,40)+boundedAccuracy*.25
   dao.recordClosestApproach(dayId,place.id,distance,boundedAccuracy,threshold)
   if(distance<=threshold&&(nearest==null||distance<nearest!!.distanceMeters))nearest=StopDetection(place,distance,threshold)
  }
  return nearest
 }
 suspend fun isClearlyOutsideStop(placeId:Long,latitude:Double,longitude:Double,accuracy:Float):Boolean{
  if(accuracy>30f)return false
  val place=dao.place(placeId)?:return true;val lat=place.latitude?:return true;val lon=place.longitude?:return true
  val result=FloatArray(1);android.location.Location.distanceBetween(latitude,longitude,lat,lon,result)
  return result[0]>(place.recognitionRadiusMeters+20+accuracy.coerceAtMost(30f)*.5f)
 }
 suspend fun applyStopDetection(dayId:Long,detection:StopDetection?,time:Long):LocationPlace?{
  val current=detection?.place
  if(current!=null)dao.setPlanVisited(dayId,current.id,true,"AUTO",time)
  val activeVisit=dao.activeVisit(dayId)
  if(activeVisit?.placeId!=current?.id){
   activeVisit?.let{visit->val arrival=visit.arrivalTime?:time;val dwell=(time-arrival).coerceAtLeast(0);dao.updateVisit(visit.copy(departureTime=time,dwellDurationMillis=dwell));dao.place(visit.placeId)?.let{place->val samples=place.dwellSampleCount+1;val average=((place.averageDwellMillis.toDouble()*place.dwellSampleCount+dwell)/samples).toLong();dao.updatePlace(place.copy(averageDwellMillis=average,dwellSampleCount=samples))}}
   detection?.let{dao.insertVisit(PlaceVisit(workDayId=dayId,placeId=it.place.id,arrivalTime=time,distanceMeters=it.distanceMeters))}
  }
  dao.settings()?.let{val distance=detection?.distanceMeters;if(it.currentPlaceId!=current?.id||it.currentPlaceDistanceMeters!=distance)dao.saveSettings(it.copy(currentPlaceId=current?.id,currentPlaceDistanceMeters=distance))}
  return current
 }
 suspend fun bakeryPresence(latitude:Double,longitude:Double,accuracy:Float):Boolean?{if(accuracy>30f||dao.settings()?.automaticBakeryTrips!=true)return null;val place=dao.placeByName("Vekni pékség")?:return null;val lat=place.latitude?:return null;val lon=place.longitude?:return null;val earth=6371000.0;val dLat=Math.toRadians(lat-latitude);val dLon=Math.toRadians(lon-longitude);val a=kotlin.math.sin(dLat/2).let{it*it}+kotlin.math.cos(Math.toRadians(latitude))*kotlin.math.cos(Math.toRadians(lat))*kotlin.math.sin(dLon/2).let{it*it};val distance=2*earth*kotlin.math.atan2(kotlin.math.sqrt(a),kotlin.math.sqrt(1-a));return distance<=place.recognitionRadiusMeters+accuracy.coerceAtMost(30f)}
 suspend fun automaticBakeryTransition(dayId:Long,left:Boolean,time:Long){val last=dao.events(dayId).lastOrNull()?.type?:return;if(left&&(last==EventType.WORK_START||last==EventType.TRIP_END))addEvent(dayId,EventType.TRIP_START,time)else if(!left&&last==EventType.TRIP_START)addEvent(dayId,EventType.TRIP_END,time)}
 fun routeConfig(dayId:Long)=dao.observeRouteConfig(dayId)
 suspend fun saveRouteConfig(v:RoutePlanConfig)=dao.saveRouteConfig(v)
 suspend fun setImportant(p:DailyPlacePlan)=dao.upsertPlan(p.copy(priority=if(p.priority==Priority.NORMAL)Priority.IMPORTANT else Priority.NORMAL))
 suspend fun closePeriod(date:String){val p=dao.activePeriod()?:return;val closing=LocalDate.parse(date);if(closing.isBefore(LocalDate.parse(p.startDate)))return;dao.updatePeriod(p.copy(endDate=date,closedAt=System.currentTimeMillis()));val nextId=dao.insertPeriod(WorkPeriod(startDate=closing.plusDays(1).toString()));dao.movePeriodDaysAfter(p.id,nextId,date)}
 suspend fun reopenLatestPeriod(){val active=dao.activePeriod()?:return;val closed=dao.observePeriods().first().filter{it.endDate!=null}.maxByOrNull{it.closedAt?:0}?:return;if(active.id!=closed.id){dao.movePeriodDays(active.id,closed.id);dao.deletePeriod(active)};dao.updatePeriod(closed.copy(endDate=null,closedAt=null))}
 suspend fun addGpsPoint(v:GpsPoint){val previous=dao.lastGpsPoint(v.tripId);dao.insertGpsPoint(v);if(previous!=null){val result=FloatArray(1);android.location.Location.distanceBetween(previous.latitude,previous.longitude,v.latitude,v.longitude,result);val addition=result[0].toDouble();if(addition in 0.0..1000.0)dao.trip(v.tripId)?.let{dao.updateTrip(it.copy(distanceMeters=it.distanceMeters+addition))}}}
 suspend fun activeTrip(dayId:Long)=dao.activeTrip(dayId)
 suspend fun seedDemo(){
  ensureDefaults()
  val seedSettings=dao.settings()?:return;if(seedSettings.stopSeedVersion>=1)return
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
  dao.saveSettings((dao.settings()?:seedSettings).copy(stopSeedVersion=1))
 }
}

data class StopDetection(val place:LocationPlace,val distanceMeters:Double,val thresholdMeters:Double)
data class DaySummary(val workMillis:Long,val travelMillis:Long,val localMillis:Long,val distanceMeters:Double,val earnings:Long)
fun DayWithEvents.summary(settings:AppSettings,now:Long=System.currentTimeMillis()):DaySummary{val sorted=events.sortedBy{it.timestamp};val start=sorted.firstOrNull{it.type==EventType.WORK_START}?.timestamp;val end=sorted.lastOrNull{it.type==EventType.WORK_END}?.timestamp?:if(start!=null)now else null;val work=if(start!=null&&end!=null)(end-start).coerceAtLeast(0) else 0;var travel=0L;var tripStart:Long?=null;sorted.forEach{when(it.type){EventType.TRIP_START->tripStart=it.timestamp;EventType.TRIP_END->{tripStart?.let{s->travel+=(it.timestamp-s).coerceAtLeast(0)};tripStart=null}else->Unit}};if(tripStart!=null)travel+=(now-tripStart!!).coerceAtLeast(0);val paid=if(settings.includeTravelInEarnings)work else (work-travel).coerceAtLeast(0);return DaySummary(work,travel,(work-travel).coerceAtLeast(0),trips.sumOf{it.distanceMeters},paid*settings.hourlyRate/3600000)}
