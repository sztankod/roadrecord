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
import hu.roadrecord.app.ui.widget.DrivingLayout

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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val bandHeight = DrivingLayout.mainBandHeight(maxWidth.value).dp
    Card(
        modifier = Modifier.fillMaxWidth().height(bandHeight),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE1E5EA)),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp).heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(24.dp), shape = CircleShape, color = Color.Transparent,
                border = BorderStroke(1.dp, Color(0xFFD6E9D0))) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(11.dp).background(Color(0xFF23BB64), CircleShape))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text("ÚTON", color = Color(0xFF397D32), fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(12.dp))
            Text("Rögzítés aktív", modifier = Modifier.weight(1f), color = Color(0xFF616161), fontSize = 12.sp, maxLines = 1)
        }
        AndroidView(
            factory = { DrivingAnimationView(it) },
            modifier = Modifier.fillMaxWidth().weight(1f)
                .onGloballyPositioned { inViewport = !it.boundsInWindow().isEmpty },
            onRelease = { it.motionEnabled = false },
            update = { it.motionEnabled = resumed && inViewport },
        )
    }
    }
}
