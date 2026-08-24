package hu.roadrecord.app.viewmodel
import android.app.Application
import androidx.lifecycle.*
import hu.roadrecord.app.RoadRecordApplication
import hu.roadrecord.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app:Application):AndroidViewModel(app){
 val repo=(app as RoadRecordApplication).repository
 val days=repo.days.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val settings=repo.settings.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),AppSettings())
 val places=repo.places.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val periods=repo.periods.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 private val _message=MutableStateFlow<String?>(null);val message=_message.asStateFlow()
 init{viewModelScope.launch{repo.ensureDefaults();repo.seedDemo()}}
 fun run(block:suspend()->Unit)=viewModelScope.launch{try{block()}catch(e:Exception){_message.value=e.message?:"Ismeretlen hiba"}}
 fun clearMessage(){_message.value=null}
}
