package hu.roadrecord.app.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.core.app.*
import com.google.android.gms.location.*
import hu.roadrecord.app.MainActivity
import hu.roadrecord.app.RoadRecordApplication
import hu.roadrecord.app.data.GpsPoint
import kotlinx.coroutines.*

class TrackingService:Service(){
 companion object { const val ACTION_START="hu.roadrecord.START";const val ACTION_STOP="hu.roadrecord.STOP";const val EXTRA_DAY="day";private const val CHANNEL="active_work";private const val ID=77 }
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);private lateinit var client:FusedLocationProviderClient;private var dayId=0L;private var bakeryInside:Boolean?=null;private var currentStopId:Long?=null
 private val callback=object:LocationCallback(){override fun onLocationResult(result:LocationResult){val l=result.lastLocation?:return;scope.launch{val app=application as RoadRecordApplication;val inside=app.repository.bakeryPresence(l.latitude,l.longitude,l.accuracy);if(inside!=null){val previous=bakeryInside;if(previous!=null&&previous!=inside)app.repository.automaticBakeryTransition(dayId,left=!inside,time=l.time)else if(previous==null&&inside&&app.repository.activeTrip(dayId)!=null)app.repository.automaticBakeryTransition(dayId,left=false,time=l.time);bakeryInside=inside};val stop=app.repository.recognizePlannedStops(dayId,l.latitude,l.longitude,l.time,l.accuracy);if(stop?.id!=currentStopId){currentStopId=stop?.id;if(stop!=null&&!stop.gpsManuallyConfirmed)playCoordinateWarning()};if(NextStopOverlayService.visible){val next=app.repository.nextPlannedStop(dayId);startService(Intent(this@TrackingService,NextStopOverlayService::class.java).setAction(NextStopOverlayService.ACTION_UPDATE).putExtra(NextStopOverlayService.EXTRA_NAME,next?.name?:"Nincs további megálló").putExtra(NextStopOverlayService.EXTRA_ADDRESS,next?.officialAddress.orEmpty()))};val trip=app.repository.activeTrip(dayId)?:return@launch;app.repository.addGpsPoint(GpsPoint(tripId=trip.id,timestamp=l.time,latitude=l.latitude,longitude=l.longitude,accuracy=l.accuracy))}}}
 override fun onCreate(){super.onCreate();client=LocationServices.getFusedLocationProviderClient(this);val manager=getSystemService(NOTIFICATION_SERVICE) as NotificationManager;manager.createNotificationChannel(NotificationChannel(CHANNEL,"Aktív munkaidő",NotificationManager.IMPORTANCE_LOW))}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{if(intent?.action==ACTION_STOP){stopTracking();return START_NOT_STICKY};intent?.getLongExtra(EXTRA_DAY,0)?.takeIf{it>0}?.let{if(dayId!=it)bakeryInside=null;dayId=it};startForeground(ID,notification());requestLocation();return START_STICKY}
 private fun notification():Notification{val pi=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("RoadRecord – Munkaidő folyamatban").setContentText("Az élő munkaidő és útidő rögzítése aktív").setOngoing(true).setContentIntent(pi).build()}
 private fun playCoordinateWarning(){ToneGenerator(AudioManager.STREAM_NOTIFICATION,90).also{tone->tone.startTone(ToneGenerator.TONE_PROP_BEEP2,650);android.os.Handler(mainLooper).postDelayed({tone.release()},800)}}
 private fun requestLocation(){if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;val request=LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,12_000).setMinUpdateIntervalMillis(8_000).setMinUpdateDistanceMeters(10f).build();client.requestLocationUpdates(request,callback,mainLooper)}
 private fun stopTracking(){client.removeLocationUpdates(callback);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
 override fun onDestroy(){client.removeLocationUpdates(callback);scope.cancel();super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}
