package dev.stepcron.mobile.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.stepcron.mobile.R
import dev.stepcron.mobile.ui.history.HistoryScreen
import dev.stepcron.mobile.ui.settings.SettingsActions
import dev.stepcron.mobile.ui.settings.SettingsScreen
import dev.stepcron.mobile.ui.theme.DeskBreakPhoneTheme
import dev.stepcron.mobile.ui.today.TodayActions
import dev.stepcron.mobile.ui.today.TodayScreen
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Proxy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Grafiken für den Eintrag im Play Store – wie [ScreenshotTest] nur mit `-Pscreenshots`:
 *
 *     ./gradlew :mobile:testDebugUnitTest -Pscreenshots --tests '*StoreAssetsTest*'
 *
 * Schreibt nach `mobile/build/screenshots/store/`:
 * - `icon-512.png`: App-Symbol 512 × 512 aus den Ebenen des Launcher-Symbols (volle Fläche, ohne Transparenz –
 *   Play rundet die Ecken selbst ab).
 * - `feature-graphic-de.png`, `feature-graphic-en.png`: Vorstellungsgrafik 1024 × 500.
 * - `phone-<sprache>-<nr>-<seite>.png`: Handy-Screenshots 1080 × 1920 (9:16) mit Titel- und Navigationsleiste
 *   wie in der App, Beispieldaten aus [SampleData].
 *
 * Alle Bilder ohne Alphakanal (Play verlangt für Vorstellungsgrafik und Screenshots 24-Bit-PNG oder JPEG). Die
 * fertigen Bilder liegen eingecheckt unter `docs/play-store/assets/`; nach Änderungen an der Oberfläche neu
 * erzeugen und dorthin kopieren (siehe docs/play-store/README.md).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w432dp-h768dp-400dpi")
class StoreAssetsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // --- App-Symbol ---

    /**
     * Hintergrund- und Vordergrundebene des adaptiven Symbols (mipmap-anydpi/ic_launcher.xml), so gezeichnet wie
     * ein Launcher sie zeigt: Von der 108-dp-Ebene ist ein mittlerer Ausschnitt sichtbar. Der Ausschnitt ist hier
     * etwas größer als die üblichen 72 dp, damit die Figur in der von Play abgerundeten Fläche Luft hat.
     */
    @Test
    fun appIcon() {
        val context = compose.activity
        val size = ICON_SIZE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(context.getColor(R.color.ic_launcher_background))
        val foreground = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground))
        val layer = (size * ADAPTIVE_LAYER_DP / ICON_VISIBLE_DP).toInt()
        val offset = (size - layer) / 2
        foreground.setBounds(offset, offset, offset + layer, offset + layer)
        foreground.draw(canvas)
        save(bitmap, "icon-512")
    }

    // --- Vorstellungsgrafik (1024 × 500 px = 512 × 250 dp bei xhdpi) ---

    @Test
    @Config(qualifiers = "de-w512dp-h250dp-xhdpi")
    fun featureGraphicGerman() = shot("feature-graphic-de") { FeatureGraphic() }

    @Test
    @Config(qualifiers = "en-w512dp-h250dp-xhdpi")
    fun featureGraphicEnglish() = shot("feature-graphic-en") { FeatureGraphic() }

    // --- Handy-Screenshots: 1080 × 1920 px = 432 × 768 dp bei 400 dpi (Dichte 2,5) ---
    // So breit wie ein übliches Handy (Pixel: 411–448 dp); bei 360 dp wären die Bilder grob und manche deutschen
    // Überschriften (z. B. „Erinnerungen") würden mitten im Wort umbrechen.

    @Test
    @Config(qualifiers = "de-w432dp-h768dp-400dpi")
    fun phoneGerman() = phoneSet("de")

    @Test
    @Config(qualifiers = "en-w432dp-h768dp-400dpi")
    fun phoneEnglish() = phoneSet("en")

    /**
     * Die Bilder einer Sprache, in der Reihenfolge für den Store. [SampleData] liefert immer denselben Stand
     * (Dienstag, 10:22 Uhr). Je Test geht nur ein `setContent` – die Seiten wechseln deshalb über einen Zustand;
     * `key` sorgt dafür, dass jede Seite frisch (ungescrollt) beginnt.
     */
    private fun phoneSet(language: String) {
        val shots = listOf(
            PhoneShot("phone-$language-1-today", StorePage.TODAY) {
                TodayScreen(state = SampleData.state(), actions = TodayActions())
            },
            PhoneShot("phone-$language-2-history", StorePage.HISTORY) { HistoryScreen(state = SampleData.state()) },
            PhoneShot("phone-$language-3-history-weekdays", StorePage.HISTORY, scrollToKey = "weekdays") {
                HistoryScreen(state = SampleData.state())
            },
            PhoneShot("phone-$language-4-history-hours", StorePage.HISTORY, scrollToKey = "hours") {
                HistoryScreen(state = SampleData.state())
            },
            PhoneShot("phone-$language-5-settings", StorePage.SETTINGS) {
                SettingsScreen(state = SampleData.state(), actions = NoSettingsActions)
            },
            PhoneShot("phone-$language-6-settings-schedule", StorePage.SETTINGS, scrollToKey = "schedule") {
                SettingsScreen(state = SampleData.state(), actions = NoSettingsActions)
            },
            PhoneShot("phone-$language-7-settings-data", StorePage.SETTINGS, scrollToKey = "steps") {
                SettingsScreen(state = SampleData.state(), actions = NoSettingsActions)
            },
        )
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(SampleData.zone))
        var current by mutableStateOf(shots.first())
        compose.setContent {
            DeskBreakPhoneTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    key(current.name) { PhoneFrame(current.page, current.content) }
                }
            }
        }
        shots.forEach { shot ->
            current = shot
            compose.waitForIdle()
            if (shot.scrollToKey != null) {
                // Der Eintrag mit diesem Schlüssel steht danach oben in der Liste.
                compose.onNode(hasScrollToKeyAction()).performScrollToKey(shot.scrollToKey)
                compose.waitForIdle()
            }
            capture(shot.name)
        }
    }

    private class PhoneShot(
        val name: String,
        val page: StorePage,
        val scrollToKey: String? = null,
        val content: @Composable () -> Unit,
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { content() }
        compose.waitForIdle()
        capture(name)
    }

    private fun capture(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        save(bitmap, name)
    }

    /** Speichert als PNG ohne Alphakanal (24 Bit), wie Play es für Vorstellungsgrafik und Screenshots verlangt. */
    private fun save(bitmap: Bitmap, name: String) {
        bitmap.setHasAlpha(false)
        val base = System.getProperty("screenshots.dir") ?: "build/screenshots"
        val dir = File(base, "store").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val ICON_SIZE_PX = 512
        const val ADAPTIVE_LAYER_DP = 108f

        /** Sichtbarer Ausschnitt der Vordergrundebene in dp (Launcher: 72 dp). */
        const val ICON_VISIBLE_DP = 80f
    }
}

/** Die drei Seiten der Navigationsleiste, wie in `DeskBreakPhoneRoot`. */
private enum class StorePage(
    @param:StringRes val title: Int,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    TODAY(R.string.title_today, R.string.nav_today, R.drawable.ic_today),
    HISTORY(R.string.title_history, R.string.nav_history, R.drawable.ic_bar_chart),
    SETTINGS(R.string.title_settings, R.string.nav_settings, R.drawable.ic_tune),
}

/**
 * Titelleiste und Navigationsleiste wie in `DeskBreakPhoneRoot` (das selbst ein ViewModel braucht und deshalb
 * hier nicht direkt nutzbar ist).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneFrame(page: StorePage, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(page.title)) },
                actions = {
                    IconButton(onClick = {}) { Icon(painterResource(R.drawable.ic_sync), contentDescription = null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                StorePage.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == page,
                        onClick = {},
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) { content() }
    }
}

/**
 * Leere [SettingsActions] als Proxy: Für Bilder reicht der Zustand, und anders als ein ausgeschriebenes Objekt
 * übersteht der Proxy neue Methoden im Interface.
 */
private val NoSettingsActions: SettingsActions = Proxy.newProxyInstance(
    SettingsActions::class.java.classLoader,
    arrayOf(SettingsActions::class.java),
) { proxy, method, args ->
    when {
        method.name == "equals" -> proxy === args?.firstOrNull()
        method.name == "hashCode" -> System.identityHashCode(proxy)
        method.returnType == String::class.java -> "deskbreak"
        method.returnType == java.lang.Boolean.TYPE -> false
        method.returnType == java.lang.Integer.TYPE -> 0
        else -> null
    }
} as SettingsActions

// --- Vorstellungsgrafik ---

/** Texte der Vorstellungsgrafik – bewusst nicht in den App-Ressourcen: Sie gehören zum Store-Eintrag, nicht zur App. */
private data class StoreFeatureTexts(val tagline: String, val features: List<String>, val watchCaption: String)

private val featureTexts = mapOf(
    "de" to StoreFeatureTexts(
        tagline = "Bewegungspausen für deine Wear-OS-Uhr",
        features = listOf("Wochenplan", "Schritterkennung", "Kachel & Komplikation"),
        watchCaption = "Nächste Pause",
    ),
    "en" to StoreFeatureTexts(
        // Geschütztes Leerzeichen: „Wear OS" nicht über zwei Zeilen trennen.
        tagline = "Movement breaks for your Wear\u00A0OS watch",
        features = listOf("Weekly schedule", "Step detection", "Tile & complication"),
        watchCaption = "Next break",
    ),
)

/** Markenfarben wie in ui/theme/Theme.kt und dem Launcher-Symbol. */
private val BrandGreen = Color(0xFF1B6D35)
private val BrandDeep = Color(0xFF0E3B20)
private val IconBackground = Color(0xFF14532D)
private val IconForeground = Color(0xFFB9F6CA)
private val RingGreen = Color(0xFF88D997)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeatureGraphic() {
    val texts = featureTexts[LocalConfiguration.current.locales[0].language] ?: featureTexts.getValue("en")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(BrandGreen, BrandDeep), Offset.Zero, Offset.Infinite)),
    ) {
        // Dezente Ringe hinter der Uhr – Anklang an den Fortschrittsring der App.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.80f, size.height * 0.5f)
            listOf(0.62f, 0.86f, 1.10f).forEach { factor ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = size.height * factor * 0.5f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp, end = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconTile()
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "DeskBreak",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = texts.tagline,
                    color = IconForeground,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    texts.features.forEach { FeaturePill(it) }
                }
            }
            Spacer(Modifier.width(16.dp))
            WatchIllustration(caption = texts.watchCaption)
        }
    }
}

/** Das App-Symbol als abgerundete Kachel (gleiche Figur wie im Launcher-Symbol). */
@Composable
private fun AppIconTile() {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(IconBackground, RoundedCornerShape(30))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(30)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_walk),
            contentDescription = null,
            tint = IconForeground,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun FeaturePill(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Stilisierte runde Uhr mit Fortschrittsring und Countdown bis zur nächsten Pause (wie auf der Uhr). */
@Composable
private fun WatchIllustration(caption: String) {
    Box(modifier = Modifier.size(156.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            // Krone rechts
            drawRoundRect(
                color = Color(0xFF3A403A),
                topLeft = Offset(size.width - 6.dp.toPx(), size.height / 2 - 14.dp.toPx()),
                size = Size(8.dp.toPx(), 28.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            drawCircle(color = Color(0xFF2E342E), radius = r - 2.dp.toPx())
            drawCircle(color = Color.Black, radius = r - 9.dp.toPx())
            val ringWidth = 7.dp.toPx()
            val inset = 18.dp.toPx()
            val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            drawArc(
                color = Color(0xFF1F3324),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = ringWidth),
            )
            drawArc(
                color = RingGreen,
                startAngle = -90f,
                sweepAngle = 360f * 0.49f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = ringWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_walk),
                contentDescription = null,
                tint = RingGreen,
                modifier = Modifier.size(24.dp),
            )
            Text("23 min", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(caption, color = Color(0xFFC1C9BF), fontSize = 11.sp)
        }
    }
}
