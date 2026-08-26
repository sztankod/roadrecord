package hu.roadrecord.app
import android.app.Application
import hu.roadrecord.app.data.RoadRecordDatabase
import hu.roadrecord.app.repository.RoadRecordRepository
class RoadRecordApplication:Application(){ val database by lazy{RoadRecordDatabase.get(this)}; val repository by lazy{RoadRecordRepository(database.dao(),this)} }
