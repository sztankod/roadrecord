package hu.roadrecord.app.theme

import android.content.Context
import android.content.Intent
import java.time.*
import kotlin.math.*

enum class AppearanceMode { AUTO, LIGHT, DARK }
data class KnownLocation(val latitude:Double,val longitude:Double,val recordedAt:Long)
data class SolarWindow(val sunrise:Instant,val sunset:Instant)

object ThemeStore {
    const val ACTION_LOCATION_UPDATED="hu.roadrecord.app.THEME_LOCATION_UPDATED"
    private const val PREFS="roadrecord_theme"
    private const val MODE="mode";private const val LAT="lat";private const val LON="lon";private const val AT="at"
    fun mode(context:Context)=runCatching{AppearanceMode.valueOf(context.getSharedPreferences(PREFS,0).getString(MODE,"AUTO")!!)}.getOrDefault(AppearanceMode.AUTO)
    fun saveMode(context:Context,mode:AppearanceMode){context.getSharedPreferences(PREFS,0).edit().putString(MODE,mode.name).apply()}
    fun location(context:Context):KnownLocation?{val p=context.getSharedPreferences(PREFS,0);return if(!p.contains(LAT)||!p.contains(LON))null else KnownLocation(Double.fromBits(p.getLong(LAT,0)),Double.fromBits(p.getLong(LON,0)),p.getLong(AT,0))}
    fun saveLocation(context:Context,latitude:Double,longitude:Double,at:Long){val old=location(context);if(old!=null&&at-old.recordedAt<15*60_000)return;context.getSharedPreferences(PREFS,0).edit().putLong(LAT,latitude.toBits()).putLong(LON,longitude.toBits()).putLong(AT,at).apply();context.sendBroadcast(Intent(ACTION_LOCATION_UPDATED).setPackage(context.packageName))}
}

object SolarTheme {
    fun window(date:LocalDate,latitude:Double,longitude:Double,zone:ZoneId):SolarWindow?{
        val rise=solarUtc(date,latitude,longitude,true)?:return null
        val set=solarUtc(date,latitude,longitude,false)?:return null
        val start=date.atStartOfDay(ZoneOffset.UTC).toInstant()
        return SolarWindow(start.plusMillis((rise*3_600_000).roundToLong()),start.plusMillis((set*3_600_000).roundToLong()))
    }
    fun isDark(now:Instant,latitude:Double,longitude:Double,zone:ZoneId):Boolean{
        val localDate=now.atZone(zone).toLocalDate();val today=window(localDate,latitude,longitude,zone)?:return false
        return now.isBefore(today.sunrise)||!now.isBefore(today.sunset.minusSeconds(30*60))
    }
    fun nextChange(now:Instant,latitude:Double,longitude:Double,zone:ZoneId):Instant{
        val date=now.atZone(zone).toLocalDate();val candidates=(-1L..2L).flatMap{offset->window(date.plusDays(offset),latitude,longitude,zone)?.let{listOf(it.sunrise,it.sunset.minusSeconds(30*60))}.orEmpty()}.filter{it.isAfter(now)}
        return candidates.minOrNull()?:now.plusSeconds(6*3600)
    }
    private fun solarUtc(date:LocalDate,lat:Double,lon:Double,sunrise:Boolean):Double?{
        val day=date.dayOfYear.toDouble();val lngHour=lon/15.0;val t=day+((if(sunrise)6.0 else 18.0)-lngHour)/24.0
        val m=0.9856*t-3.289;var l=m+1.916*sin(Math.toRadians(m))+.020*sin(Math.toRadians(2*m))+282.634;l=(l+360)%360
        var ra=Math.toDegrees(atan(.91764*tan(Math.toRadians(l))));ra=(ra+360)%360;ra+=(floor(l/90)*90-floor(ra/90)*90);ra/=15
        val sinDec=.39782*sin(Math.toRadians(l));val cosDec=cos(asin(sinDec));val cosH=(cos(Math.toRadians(90.833))-sinDec*sin(Math.toRadians(lat)))/(cosDec*cos(Math.toRadians(lat)))
        if(cosH !in -1.0..1.0)return null
        var h=if(sunrise)360-Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH));h/=15
        return ((h+ra-.06571*t-6.622)-lngHour+24)%24
    }
}
