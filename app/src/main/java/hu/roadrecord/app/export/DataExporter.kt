package hu.roadrecord.app.export
import android.content.*
import androidx.core.content.FileProvider
import hu.roadrecord.app.data.*
import hu.roadrecord.app.repository.summary
import java.io.File
import hu.roadrecord.app.RoadRecordApplication
import android.widget.Toast

object DataExporter{
 fun csv(context:Context,days:List<DayWithEvents>,settings:AppSettings)=share(context,"roadrecord-export.csv",buildString{appendLine("datum;munkaido_perc;utido_perc;kilometer;kereset_ft");days.sortedBy{it.day.date}.forEach{val s=it.summary(settings);appendLine("${it.day.date};${s.workMillis/60000};${s.travelMillis/60000};${"%.2f".format(s.distanceMeters/1000)};${s.earnings}")}})
 fun json(context:Context,days:List<DayWithEvents>)=share(context,"roadrecord-export.json",days.joinToString(prefix="[",postfix="]"){d->"{\"date\":\"${d.day.date}\",\"events\":[${d.events.joinToString{e->"{\"type\":\"${e.type}\",\"timestamp\":${e.timestamp}}"}}]}"})
 private fun share(context:Context,name:String,text:String){val access=(context.applicationContext as RoadRecordApplication).auth.state.value;if(access.pending||access.blocked){Toast.makeText(context,if(access.blocked)"A felhasználó le van tiltva." else "Várakozó állapotban az exportálás nem érhető el.",Toast.LENGTH_LONG).show();return};val dir=File(context.filesDir,"exports").apply{mkdirs()};val f=File(dir,name);f.writeText(text);val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",f);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type=if(name.endsWith("csv"))"text/csv" else "application/json";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"RoadRecord export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
}
