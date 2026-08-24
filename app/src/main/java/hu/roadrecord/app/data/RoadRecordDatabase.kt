package hu.roadrecord.app.data
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration

@Database(entities=[WorkPeriod::class,WorkDay::class,WorkEvent::class,Trip::class,GpsPoint::class,LocationPlace::class,DailyPlacePlan::class,PlaceVisit::class,AppSettings::class,RoutePlanConfig::class],version=4,exportSchema=true)
@TypeConverters(Converters::class)
abstract class RoadRecordDatabase:RoomDatabase(){
 abstract fun dao():RoadRecordDao
 companion object { @Volatile private var instance:RoadRecordDatabase?=null
  private val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("ALTER TABLE app_settings ADD COLUMN showWorkTime INTEGER NOT NULL DEFAULT 1");db.execSQL("ALTER TABLE app_settings ADD COLUMN showTravelTime INTEGER NOT NULL DEFAULT 1");db.execSQL("ALTER TABLE app_settings ADD COLUMN showEarnings INTEGER NOT NULL DEFAULT 1")}}
  private val MIGRATION_2_3=object:Migration(2,3){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("ALTER TABLE places ADD COLUMN entrancePhotoPath TEXT")}}
  private val MIGRATION_3_4=object:Migration(3,4){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("ALTER TABLE daily_place_plans ADD COLUMN lockedPosition INTEGER");db.execSQL("CREATE TABLE IF NOT EXISTS route_plan_configs (workDayId INTEGER NOT NULL, startPlaceId INTEGER, endPlaceId INTEGER, PRIMARY KEY(workDayId), FOREIGN KEY(workDayId) REFERENCES work_days(id) ON UPDATE NO ACTION ON DELETE CASCADE)");db.execSQL("CREATE INDEX IF NOT EXISTS index_route_plan_configs_workDayId ON route_plan_configs(workDayId)")}}
  fun get(context:Context)=instance?:synchronized(this){instance?:Room.databaseBuilder(context.applicationContext,RoadRecordDatabase::class.java,"roadrecord.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build().also{instance=it}}
 }
}
