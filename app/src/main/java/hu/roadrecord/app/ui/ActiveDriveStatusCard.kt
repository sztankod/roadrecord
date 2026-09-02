package hu.roadrecord.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hu.roadrecord.app.ui.widget.DrivingAnimationView

/** Only composed while the existing event model says an active trip is in progress. */
@Composable
internal fun ActiveDriveStatusCard() {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var inViewport by remember { mutableStateOf(false) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121820)),
        border = BorderStroke(1.dp, Color(0xFF343B43)),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(30.dp), shape = CircleShape, color = Color.Transparent,
                border = BorderStroke(1.dp, Color(0xFF548343))) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(13.dp).background(Color(0xFF8DD16D), CircleShape))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text("ÚTON", color = Color(0xFF8DD16D), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text("Rögzítés aktív", color = Color(0xFFB9BDC2), fontSize = 12.sp)
        }
        AndroidView(
            factory = { DrivingAnimationView(it) },
            modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).aspectRatio(400f / 145f)
                .onGloballyPositioned { inViewport = !it.boundsInWindow().isEmpty },
            onRelease = { it.motionEnabled = false },
            update = { it.motionEnabled = resumed && inViewport },
        )
    }
}
