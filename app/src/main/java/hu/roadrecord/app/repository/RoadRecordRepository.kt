package hu.roadrecord.app.repository

import hu.roadrecord.app.data.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

class RoadRecordRepository(private val dao:RoadRecordDao){
 val days=dao.observeDays(); val periods=dao.observePeriods(); val places=dao.observePlaces(); val settings=dao.observeSettings().map{it?:AppSettings()}
 suspend fun ensureDefaults(){if(dao.settings()==null)dao.saveSettings(AppSettings()); if(dao.activePeriod()==null)dao.insertPeriod(WorkPeriod(startDate=LocalDate.now().toString()))}
 suspend fun activeDay()=dao.dayByDate(LocalDate.now().toString())
 suspend fun startWork(now:Long=System.currentTimeMillis()):Long { ensureDefaults(); val existing=activeDay(); if(existing!=null)return existing.day.id; val p=dao.activePeriod()?:error("Nincs aktív időszak"); val id=dao.insertDay(WorkDay(periodId=p.id,date=LocalDate.now().toString())); dao.insertEvent(WorkEvent(workDayId=id,type=EventType.WORK_START,timestamp=now)); return id }
 suspend fun nextAction(dayId:Long,now:Long=System.currentTimeMillis()):EventType { val e=dao.events(dayId); val next=when(e.lastOrNull()?.type){null->EventType.WORK_START;EventType.WORK_START,EventType.TRIP_END->EventType.TRIP_START;EventType.TRIP_START->EventType.TRIP_END;EventType.WORK_END->throw IllegalStateException("A munkanap már lezárult")}; addEvent(dayId,next,now); return next }
 suspend fun endWork(dayId:Long,now:Long=System.currentTimeMillis()){addEvent(dayId,EventType.WORK_END,now)}
 suspend fun addEvent(dayId:Long,type:EventType,time:Long):Long { val candidate=dao.events(dayId)+WorkEvent(workDayId=dayId,type=type,timestamp=time); validate(candidate); val eventId=dao.insertEvent(WorkEvent(workDayId=dayId,type=type,timestamp=time)); when(type){EventType.TRIP_START->dao.insertTrip(Trip(workDayId=dayId,startEventId=eventId));EventType.TRIP_END->{dao.activeTrip(dayId)?.let{dao.updateTrip(it.copy(endEventId=eventId))}}else->Unit}; return eventId }
 suspend fun updateEvent(event:WorkEvent){val list=dao.events(event.workDayId).map{if(it.id==event.id)event else it};validate(list);dao.updateEvent(event)}
 suspend fun deleteEvent(event:WorkEvent){dao.deleteEvent(event)}
 private fun validate(events:List<WorkEvent>){val sorted=events.sortedBy{it.timestamp}; if(sorted!=events.sortedBy{it.timestamp})Unit; var working=false;var travelling=false; sorted.forEach{when(it.type){EventType.WORK_START->{require(!working){"Már van munkakezdés"};working=true};EventType.TRIP_START->{require(working&&!travelling){"Indulás csak aktív munkában lehetséges"};travelling=true};EventType.TRIP_END->{require(travelling){"A visszaérkezés nem előzheti meg az indulást"};travelling=false};EventType.WORK_END->{require(working&&!travelling){"Munka vége csak visszaérkezés után rögzíthető"};working=false}}}}
 fun observeDay(id:Long)=dao.observeDay(id); fun plans(id:Long)=dao.observePlans(id); fun points(id:Long)=dao.observePoints(id)
 suspend fun saveSettings(v:AppSettings)=dao.saveSettings(v)
 suspend fun savePlace(v:LocationPlace)=if(v.id==0L)dao.insertPlace(v) else {dao.updatePlace(v);v.id}
 suspend fun deletePlace(v:LocationPlace)=dao.deletePlace(v)
 suspend fun togglePlan(dayId:Long,placeId:Long,selected:Boolean){if(selected)dao.upsertPlan(DailyPlacePlan(dayId,placeId))else dao.deletePlan(dayId,placeId)}
 suspend fun setImportant(p:DailyPlacePlan)=dao.upsertPlan(p.copy(priority=if(p.priority==Priority.NORMAL)Priority.IMPORTANT else Priority.NORMAL))
 suspend fun closePeriod(date:String){val p=dao.activePeriod()?:return;dao.updatePeriod(p.copy(endDate=date,closedAt=System.currentTimeMillis()));dao.insertPeriod(WorkPeriod(startDate=LocalDate.parse(date).plusDays(1).toString()))}
 suspend fun addGpsPoint(v:GpsPoint)=dao.insertGpsPoint(v)
 suspend fun activeTrip(dayId:Long)=dao.activeTrip(dayId)
 suspend fun seedDemo(){
  ensureDefaults();val period=dao.activePeriod()!!
  val places=listOf(
   LocationPlace(type=PlaceType.HOME,name="Budapesti telephely",officialAddress="1138 Budapest, Váci út 168.",latitude=47.5518,longitude=19.0732,note="Napi indulási és érkezési pont"),
   LocationPlace(name="Váci partner",officialAddress="2600 Vác, Március 15. tér 11.",latitude=47.7759,longitude=19.1360,note="Belvárosi lerakóhely"),
   LocationPlace(name="Szokolyai ügyfél",officialAddress="2624 Szokolya, Fő utca 13.",latitude=47.8685,longitude=19.0090,note="Kapubejáró a Fő utca felől"),
   LocationPlace(name="Verőcei megálló",officialAddress="2621 Verőce, Árpád út 40.",latitude=47.8245,longitude=19.0343,note="Dunapart felőli bejárat"),
   LocationPlace(name="Kismarosi partner",officialAddress="2623 Kismaros, Kossuth Lajos út 22.",latitude=47.8376,longitude=18.9858,note="Recepción jelentkezni"),
   LocationPlace(name="Nagymarosi ügyfél",officialAddress="2626 Nagymaros, Fő tér 5.",latitude=47.7887,longitude=18.9598,note="Rakodóhely az udvarban")
  )
  val placeIds=places.map{dao.placeByName(it.name)?.id?:dao.insertPlace(it)}
  if(dao.northernDemoPointCount()>0)return
  val routes=listOf(
   doubleArrayOf(47.5518,19.0732,47.7759,19.1360),doubleArrayOf(47.7759,19.1360,47.8685,19.0090),
   doubleArrayOf(47.5518,19.0732,47.8245,19.0343),doubleArrayOf(47.8245,19.0343,47.8376,18.9858),
   doubleArrayOf(47.5518,19.0732,47.8685,19.0090),doubleArrayOf(47.8685,19.0090,47.7887,18.9598),
   doubleArrayOf(47.7759,19.1360,47.8376,18.9858),doubleArrayOf(47.7887,18.9598,47.5518,19.0732),
   doubleArrayOf(47.8376,18.9858,47.5518,19.0732),doubleArrayOf(47.8245,19.0343,47.7759,19.1360)
  )
  routes.forEachIndexed{i,r->
   val date=LocalDate.now().minusDays((10-i).toLong());val existing=dao.dayByDate(date.toString());val base=date.atTime(7+i%2,30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
   val day=existing?.day?.id?:dao.insertDay(WorkDay(periodId=period.id,date=date.toString()))
   val oldEvents=existing?.events?.sortedBy{it.timestamp}.orEmpty();val ts=oldEvents.firstOrNull{it.type==EventType.TRIP_START}?.id?:run{dao.insertEvent(WorkEvent(workDayId=day,type=EventType.WORK_START,timestamp=base));dao.insertEvent(WorkEvent(workDayId=day,type=EventType.TRIP_START,timestamp=base+45*60000))}
   val te=oldEvents.firstOrNull{it.type==EventType.TRIP_END}?.id?:dao.insertEvent(WorkEvent(workDayId=day,type=EventType.TRIP_END,timestamp=base+135*60000))
   if(oldEvents.none{it.type==EventType.WORK_END})dao.insertEvent(WorkEvent(workDayId=day,type=EventType.WORK_END,timestamp=base+8*60*60000))
   val trip=dao.insertTrip(Trip(workDayId=day,startEventId=ts,endEventId=te,distanceMeters=28000.0+i*4200))
   repeat(18){p->val f=p/17.0;val bend=kotlin.math.sin(Math.PI*f)*.012*((i%3)-1);dao.insertGpsPoint(GpsPoint(tripId=trip,timestamp=base+(45+p*5)*60000,latitude=r[0]+(r[2]-r[0])*f+bend,longitude=r[1]+(r[3]-r[1])*f+bend*.35,accuracy=8f))}
   dao.upsertPlan(DailyPlacePlan(day,placeIds[1+i%(placeIds.size-1)],visited=true))
  }
 }
}

data class DaySummary(val workMillis:Long,val travelMillis:Long,val localMillis:Long,val distanceMeters:Double,val earnings:Long)
fun DayWithEvents.summary(settings:AppSettings,now:Long=System.currentTimeMillis()):DaySummary{val sorted=events.sortedBy{it.timestamp};val start=sorted.firstOrNull{it.type==EventType.WORK_START}?.timestamp;val end=sorted.lastOrNull{it.type==EventType.WORK_END}?.timestamp?:if(start!=null)now else null;val work=if(start!=null&&end!=null)(end-start).coerceAtLeast(0) else 0;var travel=0L;var tripStart:Long?=null;sorted.forEach{when(it.type){EventType.TRIP_START->tripStart=it.timestamp;EventType.TRIP_END->{tripStart?.let{s->travel+=(it.timestamp-s).coerceAtLeast(0)};tripStart=null}else->Unit}};if(tripStart!=null)travel+=(now-tripStart!!).coerceAtLeast(0);val paid=if(settings.includeTravelInEarnings)work else (work-travel).coerceAtLeast(0);return DaySummary(work,travel,(work-travel).coerceAtLeast(0),trips.sumOf{it.distanceMeters},paid*settings.hourlyRate/3600000)}
