package pdx.bridgeride.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
