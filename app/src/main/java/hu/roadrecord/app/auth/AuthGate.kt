package hu.roadrecord.app.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.roadrecord.app.RoadRecordApplication
import hu.roadrecord.app.ui.RoadRecordLogo
import kotlinx.coroutines.launch
import hu.roadrecord.app.sync.CentralStopsManager

@Composable fun AuthGate(app:RoadRecordApplication,content: @Composable () -> Unit){
    val auth=app.auth;val state by auth.state.collectAsStateWithLifecycle();val scope=rememberCoroutineScope();LaunchedEffect(Unit){auth.initialize()}
    if(state.authenticated){
        content()
        if(!auth.centralStopsChoiceMade){
            var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
            fun loadStops(central:Boolean){busy=true;scope.launch{runCatching{CentralStopsManager.download(app,central)}.onSuccess{busy=false}.onFailure{failure->busy=false;if(failure is SessionExpiredException)auth.expireSession()else error=failure.message}}}
            AlertDialog(onDismissRequest={},title={Text("Megállók betöltése")},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){Text("Válaszd ki, melyik megállólistát töltse le ez a telepítés.");if(state.isAdmin)Text("Adminisztrátorként később a Beállításokban frissítheted a központi tárat.",style=MaterialTheme.typography.bodySmall);error?.let{Text(it,color=MaterialTheme.colorScheme.error)};if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())}},confirmButton={Button(enabled=!busy,onClick={loadStops(true)}){Text("Központi megállók")}},dismissButton={TextButton(enabled=!busy,onClick={loadStops(false)}){Text("Saját megállólista")}})
        }
        return
    }
    MaterialTheme{Surface(Modifier.fillMaxSize()){Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center){Card(Modifier.widthIn(max=430.dp)){Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(13.dp)){RoadRecordLogo();Text("RoadRecord",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);if(state.loading){CircularProgressIndicator();Text("Engedély ellenőrzése…")}else if(state.blocked){Text(state.message?:"A hozzáférés nem engedélyezett.",color=MaterialTheme.colorScheme.error);Button({scope.launch{auth.checkStatus()}}){Text("Újraellenőrzés")};TextButton({auth.signOut()}){Text("Másik e-mail-cím")}}else{AuthForm(state.email,{email->scope.launch{auth.requestCode(email).onFailure{}}},{email,code->scope.launch{auth.verify(email,code).onFailure{}}},state.message)}}}}}}
}

@Composable private fun AuthForm(initialEmail:String,onRequest:(String)->Unit,onVerify:(String,String)->Unit,message:String?){var email by remember{mutableStateOf(initialEmail)};var code by remember{mutableStateOf("")};var sent by remember{mutableStateOf(false)};Text("Azonosítás e-mail-címmel",fontWeight=FontWeight.Bold);Text("A regisztráció automatikus. E-mailben egy hatjegyű kódot küldünk.",style=MaterialTheme.typography.bodySmall);OutlinedTextField(email,{email=it},label={Text("E-mail-cím")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth());if(sent)OutlinedTextField(code,{code=it.filter(Char::isDigit).take(6)},label={Text("Ellenőrzőkód")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.fillMaxWidth());message?.let{Text(it,style=MaterialTheme.typography.bodySmall)};Button(onClick={if(sent)onVerify(email,code)else{sent=true;onRequest(email)}},enabled=email.contains('@')&&(!sent||code.length==6),modifier=Modifier.fillMaxWidth()){Text(if(sent)"Belépés" else "Ellenőrzőkód küldése")};if(sent)TextButton({sent=false;code=""}){Text("E-mail-cím módosítása")}}
