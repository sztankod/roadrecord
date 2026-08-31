package hu.roadrecord.app
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import hu.roadrecord.app.data.EventType
import hu.roadrecord.app.ui.RoadRecordApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  setContent{RoadRecordApp()}
  val app=application as RoadRecordApplication
  lifecycleScope.launch{
   repeatOnLifecycle(Lifecycle.State.STARTED){
    combine(app.repository.days,app.repository.settings){days,settings->days to settings}.collectLatest{(days,settings)->
     val events=days.firstOrNull{day->day.events.none{it.type==EventType.WORK_END}}?.events?.sortedBy{it.timestamp}.orEmpty()
     val tripStartedAt=events.lastOrNull()?.takeIf{it.type==EventType.TRIP_START}?.timestamp
     val limit=settings.keepScreenOnLimitMinutes
     val expiresAt=tripStartedAt?.let{if(limit<=0)Long.MAX_VALUE else it+limit*60_000L}
     val keep=settings.keepScreenOnDuringTrip&&expiresAt!=null&&System.currentTimeMillis()<expiresAt
     setKeepScreenOn(keep)
     if(keep&&expiresAt!=Long.MAX_VALUE){
      delay((expiresAt-System.currentTimeMillis()).coerceAtLeast(0L))
      setKeepScreenOn(false)
     }
    }
   }
  }
 }
 private fun setKeepScreenOn(enabled:Boolean){
  if(enabled)window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
 }
}
