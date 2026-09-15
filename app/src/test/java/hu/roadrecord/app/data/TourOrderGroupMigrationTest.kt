package hu.roadrecord.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class TourOrderGroupMigrationTest {
    @Test fun createsGroupsAndMovesLegacyAnchorsWithoutDeletingPlaces() {
        val statements=mutableListOf<String>()
        val database=Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,arrayOf(SupportSQLiteDatabase::class.java)){_,method,args->
            if(method.name=="execSQL") statements+=args?.firstOrNull() as String
            when(method.returnType){Boolean::class.javaPrimitiveType->false;Int::class.javaPrimitiveType->0;Long::class.javaPrimitiveType->0L;else->null}
        } as SupportSQLiteDatabase
        RoadRecordDatabase.MIGRATION_29_30.migrate(database)
        assertTrue(statements.any{it.contains("CREATE TABLE IF NOT EXISTS tour_order_groups")})
        assertTrue(statements.any{it.contains("ADD COLUMN tourOrderGroupId")})
        assertTrue(statements.any{it.contains("tourOrderGroupId=1")&&it.contains("START")})
        assertTrue(statements.any{it.contains("tourOrderGroupId=2")&&it.contains("END")})
        assertTrue(statements.none{it.contains("DELETE FROM places")})
    }
}
