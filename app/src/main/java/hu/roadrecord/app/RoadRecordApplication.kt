package hu.roadrecord.app
import android.app.Application
import hu.roadrecord.app.data.RoadRecordDatabase
import hu.roadrecord.app.repository.RoadRecordRepository
import hu.roadrecord.app.service.NextStopOverlayService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
class RoadRecordApplication:Application(),DefaultLifecycleObserver{
 companion object{@Volatile var isInForeground=true;private set}
 val database by lazy{RoadRecordDatabase.get(this)};val repository by lazy{RoadRecordRepository(database.dao(),this)}
 override fun onCreate(){super<Application>.onCreate();ProcessLifecycleOwner.get().lifecycle.addObserver(this)}
 override fun onStart(owner:LifecycleOwner){isInForeground=true;NextStopOverlayService.onAppForegroundChanged(true)}
 override fun onStop(owner:LifecycleOwner){isInForeground=false;NextStopOverlayService.onAppForegroundChanged(false)}
}
