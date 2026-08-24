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
 suspend fun seedDemo(){if(dao.dayCount()>0)return;ensureDefaults(); val period=dao.activePeriod()!!; val home=dao.insertPlace(LocationPlace(type=PlaceType.HOME,name="Telephely",officialAddress="Budapest",latitude=47.4979,longitude=19.0402)); repeat(5){dao.insertPlace(LocationPlace(name="Ügyfél ${('A'.code+it).toChar()}",officialAddress="Budapest ${it+1}.",latitude=47.48+it*.01,longitude=19.03+it*.012))}; repeat(10){i->val date=LocalDate.now().minusDays((10-i).toLong());val day=dao.insertDay(WorkDay(periodId=period.id,date=date.toString()));val base=System.currentTimeMillis()-(10-i)*86400000L;val ws=dao.insertEvent(WorkEvent(workDayId=day,type=EventType.WORK_START,timestamp=base));val ts=dao.insertEvent(WorkEvent(workDayId=day,type=EventType.TRIP_START,timestamp=base+3600000));val te=dao.insertEvent(WorkEvent(workDayId=day,type=EventType.TRIP_END,timestamp=base+7200000));dao.insertEvent(WorkEvent(workDayId=day,type=EventType.WORK_END,timestamp=base+8*3600000));val trip=dao.insertTrip(Trip(workDayId=day,startEventId=ts,endEventId=te,distanceMeters=12000.0+i*500));repeat(8){p->dao.insertGpsPoint(GpsPoint(tripId=trip,timestamp=base+3600000+p*300000,latitude=47.49+p*.001,longitude=19.04+p*.001,accuracy=12f))};dao.upsertPlan(DailyPlacePlan(day,home,visited=true))}}
}

data class DaySummary(val workMillis:Long,val travelMillis:Long,val localMillis:Long,val distanceMeters:Double,val earnings:Long)
fun DayWithEvents.summary(settings:AppSettings,now:Long=System.currentTimeMillis()):DaySummary{val sorted=events.sortedBy{it.timestamp};val start=sorted.firstOrNull{it.type==EventType.WORK_START}?.timestamp;val end=sorted.lastOrNull{it.type==EventType.WORK_END}?.timestamp?:if(start!=null)now else null;val work=if(start!=null&&end!=null)(end-start).coerceAtLeast(0) else 0;var travel=0L;var tripStart:Long?=null;sorted.forEach{when(it.type){EventType.TRIP_START->tripStart=it.timestamp;EventType.TRIP_END->{tripStart?.let{s->travel+=(it.timestamp-s).coerceAtLeast(0)};tripStart=null}else->Unit}};if(tripStart!=null)travel+=(now-tripStart!!).coerceAtLeast(0);val paid=if(settings.includeTravelInEarnings)work else (work-travel).coerceAtLeast(0);return DaySummary(work,travel,(work-travel).coerceAtLeast(0),trips.sumOf{it.distanceMeters},paid*settings.hourlyRate/3600000)}
