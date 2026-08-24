package hu.roadrecord.app.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface RoadRecordDao {
 @Transaction @Query("SELECT * FROM work_days ORDER BY date DESC") fun observeDays():Flow<List<DayWithEvents>>
 @Transaction @Query("SELECT * FROM work_days WHERE id=:id") fun observeDay(id:Long):Flow<DayWithEvents?>
 @Transaction @Query("SELECT * FROM work_days WHERE date=:date LIMIT 1") suspend fun dayByDate(date:String):DayWithEvents?
 @Query("SELECT * FROM work_events WHERE workDayId=:dayId ORDER BY timestamp") suspend fun events(dayId:Long):List<WorkEvent>
 @Insert suspend fun insertPeriod(v:WorkPeriod):Long
 @Query("SELECT * FROM work_periods ORDER BY startDate DESC") fun observePeriods():Flow<List<WorkPeriod>>
 @Query("SELECT * FROM work_periods WHERE endDate IS NULL LIMIT 1") suspend fun activePeriod():WorkPeriod?
 @Update suspend fun updatePeriod(v:WorkPeriod)
 @Insert suspend fun insertDay(v:WorkDay):Long
 @Insert suspend fun insertEvent(v:WorkEvent):Long
 @Update suspend fun updateEvent(v:WorkEvent)
 @Delete suspend fun deleteEvent(v:WorkEvent)
 @Insert suspend fun insertTrip(v:Trip):Long
 @Update suspend fun updateTrip(v:Trip)
 @Query("SELECT * FROM trips WHERE workDayId=:dayId AND endEventId IS NULL LIMIT 1") suspend fun activeTrip(dayId:Long):Trip?
 @Insert suspend fun insertGpsPoint(v:GpsPoint)
 @Query("SELECT * FROM gps_points WHERE tripId=:tripId ORDER BY timestamp") fun observePoints(tripId:Long):Flow<List<GpsPoint>>
 @Query("SELECT * FROM places ORDER BY type,name") fun observePlaces():Flow<List<LocationPlace>>
 @Query("SELECT * FROM places WHERE name=:name LIMIT 1") suspend fun placeByName(name:String):LocationPlace?
 @Query("SELECT * FROM places WHERE id=:id") suspend fun place(id:Long):LocationPlace?
 @Insert suspend fun insertPlace(v:LocationPlace):Long
 @Update suspend fun updatePlace(v:LocationPlace)
 @Delete suspend fun deletePlace(v:LocationPlace)
 @Query("SELECT * FROM daily_place_plans WHERE workDayId=:dayId") fun observePlans(dayId:Long):Flow<List<DailyPlacePlan>>
 @Query("SELECT * FROM daily_place_plans WHERE workDayId=:dayId") suspend fun plansNow(dayId:Long):List<DailyPlacePlan>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertPlan(v:DailyPlacePlan)
 @Query("UPDATE daily_place_plans SET visited=:visited WHERE workDayId=:dayId AND placeId=:placeId") suspend fun setPlanVisited(dayId:Long,placeId:Long,visited:Boolean)
 @Query("DELETE FROM daily_place_plans WHERE workDayId=:dayId AND placeId=:placeId") suspend fun deletePlan(dayId:Long,placeId:Long)
 @Insert suspend fun insertVisit(v:PlaceVisit):Long
 @Query("SELECT * FROM place_visits WHERE placeId=:placeId") fun observeVisits(placeId:Long):Flow<List<PlaceVisit>>
 @Query("SELECT * FROM app_settings WHERE id=1") fun observeSettings():Flow<AppSettings?>
 @Query("SELECT * FROM app_settings WHERE id=1") suspend fun settings():AppSettings?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveSettings(v:AppSettings)
 @Query("SELECT COUNT(*) FROM work_days") suspend fun dayCount():Int
 @Query("SELECT COUNT(*) FROM gps_points WHERE latitude >= 47.70") suspend fun northernDemoPointCount():Int
 @Query("SELECT * FROM route_plan_configs WHERE workDayId=:dayId") fun observeRouteConfig(dayId:Long):Flow<RoutePlanConfig?>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveRouteConfig(v:RoutePlanConfig)
}
