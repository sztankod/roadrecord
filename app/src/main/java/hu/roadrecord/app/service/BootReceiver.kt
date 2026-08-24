package hu.roadrecord.app.service
import android.content.*
import hu.roadrecord.app.RoadRecordApplication
import kotlinx.coroutines.*
class BootReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){if(intent.action!=Intent.ACTION_BOOT_COMPLETED)return;val pending=goAsync();CoroutineScope(Dispatchers.IO).launch{try{val day=(context.applicationContext as RoadRecordApplication).repository.activeDay();if(day!=null&&day.events.none{it.type.name=="WORK_END"})context.startForegroundService(Intent(context,TrackingService::class.java).setAction(TrackingService.ACTION_START).putExtra(TrackingService.EXTRA_DAY,day.day.id))}finally{pending.finish()}}}}
