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
 suspend fun seedDemo(){ensureDefaults();val period=dao.activePeriod()!!;val placeIds=listOf(
  LocationPlace(type=PlaceType.HOME,name="RoadRecord telephely",officialAddress="1117 Budapest, Budafoki út 56.",latitude=47.4686,longitude=19.0522,note="Napi indulási és érkezési pont"),
  LocationPlace(name="GreenPartner Kft.",officialAddress="1033 Budapest, Fő tér 6.",latitude=47.5414,longitude=19.0450,encryptedGateCode="2580",note="Rakodás a bal oldali kapunál"),
  LocationPlace(name="Metrodrom Kft.",officialAddress="1138 Budapest, Váci út 168.",latitude=47.5518,longitude=19.0732,note="Recepción jelentkezni"),
  LocationPlace(name="Aranycipő Pékség",officialAddress="1094 Budapest, Ferenc tér 11.",latitude=47.4827,longitude=19.0701,note="Hátsó gazdasági bejárat"),
  LocationPlace(name="Inventor Kft.",officialAddress="1106 Budapest, Maglódi út 14.",latitude=47.4899,longitude=19.1441,note="Ügyfélparkoló használható"),
  LocationPlace(name="Logistic Pro Kft.",officialAddress="1211 Budapest, Szállító utca 4.",latitude=47.4358,longitude=19.0718,note="2-es porta")
 ).map{dao.placeByName(it.name)?.id?:dao.insertPlace(it)};if(dao.dayCount()>0)return;repeat(16){i->val daysAgo=(16-i).toLong();val date=LocalDate.now().minusDays(daysAgo);val day=dao.insertDay(WorkDay(periodId=period.id,date=date.toString()));val base=date.atTime(7+(i%2),35).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();dao.insertEvent(WorkEvent(workDayId=day,type=EventType.WORK_START,timestamp=base));val ts=dao.insertEvent(WorkEvent(workDayId=day,type=EventType.TRIP_START,timestamp=base+55*60000));val te=dao.insertEvent(WorkEvent(workDayId=day,type=EventType.TRIP_END,timestamp=base+(145+i%4*12)*60000));dao.insertEvent(WorkEvent(workDayId=day,type=EventType.WORK_END,timestamp=base+(8*60+20+i%3*15)*60000));val trip=dao.insertTrip(Trip(workDayId=day,startEventId=ts,endEventId=te,distanceMeters=86400.0+i*3800));repeat(12){p->dao.insertGpsPoint(GpsPoint(tripId=trip,timestamp=base+(55+p*7)*60000,latitude=47.4686+p*.004+(i%3)*.001,longitude=19.0522+p*.005,accuracy=9f))};val destination=placeIds[1+i%(placeIds.size-1)];dao.upsertPlan(DailyPlacePlan(day,destination,visited=true));dao.upsertPlan(DailyPlacePlan(day,placeIds.first(),visited=true))}}
}

data class DaySummary(val workMillis:Long,val travelMillis:Long,val localMillis:Long,val distanceMeters:Double,val earnings:Long)
fun DayWithEvents.summary(settings:AppSettings,now:Long=System.currentTimeMillis()):DaySummary{val sorted=events.sortedBy{it.timestamp};val start=sorted.firstOrNull{it.type==EventType.WORK_START}?.timestamp;val end=sorted.lastOrNull{it.type==EventType.WORK_END}?.timestamp?:if(start!=null)now else null;val work=if(start!=null&&end!=null)(end-start).coerceAtLeast(0) else 0;var travel=0L;var tripStart:Long?=null;sorted.forEach{when(it.type){EventType.TRIP_START->tripStart=it.timestamp;EventType.TRIP_END->{tripStart?.let{s->travel+=(it.timestamp-s).coerceAtLeast(0)};tripStart=null}else->Unit}};if(tripStart!=null)travel+=(now-tripStart!!).coerceAtLeast(0);val paid=if(settings.includeTravelInEarnings)work else (work-travel).coerceAtLeast(0);return DaySummary(work,travel,(work-travel).coerceAtLeast(0),trips.sumOf{it.distanceMeters},paid*settings.hourlyRate/3600000)}
