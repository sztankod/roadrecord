package hu.roadrecord.app.backup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import hu.roadrecord.app.RoadRecordApplication
import hu.roadrecord.app.data.LocationPlace
import hu.roadrecord.app.data.PlaceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object StopPdfManager {
    const val TRIGGER_CHANGE="CHANGE"
    const val TRIGGER_WORK_END="WORK_END"
    const val TRIGGER_MANUAL="MANUAL"
    private const val FILE_NAME="RoadRecord-megallok.pdf"
    private const val WORK_NAME="roadrecord-stops-pdf"
    private const val KEY_TRIGGER="trigger"
    private val mutex=Mutex()

    suspend fun request(context:Context,trigger:String):Boolean=withContext(Dispatchers.IO){
        val app=context.applicationContext as RoadRecordApplication
        val settings=app.database.dao().settings()?:return@withContext false
        val due=trigger==TRIGGER_MANUAL||settings.stopPdfFrequency=="ON_CHANGE"&&trigger==TRIGGER_CHANGE||settings.stopPdfFrequency=="EACH_WORK"&&trigger==TRIGGER_WORK_END
        if(!due||settings.backupDriveTreeUri.isBlank())return@withContext false
        if(settings.stopPdfWifiOnly&&!isWifi(context)){
            val request=OneTimeWorkRequestBuilder<StopPdfWorker>().setInputData(workDataOf(KEY_TRIGGER to trigger)).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME,ExistingWorkPolicy.REPLACE,request)
            return@withContext true
        }
        generateAndUpload(context)
    }

    private suspend fun generateAndUpload(context:Context):Boolean=mutex.withLock{
        val app=context.applicationContext as RoadRecordApplication
        val dao=app.database.dao();val settings=dao.settings()?:return@withLock false
        if(settings.backupDriveTreeUri.isBlank())return@withLock false
        val places=dao.placesNow();val groups=dao.tourOrderGroupsNow().associateBy{it.id}
        val temp=File(context.cacheDir,FILE_NAME)
        createPdf(temp,places,groups.mapValues{it.value.name},settings.stopPdfIncludeCodes,settings.stopPdfIncludePhotos)
        val uploaded=runCatching{
            val tree=DocumentFile.fromTreeUri(context,Uri.parse(settings.backupDriveTreeUri))?:error("A Drive-mappa nem érhető el")
            val target=tree.listFiles().firstOrNull{it.name==FILE_NAME}?:tree.createFile("application/pdf",FILE_NAME.removeSuffix(".pdf"))?:error("A PDF nem hozható létre")
            context.contentResolver.openOutputStream(target.uri,"wt")?.use{out->temp.inputStream().use{it.copyTo(out)}}?:error("A PDF nem írható")
        }.isSuccess
        temp.delete();if(uploaded)dao.markStopPdf(System.currentTimeMillis());uploaded
    }

    private fun createPdf(file:File,places:List<LocationPlace>,groups:Map<Long,String>,includeCodes:Boolean,includePhotos:Boolean){
        val document=PdfDocument();val title=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(8,42,82);textSize=25f;isFakeBoldText=true};val heading=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(8,42,82);textSize=18f;isFakeBoldText=true};val label=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.DKGRAY;textSize=10f;isFakeBoldText=true};val body=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(30,35,42);textSize=12f}
        var pageNumber=1
        fun newPage()=document.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber++).create())
        var page=newPage();var canvas=page.canvas
        canvas.drawText("RoadRecord megállók",36f,62f,title)
        canvas.drawText("${places.size} megálló • Frissítve: ${DateTimeFormatter.ofPattern("yyyy.MM.dd. HH:mm").withZone(ZoneId.systemDefault()).format(Instant.now())}",36f,88f,body)
        canvas.drawText("A dokumentum automatikusan frissül, és ugyanazt a Drive-fájlt írja felül.",36f,112f,body)
        document.finishPage(page)
        places.sortedWith(compareBy<LocationPlace>{it.type.ordinal}.thenBy{it.name.lowercase(Locale("hu"))}).forEach{place->
            page=newPage();canvas=page.canvas;var y=55f
            canvas.drawText(place.name,36f,y,title);y+=28f
            canvas.drawLine(36f,y,559f,y,Paint().apply{color=Color.LTGRAY;strokeWidth=1f});y+=25f
            fun row(name:String,value:String){canvas.drawText(name,36f,y,label);y=drawWrapped(canvas,value,165f,y,390f,body)+18f}
            row("Típus",typeName(place.type));row("Állapot",if(place.active)"Aktív" else "Inaktív");row("Hivatalos cím",place.officialAddress.ifBlank{"Nincs megadva"})
            row("GPS-koordináta",if(place.latitude!=null&&place.longitude!=null)String.format(Locale.US,"%.6f, %.6f",place.latitude,place.longitude) else "Nincs megadva")
            row("GPS forrása",if(place.gpsManuallyConfirmed)"Manuálisan pontosított" else "Cím alapján / nem pontosított");row("Felismerési sugár","${place.recognitionRadiusMeters} méter")
            row("Túracsoport",place.tourOrderGroupId?.let{groups[it]}?:"Nincs");row("Csoporton belüli sorrend",if(place.tourOrderGroupId!=null)(place.defaultTourOrder+1).toString() else "—")
            row("Átlagos tartózkodás",duration(place.averageDwellMillis,place.dwellSampleCount));if(includeCodes)row(if(place.type==PlaceType.FUEL)"Kártyakód" else "Kapukód",place.encryptedGateCode?.ifBlank{null}?:"Nincs")
            row("Megjegyzés",place.note.ifBlank{"Nincs"})
            if(includePhotos){val photos=listOf("Kapu / bejárat fotó" to place.entrancePhotoPath,"Kulcsfotó" to place.photoPath).filter{!it.second.isNullOrBlank()&&File(it.second!!).exists()};photos.forEach{(name,path)->if(y>610f)return@forEach;canvas.drawText(name,36f,y,label);y+=10f;decodeScaled(path!!,510,190)?.let{bitmap->val ratio=minOf(510f/bitmap.width,190f/bitmap.height);val width=bitmap.width*ratio;val height=bitmap.height*ratio;canvas.drawBitmap(bitmap,null,android.graphics.RectF(36f,y,36f+width,y+height),null);y+=height+15f;bitmap.recycle()}}}
            canvas.drawText("$pageNumber / ${places.size+1}",510f,815f,label);document.finishPage(page)
        }
        file.outputStream().use{document.writeTo(it)};document.close()
    }

    private fun drawWrapped(canvas:android.graphics.Canvas,text:String,x:Float,startY:Float,maxWidth:Float,paint:Paint):Float{var y=startY;var line="";text.split(Regex("\\s+")).forEach{word->val candidate=if(line.isEmpty())word else "$line $word";if(paint.measureText(candidate)>maxWidth&&line.isNotEmpty()){canvas.drawText(line,x,y,paint);y+=15f;line=word}else line=candidate};if(line.isNotEmpty())canvas.drawText(line,x,y,paint);return y}
    private fun decodeScaled(path:String,maxWidth:Int,maxHeight:Int):Bitmap?{val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(path,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)return null;var sample=1;while(bounds.outWidth/sample>maxWidth*2||bounds.outHeight/sample>maxHeight*2)sample*=2;return BitmapFactory.decodeFile(path,BitmapFactory.Options().apply{inSampleSize=sample})}
    private fun typeName(type:PlaceType)=when(type){PlaceType.HOME->"Home / pékség";PlaceType.BAKERY->"Pékség";PlaceType.CLIENT->"Megálló";PlaceType.FUEL->"Tankolás"}
    private fun duration(millis:Long,samples:Int)=if(samples<=0||millis<=0)"Nincs még adat" else String.format(Locale("hu"),"%d perc (%d minta)",millis/60_000,samples)
    private fun isWifi(context:Context):Boolean{val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;val caps=cm.getNetworkCapabilities(cm.activeNetwork)?:return false;return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}
}

class StopPdfWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){override suspend fun doWork()=if(runCatching{StopPdfManager.request(applicationContext,inputData.getString("trigger")?:StopPdfManager.TRIGGER_MANUAL)}.getOrDefault(false))Result.success()else Result.retry()}
