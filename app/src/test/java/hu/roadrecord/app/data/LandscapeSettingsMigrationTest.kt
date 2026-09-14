package hu.roadrecord.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class LandscapeSettingsMigrationTest {
    @Test fun addsLandscapePreferenceWithoutChangingExistingSettings() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") statements += args?.firstOrNull() as String
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase

        RoadRecordDatabase.MIGRATION_28_29.migrate(database)

        assertTrue(statements.any { it.contains("landscapeEnabled INTEGER NOT NULL DEFAULT 0") })
    }
}
