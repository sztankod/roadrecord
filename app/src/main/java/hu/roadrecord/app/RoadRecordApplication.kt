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
import hu.roadrecord.app.sync.ServerLiveStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
class RoadRecordApplication:Application(),DefaultLifecycleObserver{
 companion object{@Volatile var isInForeground=true;private set}
 val database by lazy{RoadRecordDatabase.get(this)};val repository by lazy{RoadRecordRepository(database.dao(),this)}
 val auth by lazy{ServerAuth(this)};private val appScope=CoroutineScope(SupervisorJob()+Dispatchers.IO);private var heartbeatJob:Job?=null
 override fun onCreate(){super<Application>.onCreate();ProcessLifecycleOwner.get().lifecycle.addObserver(this)}
 override fun onStart(owner:LifecycleOwner){isInForeground=true;NextStopOverlayService.onAppForegroundChanged(true);heartbeatJob?.cancel();heartbeatJob=appScope.launch{if(auth.checkStatus()){do{ServerLiveStatus.send(this@RoadRecordApplication,repository.activeDay()?.day?.id?:0L);delay(60_000)}while(isInForeground)}};if(auth.syncPolicy=="APP_ACTIVITY")ServerSyncManager.enqueue(this)}
 override fun onStop(owner:LifecycleOwner){isInForeground=false;heartbeatJob?.cancel();heartbeatJob=null;NextStopOverlayService.onAppForegroundChanged(false);if(auth.syncPolicy=="APP_ACTIVITY")ServerSyncManager.enqueue(this)}
}
