package hu.roadrecord.app.data
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration

@Database(entities=[WorkPeriod::class,WorkDay::class,WorkEvent::class,Trip::class,GpsPoint::class,LocationPlace::class,DailyPlacePlan::class,PlaceVisit::class,AppSettings::class],version=2,exportSchema=true)
@TypeConverters(Converters::class)
abstract class RoadRecordDatabase:RoomDatabase(){
 abstract fun dao():RoadRecordDao
 companion object { @Volatile private var instance:RoadRecordDatabase?=null
  private val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("ALTER TABLE app_settings ADD COLUMN showWorkTime INTEGER NOT NULL DEFAULT 1");db.execSQL("ALTER TABLE app_settings ADD COLUMN showTravelTime INTEGER NOT NULL DEFAULT 1");db.execSQL("ALTER TABLE app_settings ADD COLUMN showEarnings INTEGER NOT NULL DEFAULT 1")}}
  fun get(context:Context)=instance?:synchronized(this){instance?:Room.databaseBuilder(context.applicationContext,RoadRecordDatabase::class.java,"roadrecord.db").addMigrations(MIGRATION_1_2).build().also{instance=it}}
 }
}
