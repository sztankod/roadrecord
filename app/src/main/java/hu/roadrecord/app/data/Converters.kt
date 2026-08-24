package hu.roadrecord.app.data
import androidx.room.TypeConverter
class Converters {
 @TypeConverter fun eventToString(v:EventType)=v.name
 @TypeConverter fun stringToEvent(v:String)=EventType.valueOf(v)
 @TypeConverter fun placeToString(v:PlaceType)=v.name
 @TypeConverter fun stringToPlace(v:String)=PlaceType.valueOf(v)
 @TypeConverter fun priorityToString(v:Priority)=v.name
 @TypeConverter fun stringToPriority(v:String)=Priority.valueOf(v)
}
