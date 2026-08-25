package hu.roadrecord.app.data
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration

@Database(entities=[WorkPeriod::class,WorkDay::class,WorkEvent::class,Trip::class,GpsPoint::class,LocationPlace::class,DailyPlacePlan::class,PlaceVisit::class,AppSettings::class,RoutePlanConfig::class],version=6,exportSchema=true)
@TypeConverters(Converters::class)
abstract class RoadRecordDatabase:RoomDatabase(){
 abstract fun dao():RoadRecordDao
 companion object { @Volatile private var instance:RoadRecordDatabase?=null
  private val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("ALTER TABLE app_settings ADD COLUMN showWorkTime INTEGER NOT NULL DEFAULT 1");db.execSQL("ALTER TABLE app_settings ADD COLUMN showTravelTime INTEGER NOT NULL DEFAULT 1");db.execSQL("ALTER TABLE app_settings ADD COLUMN showEarnings INTEGER NOT NULL DEFAULT 1")}}
  private val MIGRATION_2_3=object:Migration(2,3){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("ALTER TABLE places ADD COLUMN entrancePhotoPath TEXT")}}
  private val MIGRATION_3_4=object:Migration(3,4){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("ALTER TABLE daily_place_plans ADD COLUMN lockedPosition INTEGER");db.execSQL("CREATE TABLE IF NOT EXISTS route_plan_configs (workDayId INTEGER NOT NULL, startPlaceId INTEGER, endPlaceId INTEGER, PRIMARY KEY(workDayId), FOREIGN KEY(workDayId) REFERENCES work_days(id) ON UPDATE NO ACTION ON DELETE CASCADE)");db.execSQL("CREATE INDEX IF NOT EXISTS index_route_plan_configs_workDayId ON route_plan_configs(workDayId)")}}
  private val MIGRATION_4_5=object:Migration(4,5){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("ALTER TABLE app_settings ADD COLUMN dataResetVersion INTEGER NOT NULL DEFAULT 0")}}
  private val MIGRATION_5_6=object:Migration(5,6){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("DROP INDEX IF EXISTS index_work_days_date");db.execSQL("CREATE INDEX IF NOT EXISTS index_work_days_date ON work_days(date)");db.execSQL("ALTER TABLE app_settings ADD COLUMN overnightRepairVersion INTEGER NOT NULL DEFAULT 0")}}
  fun get(context:Context)=instance?:synchronized(this){instance?:Room.databaseBuilder(context.applicationContext,RoadRecordDatabase::class.java,"roadrecord.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6).build().also{instance=it}}
 }
}
