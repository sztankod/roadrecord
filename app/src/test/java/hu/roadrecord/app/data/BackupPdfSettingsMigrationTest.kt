package hu.roadrecord.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class BackupPdfSettingsMigrationTest {
    @Test fun addsBackupDeduplicationAndStopPdfSettings() {
        val statements=mutableListOf<String>()
        val database=Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,arrayOf(SupportSQLiteDatabase::class.java)){_,method,args->
            if(method.name=="execSQL")statements+=args?.firstOrNull() as String
            when(method.returnType){Boolean::class.javaPrimitiveType->false;Int::class.javaPrimitiveType->0;Long::class.javaPrimitiveType->0L;else->null}
        } as SupportSQLiteDatabase
        RoadRecordDatabase.MIGRATION_30_31.migrate(database)
        assertTrue(statements.any{it.contains("lastLocalBackupWorkDayId")})
        assertTrue(statements.any{it.contains("lastDriveBackupWorkDayId")})
        assertTrue(statements.any{it.contains("stopPdfWifiOnly")&&it.contains("DEFAULT 1")})
        assertTrue(statements.any{it.contains("stopPdfIncludeCodes")&&it.contains("DEFAULT 1")})
        assertTrue(statements.any{it.contains("stopPdfIncludePhotos")&&it.contains("DEFAULT 1")})
        assertTrue(statements.any{it.contains("stopPdfFrequency")&&it.contains("ON_CHANGE")})
        assertTrue(statements.any{it.contains("lastStopPdfAt")})
    }
}
