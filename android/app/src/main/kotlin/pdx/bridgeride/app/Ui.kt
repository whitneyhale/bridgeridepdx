package pdx.bridgeride.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun lookShape(): Shape = RoundedCornerShape(LocalLook.current.corner)

/**
 * A surface in the current look. E: white sticker with an ink border on a hard ink shadow.
 * C: concrete panel with a hairline. The shadow is a plain offset box, so it costs nothing to draw.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    val look = LocalLook.current
    val shape = lookShape()
    Box(modifier.padding(end = look.stickerShadow, bottom = look.stickerShadow)) {
        if (look.stickerShadow > 0.dp) {
            Box(Modifier.matchParentSize().offset(look.stickerShadow, look.stickerShadow).background(look.borderColor, shape))
        }
        Box(Modifier.fillMaxWidth().clip(shape).background(color).border(look.border, look.borderColor, shape)) { content() }
    }
}

/** The one big action on a screen (Start ride). 64 dp tall for gloves. */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = LocalLook.current
    val shape = lookShape()
    Box(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .border(look.border, if (look.dark) MaterialTheme.colorScheme.primary else look.borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
    }
}

/** A secondary action (Locate, GPX, Open App info). 48 dp tall. */
@Composable
fun SmallButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = LocalLook.current
    val shape = lookShape()
    Box(
        modifier.heightIn(min = 48.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(look.border, if (look.dark) MaterialTheme.colorScheme.outline else look.borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Auto, Light, Dark. The chosen one is filled. */
@Composable
fun LookPicker(choice: LookChoice, onChoose: (LookChoice) -> Unit) {
    val look = LocalLook.current
    val shape = lookShape()
    Row(Modifier.fillMaxWidth().clip(shape).border(look.border, if (look.dark) MaterialTheme.colorScheme.outline else look.borderColor, shape)) {
        LookChoice.entries.forEach { c ->
            val on = c == choice
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .semantics { selected = on }
                    .clickable(role = Role.RadioButton) { onChoose(c) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    c.label, fontWeight = FontWeight.Bold,
                    color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** A secondary action on the ride screen (Lock, Re-center). 64 dp tall: rain and gloves. */
@Composable
fun TallButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = LocalLook.current
    val shape = lookShape()
    Box(
        modifier.heightIn(min = 64.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(look.border, if (look.dark) MaterialTheme.colorScheme.outline else look.borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
    }
}

/**
 * An action that needs a 1 s press, so a bump, a raindrop or a glove edge cannot trigger it
 * (End: workflow review 2026-11-05. Unlock: rain and gloves, 0.3). The bar fills while held.
 */
@Composable
fun HoldButton(label: String, hint: String, description: String, modifier: Modifier = Modifier, onDone: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    val done by rememberUpdatedState(onDone)
    val fill = MaterialTheme.colorScheme.primary
    Box(
        modifier.heightIn(min = 64.dp).clip(lookShape())
            .background(MaterialTheme.colorScheme.onBackground)
            // The bar that fills while held. Drawn behind the label so it never changes the button's size.
            .drawBehind { drawRect(fill, size = Size(size.width * progress.coerceIn(0f, 1f), size.height)) }
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    job = scope.launch {
                        val t0 = System.currentTimeMillis()
                        while (progress < 1f) { progress = (System.currentTimeMillis() - t0) / 1000f; delay(16) }
                        done()
                    }
                    waitForUpOrCancellation()
                    job?.cancel(); progress = 0f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = MaterialTheme.colorScheme.background, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(hint, color = MaterialTheme.colorScheme.background, fontSize = 13.sp)
        }
    }
}

/** Warning text always carries the warning icon, so it never depends on color alone (palette fix, 2026-11-09). */
@Composable
fun Warning(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = "Warning", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = LocalLook.current.numbers, maxLines = 1, softWrap = false)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, softWrap = false)
    }
}

/** A small label: "Lift bridge", "New", "3 of 12". */
@Composable
fun Tag(text: String, container: Color = MaterialTheme.colorScheme.secondaryContainer, content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(if (look.dark) 2.dp else 999.dp)
    Text(
        text, color = content, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(shape).background(container).border(if (look.dark) 0.dp else 2.dp, if (look.dark) container else look.borderColor, shape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Marks a part of a screen that is drawn but does not work yet, and says when it is due. */
@Composable
fun NotBuilt(text: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val corner = LocalLook.current.corner
    Row(
        modifier.fillMaxWidth().drawBehind {
            drawRoundRect(
                color, cornerRadius = CornerRadius(corner.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
            )
        }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Not built yet", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text, color = color, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

/** Two or three choices side by side (Rides | Passport). The chosen one is filled. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val look = LocalLook.current
    val shape = lookShape()
    Row(Modifier.fillMaxWidth().clip(shape).border(look.border, if (look.dark) MaterialTheme.colorScheme.outline else look.borderColor, shape)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .semantics { this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Close or back, 48 dp, drawn as two strokes so it needs no icon file. */
@Composable
fun GlyphButton(back: Boolean, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface
    val look = LocalLook.current
    val shape = lookShape()
    Box(
        modifier.size(48.dp).clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(look.border, if (look.dark) MaterialTheme.colorScheme.outline else look.borderColor, shape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = 2.6.dp.toPx()
            if (back) {
                drawLine(color, Offset(size.width * 0.7f, 0f), Offset(size.width * 0.2f, size.height / 2), w, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.2f, size.height / 2), Offset(size.width * 0.7f, size.height), w, StrokeCap.Round)
            } else {
                drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
                drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
            }
        }
    }
}

/** A tappable card in a list (a ride to pick, a ride in the log). */
@Composable
fun CardButton(modifier: Modifier = Modifier, description: String? = null, onClick: () -> Unit, content: @Composable () -> Unit) {
    Panel(modifier) {
        Box(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
                .padding(14.dp),
        ) { content() }
    }
}
