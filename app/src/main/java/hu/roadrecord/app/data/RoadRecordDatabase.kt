package hu.roadrecord.app.data
import android.content.Context
import androidx.room.*

@Database(entities=[WorkPeriod::class,WorkDay::class,WorkEvent::class,Trip::class,GpsPoint::class,LocationPlace::class,DailyPlacePlan::class,PlaceVisit::class,AppSettings::class],version=1,exportSchema=true)
@TypeConverters(Converters::class)
abstract class RoadRecordDatabase:RoomDatabase(){
 abstract fun dao():RoadRecordDao
 companion object { @Volatile private var instance:RoadRecordDatabase?=null
  fun get(context:Context)=instance?:synchronized(this){instance?:Room.databaseBuilder(context.applicationContext,RoadRecordDatabase::class.java,"roadrecord.db").build().also{instance=it}}
 }
}
