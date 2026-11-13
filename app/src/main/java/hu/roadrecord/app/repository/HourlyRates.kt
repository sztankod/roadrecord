package hu.roadrecord.app.repository

import hu.roadrecord.app.data.AppSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

data class HourlyRateEntry(val effectiveDate:String,val rate:Long)

fun AppSettings.hourlyRateEntries():List<HourlyRateEntry>{
    val parsed=hourlyRateHistory.split(';').mapNotNull{item->
        val parts=item.split(':',limit=2);if(parts.size!=2)return@mapNotNull null
        val date=parts[0];val rate=parts[1].toLongOrNull()?:return@mapNotNull null
        if(runCatching{LocalDate.parse(date)}.isSuccess&&rate>0)HourlyRateEntry(date,rate)else null
    }
    return (parsed.ifEmpty{listOf(HourlyRateEntry("1970-01-01",hourlyRate.coerceAtLeast(1)))})
        .distinctBy{it.effectiveDate}.sortedBy{it.effectiveDate}
}

fun AppSettings.withHourlyRateEntries(entries:List<HourlyRateEntry>):AppSettings{
    val normalized=entries.filter{it.rate>0&&runCatching{LocalDate.parse(it.effectiveDate)}.isSuccess}
        .distinctBy{it.effectiveDate}.sortedBy{it.effectiveDate}.ifEmpty{hourlyRateEntries()}
    val encoded=normalized.joinToString(";"){"${it.effectiveDate}:${it.rate}"}
    val today=LocalDate.now().toString()
    val current=normalized.lastOrNull{it.effectiveDate<=today}?:normalized.first()
    return copy(hourlyRate=current.rate,hourlyRateHistory=encoded)
}

fun AppSettings.hourlyRateAt(timestamp:Long):Long{
    val date=Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    return hourlyRateEntries().lastOrNull{it.effectiveDate<=date}?.rate?:hourlyRateEntries().first().rate
}

internal fun earningsForIntervals(intervals:List<LongRange>,settings:AppSettings):Long{
    val zone=ZoneId.systemDefault()
    var total=0.0
    intervals.forEach{range->
        var cursor=range.first
        val endExclusive=range.last+1
        while(cursor<endExclusive){
            val day=Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val nextMidnight=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val segmentEnd=minOf(endExclusive,nextMidnight)
            total+=(segmentEnd-cursor).toDouble()*settings.hourlyRateAt(cursor)/3_600_000.0
            cursor=segmentEnd
        }
    }
    return total.roundToLong()
}
