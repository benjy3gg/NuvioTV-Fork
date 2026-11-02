package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.player.dvfel.GazelleFelControl
import com.nuvio.tv.core.player.dvfel.GazelleFelState
import com.nuvio.tv.ui.components.PlayerPanelRow
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.v2.components.NuvioSideSheet
import com.nuvio.tv.ui.v2.appearance.LocalV2Appearance
import kotlinx.coroutines.delay

/** The "FEL" lettering of the FEL control button (block letters on a 24 dp grid). */
internal val FelIcon: ImageVector by lazy {
    ImageVector.Builder("Fel", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black)) {
        fun rect(l: Float, t: Float, r: Float, b: Float) { moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close() }
        // F
        rect(1f, 6f, 3f, 18f); rect(3f, 6f, 7f, 8f); rect(3f, 11f, 6f, 13f)
        // E
        rect(8.5f, 6f, 10.5f, 18f); rect(10.5f, 6f, 15f, 8f); rect(10.5f, 11f, 14f, 13f); rect(10.5f, 16f, 15f, 18f)
        // L
        rect(17f, 6f, 19f, 18f); rect(19f, 16f, 23f, 18f)
    }.build()
}

/** The FEL panel: the enhancement layer switch (the FEL stats are in the stats overlay, under DV). */
@Composable
internal fun FelPanel(viewModel: PlayerViewModel, onSetEl: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var elOn by remember { mutableStateOf(GazelleFelState.elApplied && GazelleFelState.elRequested) }
    // the state being switched to, and when; shown until the player applies it (or 5 s, e.g. no root)
    var target by remember { mutableStateOf<Boolean?>(null) }
    var targetAtMs by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val applied = GazelleFelState.elApplied && GazelleFelState.elRequested
            val pending = target
            if (pending == null || applied == pending ||
                android.os.SystemClock.elapsedRealtime() - targetAtMs > 5_000L
            ) {
                target = null
                elOn = applied
            }
            delay(250L)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val content: @Composable () -> Unit = {
        Text(stringResource(R.string.player_fel_title), style = MaterialTheme.typography.headlineSmall,
            color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
        PlayerPanelRow(
            title = stringResource(R.string.player_fel_el),
            subtitle = stringResource(when {
                !GazelleFelState.elSwitchable -> R.string.player_fel_el_unavailable
                !elOn && GazelleFelControl.lastError != null -> R.string.player_fel_el_no_root
                elOn -> R.string.player_fel_el_on
                else -> R.string.player_fel_el_off
            }),
            selected = elOn,
            titleEnd = if (elOn) "ON" else "OFF",
            onClick = {
                if (GazelleFelState.elSwitchable) {
                    elOn = !elOn
                    target = elOn
                    targetAtMs = android.os.SystemClock.elapsedRealtime()
                    onSetEl(elOn)
                }
            },
            modifier = Modifier.focusRequester(focus)
        )
    }
    if (LocalV2Appearance.current != null) {
        NuvioSideSheet(onDismiss, width = 520.dp, requestInitialFocus = false) { content() }
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.width(520.dp).heightIn(max = 560.dp)
                .clip(RoundedCornerShape(NuvioTheme.radii.xxl))
                .background(Color.Black.copy(alpha = 0.85f))
        ) {
            Column(Modifier.padding(NuvioTheme.spacing.xl)) { content() }
        }
    }
}
