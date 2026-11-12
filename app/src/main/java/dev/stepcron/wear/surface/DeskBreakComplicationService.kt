package dev.stepcron.wear.surface

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.stepcron.wear.R
import dev.stepcron.wear.appContainer
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Komplikation fürs Zifferblatt: Countdown bis zur nächsten Bewegungspause (das Zifferblatt zählt selbst
 * herunter, ohne Aktualisierungen) bzw. der aktuelle Status – ohne gültige Lizenz „Freischalten". Als
 * Fortschrittsring wächst der Wert über das laufende Intervall. Ein Tipp öffnet die App.
 */
class DeskBreakComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val container = applicationContext.appContainer
        val now = System.currentTimeMillis()
        val model = SurfaceModel.create(
            container.settingsRepository.currentSettings(),
            container.settingsRepository.currentState(),
            now,
            ZoneId.systemDefault(),
            container.watchLicense.status(now),
        )
        return ComplicationContent(applicationContext).data(request.complicationType, model, now)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val now = System.currentTimeMillis()
        val preview = SurfaceModel.Countdown(
            reminderAtMillis = now + 23 * 60_000L,
            cycleStartMillis = now - 22 * 60_000L,
            snoozed = false,
            reminderPending = false,
        )
        return ComplicationContent(applicationContext).data(type, preview, now)
    }
}

/** Baut die Komplikationsdaten für einen [SurfaceModel]-Zustand. */
private class ComplicationContent(private val context: Context) {

    private val texts = SurfaceTexts(context)
    private val icon = MonochromaticImage.Builder(Icon.createWithResource(context, R.drawable.ic_walk)).build()
    private val tapAction = context.appContainer.notifier.openAppPendingIntent()

    fun data(type: ComplicationType, model: SurfaceModel, nowMillis: Long): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(shortText(model, nowMillis), description(model))
            .setTitle(plain(context.getString(R.string.complication_title)))
            .setMonochromaticImage(icon)
            .setTapAction(tapAction)
            .build()

        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(longText(model), description(model))
            .setTitle(plain(context.getString(R.string.tile_label)))
            .setMonochromaticImage(icon)
            .setTapAction(tapAction)
            .build()

        ComplicationType.RANGED_VALUE -> rangedValue(model, nowMillis)
        else -> null
    }

    /** Countdown („23m"), sonst der kurze Status („Frei", „Aus" …). */
    private fun shortText(model: SurfaceModel, nowMillis: Long): ComplicationText =
        if (model is SurfaceModel.Countdown) countdown(model, TimeDifferenceStyle.SHORT_SINGLE_UNIT) else plain(texts.headline(model, nowMillis))

    private fun longText(model: SurfaceModel): ComplicationText = when (model) {
        is SurfaceModel.Countdown -> countdown(
            model,
            TimeDifferenceStyle.WORDS_SINGLE_UNIT,
            surroundingText = context.getString(R.string.complication_long_countdown),
        )

        else -> plain(texts.caption(model))
    }

    private fun rangedValue(model: SurfaceModel, nowMillis: Long): ComplicationData {
        val description = description(model)
        val builder = when (model) {
            is SurfaceModel.Countdown -> countdownRange(model, nowMillis, description)
            // Heute geschafft: voller Ring; sonst leer.
            is SurfaceModel.DayDone -> RangedValueComplicationData.Builder(1f, 0f, 1f, description)
            else -> RangedValueComplicationData.Builder(0f, 0f, 1f, description)
        }
        return builder
            .setText(shortText(model, nowMillis))
            .setMonochromaticImage(icon)
            .setTapAction(tapAction)
            .build()
    }

    /**
     * Fortschritt über das laufende Intervall. Ab Wear OS 4 rechnet das Zifferblatt ihn selbst laufend nach
     * (dynamischer Wert); davor gilt der Stand von jetzt bis zur nächsten Aktualisierung.
     */
    private fun countdownRange(
        model: SurfaceModel.Countdown,
        nowMillis: Long,
        description: ComplicationText,
    ): RangedValueComplicationData.Builder {
        val fallback = model.progressAt(nowMillis)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return RangedValueComplicationData.Builder(fallback, 0f, 1f, description)
        }
        val totalSeconds = ((model.reminderAtMillis - model.cycleStartMillis) / 1000f).coerceAtLeast(1f)
        val elapsedSeconds = DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(model.cycleStartMillis))
            .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
            .toIntSeconds()
            .asFloat()
        return RangedValueComplicationData.Builder(
            dynamicValue = elapsedSeconds.div(totalSeconds),
            fallbackValue = fallback,
            min = 0f,
            max = 1f,
            contentDescription = description,
        )
    }

    private fun countdown(
        model: SurfaceModel.Countdown,
        style: TimeDifferenceStyle,
        surroundingText: String? = null,
    ): ComplicationText =
        TimeDifferenceComplicationText.Builder(style, CountDownTimeReference(Instant.ofEpochMilli(model.reminderAtMillis)))
            .setText(surroundingText)
            .setMinimumTimeUnit(TimeUnit.MINUTES)
            .build()

    private fun description(model: SurfaceModel): ComplicationText =
        plain("${context.getString(R.string.complication_label)}: ${texts.caption(model)}")

    private fun plain(text: String): ComplicationText = PlainComplicationText.Builder(text).build()
}
