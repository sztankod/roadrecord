package hu.roadrecord.app.data

import androidx.room.*

enum class EventType { WORK_START, TRIP_START, TRIP_END, WORK_END }
enum class PlaceType { HOME, CLIENT }
enum class Priority { NORMAL, IMPORTANT }

@Entity(tableName = "work_periods")
data class WorkPeriod(@PrimaryKey(autoGenerate = true) val id: Long = 0, val startDate: String, val endDate: String? = null, val closedAt: Long? = null)

@Entity(tableName = "work_days", foreignKeys = [ForeignKey(entity = WorkPeriod::class, parentColumns = ["id"], childColumns = ["periodId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("periodId"), Index(value=["date"], unique=true)])
data class WorkDay(@PrimaryKey(autoGenerate = true) val id: Long = 0, val periodId: Long, val date: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "work_events", foreignKeys = [ForeignKey(entity = WorkDay::class, parentColumns = ["id"], childColumns = ["workDayId"], onDelete = ForeignKey.CASCADE)], indices = [Index("workDayId")])
data class WorkEvent(@PrimaryKey(autoGenerate = true) val id: Long = 0, val workDayId: Long, val type: EventType, val timestamp: Long)

@Entity(tableName = "trips", foreignKeys = [ForeignKey(entity = WorkDay::class, parentColumns = ["id"], childColumns = ["workDayId"], onDelete = ForeignKey.CASCADE)], indices = [Index("workDayId")])
data class Trip(@PrimaryKey(autoGenerate = true) val id: Long = 0, val workDayId: Long, val startEventId: Long, val endEventId: Long? = null, val distanceMeters: Double = 0.0)

@Entity(tableName = "gps_points", foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)], indices = [Index("tripId")])
data class GpsPoint(@PrimaryKey(autoGenerate = true) val id: Long = 0, val tripId: Long, val timestamp: Long, val latitude: Double, val longitude: Double, val accuracy: Float)

@Entity(tableName = "places")
data class LocationPlace(@PrimaryKey(autoGenerate = true) val id: Long = 0, val type: PlaceType = PlaceType.CLIENT, val name: String, val officialAddress: String = "", val latitude: Double? = null, val longitude: Double? = null, val encryptedGateCode: String? = null, val photoPath: String? = null, val note: String = "", val recognitionRadiusMeters: Int = 150)

@Entity(tableName = "daily_place_plans", primaryKeys = ["workDayId", "placeId"], foreignKeys = [ForeignKey(entity=WorkDay::class,parentColumns=["id"],childColumns=["workDayId"],onDelete=ForeignKey.CASCADE), ForeignKey(entity=LocationPlace::class,parentColumns=["id"],childColumns=["placeId"],onDelete=ForeignKey.CASCADE)], indices=[Index("placeId")])
data class DailyPlacePlan(val workDayId: Long, val placeId: Long, val priority: Priority = Priority.NORMAL, val visited: Boolean = false, val sortHint: Int? = null)

@Entity(tableName = "place_visits", foreignKeys = [ForeignKey(entity=WorkDay::class,parentColumns=["id"],childColumns=["workDayId"],onDelete=ForeignKey.CASCADE), ForeignKey(entity=LocationPlace::class,parentColumns=["id"],childColumns=["placeId"],onDelete=ForeignKey.CASCADE)], indices=[Index("workDayId"),Index("placeId")])
data class PlaceVisit(@PrimaryKey(autoGenerate=true) val id:Long=0,val workDayId:Long,val placeId:Long,val previousPlaceId:Long?=null,val nextPlaceId:Long?=null,val arrivalTime:Long?=null,val departureTime:Long?=null,val travelDurationMillis:Long?=null,val distanceMeters:Double?=null)

@Entity(tableName = "app_settings")
data class AppSettings(@PrimaryKey val id:Int=1,val hourlyRate:Long=4500,val includeTravelInEarnings:Boolean=true,val automaticPlaceRecognition:Boolean=false,val reportEmail:String="",val reportFrequency:String="PERIOD_CLOSE",val automaticReports:Boolean=false)

data class DayWithEvents(@Embedded val day: WorkDay, @Relation(parentColumn="id",entityColumn="workDayId") val events:List<WorkEvent>, @Relation(parentColumn="id",entityColumn="workDayId") val trips:List<Trip>)
