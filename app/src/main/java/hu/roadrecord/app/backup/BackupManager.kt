package hu.roadrecord.app.backup

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import hu.roadrecord.app.MainActivity
import hu.roadrecord.app.RoadRecordApplication
import hu.roadrecord.app.data.AppSettings
import hu.roadrecord.app.data.RoadRecordDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

object BackupManager {
    private const val WORK_NAME="roadrecord-periodic-backup"
    private val stamp=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    suspend fun create(context:Context,automatic:Boolean=false):Boolean=withContext(Dispatchers.IO){
        val app=context.applicationContext as RoadRecordApplication
        val settings=app.database.dao().settings()?:AppSettings()
        val driveDue=!automatic||settings.backupFrequency=="EACH_WORK";val copyToDrive=driveDue&&(!settings.backupWifiOnly||isWifi(context))
        app.database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val temp=File(context.cacheDir,"roadrecord-backup.zip")
        ZipOutputStream(temp.outputStream().buffered()).use{zip->
            addFile(zip,context.getDatabasePath("roadrecord.db"),"database/roadrecord.db")
            val photos=File(context.filesDir,"photos")
            photos.listFiles()?.filter{it.isFile}?.forEach{addFile(zip,it,"photos/${it.name}")}
        }
        val name="roadrecord-backup-${LocalDateTime.now().format(stamp)}.zip"
        val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,"application/zip");put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/RoadRecord")}
        context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?.let{uri->context.contentResolver.openOutputStream(uri)?.use{out->temp.inputStream().use{it.copyTo(out)}}}
        if(copyToDrive&&settings.backupDriveTreeUri.isNotBlank())runCatching{val tree=DocumentFile.fromTreeUri(context,Uri.parse(settings.backupDriveTreeUri));tree?.createFile("application/zip",name)?.uri?.let{uri->context.contentResolver.openOutputStream(uri)?.use{out->temp.inputStream().use{it.copyTo(out)}}}}
        else if(driveDue&&settings.backupDriveTreeUri.isNotBlank())WorkManager.getInstance(context).enqueueUniqueWork("roadrecord-pending-drive",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<BackupWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()).build())
        app.database.dao().saveSettings(settings.copy(lastBackupAt=System.currentTimeMillis()))
        temp.delete();true
    }

    fun schedule(context:Context,settings:AppSettings){
        val wm=WorkManager.getInstance(context)
        if(settings.backupFrequency=="EACH_WORK"||settings.backupFrequency=="MANUAL"){wm.cancelUniqueWork(WORK_NAME);return}
        val days=if(settings.backupFrequency=="WEEKLY")7L else 1L
        val constraints=Constraints.Builder().setRequiredNetworkType(if(settings.backupWifiOnly)NetworkType.UNMETERED else NetworkType.CONNECTED).build()
        val request=PeriodicWorkRequestBuilder<BackupWorker>(days,TimeUnit.DAYS).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.UPDATE,request)
    }

    suspend fun restore(context:Context,uri:Uri):Nothing=withContext(Dispatchers.IO){
        val stage=File(context.cacheDir,"restore-${System.currentTimeMillis()}").apply{mkdirs()}
        context.contentResolver.openInputStream(uri)?.use{input->ZipInputStream(input.buffered()).use{zip->var entry=zip.nextEntry;while(entry!=null){val out=File(stage,entry.name);require(out.canonicalPath.startsWith(stage.canonicalPath+File.separator));if(entry.isDirectory)out.mkdirs()else{out.parentFile?.mkdirs();out.outputStream().use{zip.copyTo(it)}};entry=zip.nextEntry}}}?:error("A mentés nem olvasható.")
        val db=File(stage,"database/roadrecord.db");require(db.exists()){"Ez nem RoadRecord biztonsági mentés."}
        RoadRecordDatabase.closeForRestore();val target=context.getDatabasePath("roadrecord.db");target.parentFile?.mkdirs();db.copyTo(target,true);File(target.path+"-wal").delete();File(target.path+"-shm").delete()
        val photoTarget=File(context.filesDir,"photos").apply{mkdirs()};File(stage,"photos").listFiles()?.forEach{it.copyTo(File(photoTarget,it.name),true)}
        val restart=Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK);PendingIntent.getActivity(context,991,restart,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT).send();exitProcess(0)
    }

    private fun addFile(zip:ZipOutputStream,file:File,name:String){if(!file.exists())return;zip.putNextEntry(ZipEntry(name));file.inputStream().use{it.copyTo(zip)};zip.closeEntry()}
    private fun isWifi(context:Context):Boolean{val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;val caps=cm.getNetworkCapabilities(cm.activeNetwork)?:return false;return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}
}

class BackupWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){override suspend fun doWork()=if(runCatching{BackupManager.create(applicationContext)}.getOrDefault(false))Result.success()else Result.retry()}
