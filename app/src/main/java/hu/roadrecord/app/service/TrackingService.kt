package hu.roadrecord.app.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.*
import com.google.android.gms.location.*
import hu.roadrecord.app.MainActivity
import hu.roadrecord.app.RoadRecordApplication
import hu.roadrecord.app.data.GpsPoint
import kotlinx.coroutines.*

class TrackingService:Service(){
 companion object { const val ACTION_START="hu.roadrecord.START";const val ACTION_STOP="hu.roadrecord.STOP";const val EXTRA_DAY="day";private const val CHANNEL="active_work";private const val ID=77 }
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO); private lateinit var client:FusedLocationProviderClient;private var dayId=0L
 private val callback=object:LocationCallback(){override fun onLocationResult(result:LocationResult){val l=result.lastLocation?:return;scope.launch{val app=application as RoadRecordApplication;val trip=app.repository.activeTrip(dayId)?:return@launch;app.repository.addGpsPoint(GpsPoint(tripId=trip.id,timestamp=l.time,latitude=l.latitude,longitude=l.longitude,accuracy=l.accuracy))}}}
 override fun onCreate(){super.onCreate();client=LocationServices.getFusedLocationProviderClient(this);(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL,"Aktív munkaidő",NotificationManager.IMPORTANCE_LOW))}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{if(intent?.action==ACTION_STOP){stopTracking();return START_NOT_STICKY};dayId=intent?.getLongExtra(EXTRA_DAY,0)?:0;startForeground(ID,notification());requestLocation();return START_STICKY}
 private fun notification():Notification{val pi=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("RoadRecord – Munkaidő folyamatban").setContentText("Az élő munkaidő és útidő rögzítése aktív").setOngoing(true).setContentIntent(pi).build()}
 private fun requestLocation(){if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;val request=LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY,20_000).setMinUpdateIntervalMillis(15_000).setMinUpdateDistanceMeters(50f).build();client.requestLocationUpdates(request,callback,mainLooper)}
 private fun stopTracking(){client.removeLocationUpdates(callback);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
 override fun onDestroy(){client.removeLocationUpdates(callback);scope.cancel();super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}
