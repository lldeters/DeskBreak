package dev.stepcron.wear.surface

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dev.stepcron.wear.MainActivity
import dev.stepcron.wear.R
import dev.stepcron.wear.appContainer
import dev.stepcron.wear.ui.theme.DeskBreakColors
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * Kachel: Countdown bis zur nächsten Bewegungspause bzw. der aktuelle Status, dazu „Jetzt bewegt"
 * (setzt den Timer zurück, ohne die App zu öffnen). Ein Tipp auf den Text öffnet die App.
 */
class DeskBreakTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val container = applicationContext.appContainer
            val didReset = consumeResetClick(requestParams.currentState.lastClickableId)
            if (didReset) container.engine.onManualReset()
            val now = System.currentTimeMillis()
            val model = SurfaceModel.create(
                container.settingsRepository.currentSettings(),
                container.settingsRepository.currentState(),
                now,
                ZoneId.systemDefault(),
            )
            TileLayout(this@DeskBreakTileService, requestParams.deviceConfiguration).tile(model, now, didReset)
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture {
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        }

    internal companion object {
        const val RESOURCES_VERSION = "1"

        /** Klick-IDs für „Jetzt bewegt" beginnen damit; dahinter steht eine pro Kachel-Aufbau eindeutige Zahl. */
        const val RESET_CLICK_PREFIX = "reset:"

        private val lastHandledResetClick = AtomicReference<String?>(null)

        /**
         * `true` genau einmal pro Tipp auf „Jetzt bewegt" – auch falls der Host dieselbe Klick-ID bei einer
         * späteren Aktualisierung noch einmal mitschickt. Sonst würde jede Aktualisierung den Timer erneut
         * zurücksetzen.
         */
        fun consumeResetClick(clickableId: String): Boolean =
            clickableId.startsWith(RESET_CLICK_PREFIX) && lastHandledResetClick.getAndSet(clickableId) != clickableId
    }
}

/** Aufbau der Kachel (Material-Layout von ProtoLayout). */
private class TileLayout(private val context: Context, private val device: DeviceParameters) {

    private val colors = Colors(
        DeskBreakColors.Primary.toArgb(),
        ON_PRIMARY,
        DeskBreakColors.Surface.toArgb(),
        ON_SURFACE,
    )
    private val texts = SurfaceTexts(context)

    fun tile(model: SurfaceModel, nowMillis: Long, didReset: Boolean): TileBuilders.Tile {
        val layout = PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(
                text(context.getString(R.string.tile_label), Typography.TYPOGRAPHY_CAPTION1, colors.primary),
            )
            .setContent(content(model, nowMillis, didReset))
            .setPrimaryChipContent(chip(model, nowMillis))
            .build()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(DeskBreakTileService.RESOURCES_VERSION)
            // Der Countdown steht als Text in der Kachel: höchstens eine Minute alt, solange sie sichtbar ist.
            .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    private fun content(model: SurfaceModel, nowMillis: Long, didReset: Boolean): LayoutElementBuilders.LayoutElement {
        val caption = if (didReset) context.getString(R.string.surface_reset_done) else texts.caption(model)
        return LayoutElementBuilders.Column.Builder()
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openAppClickable()).build())
            .addContent(text(texts.headline(model, nowMillis), Typography.TYPOGRAPHY_DISPLAY3, ON_SURFACE))
            .addContent(text(caption, Typography.TYPOGRAPHY_CAPTION2, ON_SURFACE_VARIANT, maxLines = 2))
            .build()
    }

    private fun chip(model: SurfaceModel, nowMillis: Long): LayoutElementBuilders.LayoutElement {
        val canReset = model != SurfaceModel.Off && model != SurfaceModel.NoActiveDays
        val (label, clickable) = if (canReset) {
            context.getString(R.string.tile_reset) to ModifiersBuilders.Clickable.Builder()
                .setId(DeskBreakTileService.RESET_CLICK_PREFIX + nowMillis)
                .setOnClick(ActionBuilders.LoadAction.Builder().build())
                .build()
        } else {
            context.getString(R.string.tile_open) to openAppClickable()
        }
        return CompactChip.Builder(context, label, clickable, device)
            .setChipColors(ChipColors.primaryChipColors(colors))
            .build()
    }

    private fun openAppClickable(): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId(OPEN_APP_CLICK_ID)
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(context.packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun text(value: String, typography: Int, color: Int, maxLines: Int = 1): LayoutElementBuilders.LayoutElement =
        Text.Builder(context, value)
            .setTypography(typography)
            .setColor(argb(color))
            .setMaxLines(maxLines)
            .build()

    private companion object {
        const val FRESHNESS_MILLIS = 60_000L
        const val OPEN_APP_CLICK_ID = "open"
        const val ON_PRIMARY = 0xFF0B2913.toInt()
        const val ON_SURFACE = 0xFFFFFFFF.toInt()
        const val ON_SURFACE_VARIANT = 0xFFB4B9BF.toInt()
    }
}
