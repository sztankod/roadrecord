package hu.roadrecord.app.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface RoadRecordDao {
 @Transaction @Query("SELECT * FROM work_days ORDER BY date DESC") fun observeDays():Flow<List<DayWithEvents>>
 @Transaction @Query("SELECT * FROM work_days WHERE id=:id") fun observeDay(id:Long):Flow<DayWithEvents?>
 @Transaction @Query("SELECT * FROM work_days WHERE date=:date LIMIT 1") suspend fun dayByDate(date:String):DayWithEvents?
 @Transaction @Query("SELECT * FROM work_days WHERE EXISTS (SELECT 1 FROM work_events e WHERE e.workDayId=work_days.id AND e.type='WORK_START') AND NOT EXISTS (SELECT 1 FROM work_events e WHERE e.workDayId=work_days.id AND e.type='WORK_END') ORDER BY createdAt DESC LIMIT 1") suspend fun openDay():DayWithEvents?
 @Transaction @Query("SELECT * FROM work_days WHERE EXISTS (SELECT 1 FROM work_events e WHERE e.workDayId=work_days.id AND e.type='WORK_END') ORDER BY createdAt DESC LIMIT 1") suspend fun latestClosedDay():DayWithEvents?
 @Query("SELECT * FROM work_events WHERE workDayId=:dayId ORDER BY timestamp") suspend fun events(dayId:Long):List<WorkEvent>
 @Insert suspend fun insertPeriod(v:WorkPeriod):Long
 @Query("SELECT * FROM work_periods ORDER BY startDate DESC") fun observePeriods():Flow<List<WorkPeriod>>
 @Query("SELECT * FROM work_periods WHERE endDate IS NULL LIMIT 1") suspend fun activePeriod():WorkPeriod?
 @Update suspend fun updatePeriod(v:WorkPeriod)
 @Delete suspend fun deletePeriod(v:WorkPeriod)
 @Query("SELECT COUNT(*) FROM work_days WHERE periodId=:periodId") suspend fun periodDayCount(periodId:Long):Int
 @Query("UPDATE work_days SET periodId=:targetPeriodId WHERE periodId=:sourcePeriodId") suspend fun movePeriodDays(sourcePeriodId:Long,targetPeriodId:Long)
 @Insert suspend fun insertDay(v:WorkDay):Long
 @Query("DELETE FROM work_days WHERE id=:dayId") suspend fun deleteDay(dayId:Long)
 @Insert suspend fun insertEvent(v:WorkEvent):Long
 @Update suspend fun updateEvent(v:WorkEvent)
 @Delete suspend fun deleteEvent(v:WorkEvent)
 @Insert suspend fun insertTrip(v:Trip):Long
 @Update suspend fun updateTrip(v:Trip)
 @Query("SELECT * FROM trips WHERE workDayId=:dayId AND endEventId IS NULL LIMIT 1") suspend fun activeTrip(dayId:Long):Trip?
 @Insert suspend fun insertGpsPoint(v:GpsPoint)
 @Query("SELECT * FROM gps_points WHERE tripId=:tripId ORDER BY timestamp DESC LIMIT 1") suspend fun lastGpsPoint(tripId:Long):GpsPoint?
 @Query("SELECT * FROM trips WHERE id=:tripId") suspend fun trip(tripId:Long):Trip?
 @Query("SELECT * FROM trips") suspend fun allTrips():List<Trip>
 @Query("SELECT * FROM gps_points WHERE tripId=:tripId ORDER BY timestamp") suspend fun gpsPointsNow(tripId:Long):List<GpsPoint>
 @Query("SELECT * FROM gps_points WHERE tripId=:tripId ORDER BY timestamp") fun observePoints(tripId:Long):Flow<List<GpsPoint>>
 @Query("SELECT * FROM places ORDER BY type,name") fun observePlaces():Flow<List<LocationPlace>>
 @Query("SELECT * FROM places WHERE name=:name LIMIT 1") suspend fun placeByName(name:String):LocationPlace?
 @Query("SELECT * FROM places WHERE id=:id") suspend fun place(id:Long):LocationPlace?
 @Insert suspend fun insertPlace(v:LocationPlace):Long
 @Update suspend fun updatePlace(v:LocationPlace)
 @Delete suspend fun deletePlace(v:LocationPlace)
 @Query("SELECT * FROM daily_place_plans WHERE workDayId=:dayId") fun observePlans(dayId:Long):Flow<List<DailyPlacePlan>>
 @Query("SELECT * FROM daily_place_plans WHERE workDayId=:dayId") suspend fun plansNow(dayId:Long):List<DailyPlacePlan>
 @Query("SELECT p.* FROM daily_place_plans p INNER JOIN work_days d ON d.id=p.workDayId WHERE p.placeId=:placeId AND p.workDayId!=:dayId ORDER BY d.createdAt DESC LIMIT 1") suspend fun previousPlan(placeId:Long,dayId:Long):DailyPlacePlan?
 @Query("SELECT p.* FROM daily_place_plans p WHERE p.workDayId=(SELECT d.id FROM work_days d WHERE d.id!=:dayId AND EXISTS (SELECT 1 FROM work_events e WHERE e.workDayId=d.id AND e.type='WORK_END') ORDER BY d.createdAt DESC LIMIT 1)") suspend fun previousDayPlans(dayId:Long):List<DailyPlacePlan>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertPlan(v:DailyPlacePlan)
 @Query("UPDATE daily_place_plans SET visited=:visited WHERE workDayId=:dayId AND placeId=:placeId") suspend fun setPlanVisited(dayId:Long,placeId:Long,visited:Boolean)
 @Query("UPDATE daily_place_plans SET closestApproachMeters=:distance WHERE workDayId=:dayId AND placeId=:placeId AND (closestApproachMeters IS NULL OR :distance<closestApproachMeters)") suspend fun recordClosestApproach(dayId:Long,placeId:Long,distance:Double)
 @Query("DELETE FROM daily_place_plans WHERE workDayId=:dayId AND placeId=:placeId") suspend fun deletePlan(dayId:Long,placeId:Long)
 @Insert suspend fun insertVisit(v:PlaceVisit):Long
 @Update suspend fun updateVisit(v:PlaceVisit)
 @Query("SELECT * FROM place_visits WHERE workDayId=:dayId AND departureTime IS NULL ORDER BY arrivalTime DESC LIMIT 1") suspend fun activeVisit(dayId:Long):PlaceVisit?
 @Query("SELECT COUNT(*) FROM place_visits WHERE workDayId=:dayId AND placeId=:placeId") suspend fun visitCount(dayId:Long,placeId:Long):Int
 @Query("SELECT * FROM place_visits WHERE placeId=:placeId") fun observeVisits(placeId:Long):Flow<List<PlaceVisit>>
 @Query("SELECT * FROM app_settings WHERE id=1") fun observeSettings():Flow<AppSettings?>
 @Query("SELECT * FROM app_settings WHERE id=1") suspend fun settings():AppSettings?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveSettings(v:AppSettings)
 @Query("SELECT COUNT(*) FROM work_days") suspend fun dayCount():Int
 @Query("SELECT COUNT(*) FROM gps_points WHERE latitude >= 47.70") suspend fun northernDemoPointCount():Int
 @Query("DELETE FROM work_days") suspend fun clearAllWorkDays()
 @Query("DELETE FROM work_days WHERE date IN (:dates)") suspend fun deleteWorkDaysByDates(dates:List<String>)
 @Query("SELECT * FROM route_plan_configs WHERE workDayId=:dayId") fun observeRouteConfig(dayId:Long):Flow<RoutePlanConfig?>
 @Query("SELECT * FROM route_plan_configs WHERE workDayId=:dayId") suspend fun routeConfigNow(dayId:Long):RoutePlanConfig?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveRouteConfig(v:RoutePlanConfig)
}
