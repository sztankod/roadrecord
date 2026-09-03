package hu.roadrecord.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.roadrecord.app.data.AppSettings
import hu.roadrecord.app.display.ScreenAwakeOptions
import kotlin.math.roundToInt

@Composable
internal fun ScreenSettingsDialog(settings: AppSettings, onDismiss: () -> Unit, onSave: (ScreenAwakeOptions) -> Unit) {
    val initial = remember { ScreenAwakeOptions.from(settings) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var minutes by remember { mutableStateOf(initial.limitMinutes.toString()) }
    var dimEnabled by remember { mutableStateOf(initial.dimEnabled) }
    var dimMinutes by remember { mutableStateOf(initial.dimAfterMinutes.toString()) }
    var percent by remember { mutableFloatStateOf(initial.dimPercent.toFloat()) }
    val limit = minutes.toIntOrNull()
    val dimAfter = dimMinutes.toIntOrNull()
    val limitValid = limit != null && limit in 0..720
    val delayValid = dimAfter != null && dimAfter in 1..120 && (limit == 0 || (limit != null && dimAfter < limit))
    val valid = !enabled || (limitValid && (!dimEnabled || delayValid))

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Kijelző és ébren tartás") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScreenToggle("Képernyő ébren tartása", enabled) { enabled = it }
                Text("Csak akkor működik, amikor a RoadRecord van előtérben, nem csak út közben.", fontSize = 12.sp)
                if (enabled) {
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Ébren tartás (perc)") }, isError = !limitValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("0 = korlátlan; egyébként 1–720 perc. Utána az Android saját kikapcsolási időzítése érvényesül.") })
                    HorizontalDivider()
                    ScreenToggle("Automatikus halványítás", dimEnabled) { dimEnabled = it }
                    if (dimEnabled) {
                        OutlinedTextField(dimMinutes, { dimMinutes = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("Halványítás ennyi perc után") }, isError = !delayValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = { Text(if (!delayValid) "1–120 perc, az ébren tartás időkorlátjánál rövidebb idő." else "Érintés nélküli idő; az alkalmazás működése közben is.") })
                        Text("Halványított fényerő: legfeljebb ${percent.roundToInt()}%")
                        Slider(value = percent, onValueChange = { percent = it }, valueRange = 5f..80f, steps = 14)
                        Text("Ha a rendszer fényereje már alacsonyabb, nem világosítjuk fel a kijelzőt.", fontSize = 11.sp)
                    }
                    Text("Érintésre visszaáll a normál fényerő, és mindkét időzítő újraindul. Másik alkalmazásra váltva visszaállnak az eredeti kijelzőbeállítások.", fontSize = 12.sp)
                    Text("A hosszú ébren tartás növelheti az akkumulátor fogyasztását.", fontSize = 11.sp)
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = {
            onSave(ScreenAwakeOptions(enabled, limit ?: initial.limitMinutes, dimEnabled,
                dimAfter ?: initial.dimAfterMinutes, percent.roundToInt()))
        }) { Text("Mentés") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}

@Composable
private fun ScreenToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
