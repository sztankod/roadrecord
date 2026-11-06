package hu.roadrecord.app
import android.app.Application
import hu.roadrecord.app.data.RoadRecordDatabase
import hu.roadrecord.app.repository.RoadRecordRepository
import hu.roadrecord.app.service.NextStopOverlayService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import hu.roadrecord.app.auth.ServerAuth
import hu.roadrecord.app.sync.ServerSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
class RoadRecordApplication:Application(),DefaultLifecycleObserver{
 companion object{@Volatile var isInForeground=true;private set}
 val database by lazy{RoadRecordDatabase.get(this)};val repository by lazy{RoadRecordRepository(database.dao(),this)}
 val auth by lazy{ServerAuth(this)};private val appScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 override fun onCreate(){super<Application>.onCreate();ProcessLifecycleOwner.get().lifecycle.addObserver(this)}
 override fun onStart(owner:LifecycleOwner){isInForeground=true;NextStopOverlayService.onAppForegroundChanged(true);appScope.launch{if(auth.checkStatus())ServerSyncManager.enqueue(this@RoadRecordApplication)}}
 override fun onStop(owner:LifecycleOwner){isInForeground=false;NextStopOverlayService.onAppForegroundChanged(false);ServerSyncManager.enqueue(this)}
}
