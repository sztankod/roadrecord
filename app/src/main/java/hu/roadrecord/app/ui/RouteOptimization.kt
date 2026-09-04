package hu.roadrecord.app.ui

import hu.roadrecord.app.data.LocationPlace
import java.util.Collections
import kotlin.math.*

internal data class RoadMatrix(val durations:Array<DoubleArray>,val distances:Array<DoubleArray>)
internal data class RouteResult(val stops:List<LocationPlace>,val seconds:Double,val meters:Double,val roadBased:Boolean)
internal fun optimizeByTime(start:LocationPlace,end:LocationPlace?,stops:List<LocationPlace>,locked:Map<Long,Int>,matrix:RoadMatrix):RouteResult{
    val all=(listOf(start)+stops+listOfNotNull(end)).distinctBy{it.id};val idx=all.mapIndexed{i,p->p.id to i}.toMap();val result=MutableList<LocationPlace?>(stops.size){null};locked.forEach{(id,pos)->stops.firstOrNull{it.id==id}?.let{if(pos in result.indices&&result[pos]==null)result[pos]=it}}
    val free=stops.filter{p->result.none{it?.id==p.id}}.toMutableList();var previous=start
    result.indices.forEach{i->if(result[i]==null){val next=free.minByOrNull{matrix.durations[idx.getValue(previous.id)][idx.getValue(it.id)]}?:return@forEach;result[i]=next;free.remove(next);previous=next}else previous=result[i]!!}
    var route=result.filterNotNull().toMutableList();fun time(r:List<LocationPlace>):Double{val path=listOf(start)+r+listOfNotNull(end);return path.zipWithNext().sumOf{matrix.durations[idx.getValue(it.first.id)][idx.getValue(it.second.id)]}+r.sumOf{it.averageDwellMillis.coerceAtLeast(0)/1000.0}}
    var improved=true;while(improved){improved=false;var best=time(route);for(i in route.indices)for(j in i+1 until route.size){if(locked.containsKey(route[i].id)||locked.containsKey(route[j].id))continue;val candidate=route.toMutableList().also{Collections.swap(it,i,j)};val score=time(candidate);if(score+1<best){route=candidate;best=score;improved=true}}}
    val path=listOf(start)+route+listOfNotNull(end);return RouteResult(route,path.zipWithNext().sumOf{matrix.durations[idx.getValue(it.first.id)][idx.getValue(it.second.id)]}+route.sumOf{it.averageDwellMillis.coerceAtLeast(0)/1000.0},path.zipWithNext().sumOf{matrix.distances[idx.getValue(it.first.id)][idx.getValue(it.second.id)]},true)
}
internal fun airFallback(start:LocationPlace,end:LocationPlace?,stops:List<LocationPlace>,locked:Map<Long,Int>):RouteResult{if((listOf(start)+stops+listOfNotNull(end)).any{it.latitude==null||it.longitude==null})return RouteResult(stops,Double.NaN,Double.NaN,false);val result=MutableList<LocationPlace?>(stops.size){null};locked.forEach{(id,pos)->stops.firstOrNull{it.id==id}?.let{if(pos in result.indices)result[pos]=it}};val free=stops.filter{p->result.none{it?.id==p.id}}.toMutableList();var current=start;result.indices.forEach{i->if(result[i]==null){val next=free.minBy{routeDistance(current,it)};result[i]=next;free.remove(next);current=next}else current=result[i]!!};val route=result.filterNotNull();val path=listOf(start)+route+listOfNotNull(end);val km=path.zipWithNext().sumOf{sqrt(routeDistance(it.first,it.second))*111.0*1.25};return RouteResult(route,km/55.0*3600+route.sumOf{it.averageDwellMillis.coerceAtLeast(0)/1000.0},km*1000,false)}
internal fun routeDistance(a:LocationPlace,b:LocationPlace):Double{val lat1=a.latitude?:return Double.MAX_VALUE;val lat2=b.latitude?:return Double.MAX_VALUE;val lon1=a.longitude?:return Double.MAX_VALUE;val lon2=b.longitude?:return Double.MAX_VALUE;val x=(lon2-lon1)*cos(Math.toRadians((lat1+lat2)/2));val y=lat2-lat1;return x*x+y*y}
