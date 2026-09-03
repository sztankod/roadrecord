package hu.roadrecord.app.display

import androidx.sqlite.db.SupportSQLiteDatabase
import hu.roadrecord.app.data.RoadRecordDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

/** Checks migration intent without needing a device; an on-device upgrade remains a smoke test. */
class ScreenSettingsMigrationTest {
    @Test fun migrationAddsPreferencesAndPreservesExistingChoiceWithoutDeletingData() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            if (method.name == "execSQL") { statements += args!![0] as String; null }
            else error("Unexpected database operation: ${method.name}")
        } as SupportSQLiteDatabase
        RoadRecordDatabase.MIGRATION_24_25.migrate(database)
        assertEquals(5, statements.size)
        assertEquals(4, statements.count { it.startsWith("ALTER TABLE app_settings ADD COLUMN") })
        assertTrue(statements.contains("UPDATE app_settings SET keepScreenOnEnabled = keepScreenOnDuringTrip"))
        assertTrue(statements.none { it.contains("DROP") || it.contains("DELETE") })
        assertTrue(statements.any { it.contains("screenDimEnabled INTEGER NOT NULL DEFAULT 0") })
    }
}
