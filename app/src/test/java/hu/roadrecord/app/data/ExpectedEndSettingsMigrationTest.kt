package hu.roadrecord.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpectedEndSettingsMigrationTest {
    @Test fun migrationAddsRefreshPreferencesWithoutRemovingExistingData() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL") {
                statements += args!![0] as String
                null
            } else error("Unexpected database operation: ${method.name}")
        } as SupportSQLiteDatabase

        RoadRecordDatabase.MIGRATION_27_28.migrate(database)

        assertEquals(2, statements.size)
        assertTrue(statements.any { it.contains("expectedEndRefreshMode TEXT NOT NULL DEFAULT 'STOP_EXIT'") })
        assertTrue(statements.any { it.contains("expectedEndRefreshMinutes INTEGER NOT NULL DEFAULT 5") })
        assertTrue(statements.none { it.contains("DROP") || it.contains("DELETE") })
    }
}
