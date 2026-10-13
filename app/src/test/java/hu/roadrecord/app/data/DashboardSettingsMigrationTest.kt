package hu.roadrecord.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DashboardSettingsMigrationTest {
    @Test fun addsPersistentDashboardRouteAndWeatherFields(){
        val sql=mutableListOf<String>()
        val db=Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,arrayOf(SupportSQLiteDatabase::class.java)){_,method,args->
            if(method.name=="execSQL")sql+=args?.firstOrNull() as String
            when(method.returnType){Boolean::class.javaPrimitiveType->false;Int::class.javaPrimitiveType->0;Long::class.javaPrimitiveType->0L;else->null}
        } as SupportSQLiteDatabase
        RoadRecordDatabase.MIGRATION_31_32.migrate(db)
        assertTrue(sql.any{it.contains("todayScrollLocked")&&it.contains("DEFAULT 0")})
        assertTrue(sql.any{it.contains("routeRefreshSeconds")&&it.contains("DEFAULT 60")})
        assertTrue(sql.any{it.contains("weatherTempC")})
        assertTrue(sql.any{it.contains("weatherFetchedAt")})
    }

    @Test fun addsSeparatePhotoBackupTracking(){
        val sql=mutableListOf<String>()
        val db=Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,arrayOf(SupportSQLiteDatabase::class.java)){_,method,args->
            if(method.name=="execSQL")sql+=args?.firstOrNull() as String
            when(method.returnType){Boolean::class.javaPrimitiveType->false;Int::class.javaPrimitiveType->0;Long::class.javaPrimitiveType->0L;else->null}
        } as SupportSQLiteDatabase
        RoadRecordDatabase.MIGRATION_32_33.migrate(db)
        assertTrue(sql.any{it.contains("lastPhotoBackupSignature")&&it.contains("DEFAULT ''")})
        assertTrue(sql.any{it.contains("lastPhotoBackupAt")})
    }
}
