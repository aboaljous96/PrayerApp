@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.prayerapp
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
// إضافة import جديدة
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
// ================== تعديل: استيراد الخط الصحيح لسطح المكتب ==================
import androidx.compose.ui.text.platform.Font
// ======================================================================
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.awt.FileDialog
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
// ================== حذف import androidx.compose.ui.unit.min ==================
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos

// =========================== تشغيل الأذان (Desktop) ===============================
class AdhanPlayer {
    private var clip: Clip? = null

    fun play(sound: AdhanSound) {
        stop()
        try {
            val clipInstance = AudioSystem.getClip()
            val audioStream = when (sound) {
                is AdhanSound.Resource -> {
                    val resourceStream: InputStream? = javaClass.classLoader.getResourceAsStream(sound.path)
                    if (resourceStream == null) {
                        println("!!! خطأ: لم يتم العثور على ملف الصوت: ${sound.path}")
                        println("!!! تأكد من وضع ملفات wav في مجلد 'src/main/resources'")
                        return
                    }
                    AudioSystem.getAudioInputStream(BufferedInputStream(resourceStream))
                }
                is AdhanSound.FilePath -> {
                    AudioSystem.getAudioInputStream(sound.file)
                }
            }
            audioStream.use { stream ->
                clipInstance.open(stream)
            }
            clipInstance.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) release()
            }
            clipInstance.start()
            clip = clipInstance
        } catch (e: Exception) {
            println("!!! خطأ أثناء تشغيل الصوت: ${e.message}")
            e.printStackTrace()
            release()
        }
    }

    fun stop() {
        clip?.let {
            if (it.isRunning) it.stop()
            it.close()
        }
        clip = null
    }

    fun release() = stop()
}

sealed class AdhanSound {
    data class Resource(val path: String) : AdhanSound()
    data class FilePath(val file: File) : AdhanSound()
}

private val PRAYER_LABEL_KEYS = mapOf(
    "الفجر" to "fajr",
    "الظهر" to "dhuhr",
    "العصر" to "asr",
    "المغرب" to "maghrib",
    "العشاء" to "isha"
)

private val DEFAULT_ADHAN_RESOURCES = mapOf(
    "fajr" to "azan_fajr.wav",
    "dhuhr" to "azan_dhuhr.wav",
    "asr" to "azan_asr.wav",
    "maghrib" to "azan_maghrib.wav",
    "isha" to "azan_isha.wav"
)

private val CUSTOMIZABLE_PRAYER_LABELS = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")

data class AdhanSettings(
    val customFiles: Map<String, String> = emptyMap(),
    val disableFajr: Boolean = false,
    val disableIsha: Boolean = false
)

private fun adhanSoundForLabel(label: String, settings: AdhanSettings): AdhanSound? {
    val key = PRAYER_LABEL_KEYS[label] ?: return null
    val customPath = settings.customFiles[key]?.takeIf { it.isNotBlank() }
    if (customPath != null) {
        val file = File(customPath)
        if (file.exists()) return AdhanSound.FilePath(file)
    }
    val resource = DEFAULT_ADHAN_RESOURCES[key] ?: return null
    return AdhanSound.Resource(resource)
}

private fun isAdhanEnabled(label: String, settings: AdhanSettings): Boolean = when (PRAYER_LABEL_KEYS[label]) {
    "fajr" -> !settings.disableFajr
    "isha" -> !settings.disableIsha
    else -> true
}

// رابط JSON الافتراضي
private const val REMOTE_JSON_URL_DEFAULT =
    "https://drive.google.com/uc?export=download&id=10edNDUuxq9vqIMq2O7ZSW0tPKNxcflTX"

// =========================== الخلفية المتحركة ===============================
enum class BackgroundPattern { Nebula, Waves, Rays, Mosaic, Skyline, Petals }

data class AnimatedPrayerGradient(
    val startColors: List<Color>,
    val endColors: List<Color>,
    val accent: Color,
    val glow: Color,
    val pattern: BackgroundPattern
)

data class AnimatedGradientPalette(
    val brush: Brush,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val glow: Color,
    val pattern: BackgroundPattern
)

object PrayerAnimatedGradients {
    val Isha = AnimatedPrayerGradient(
        startColors = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B)),
        endColors = listOf(Color(0xFF1B263B), Color(0xFF0D1B2A)),
        accent = Color(0xFF5B8DEF),
        glow = Color(0xFF66FFE3),
        pattern = BackgroundPattern.Nebula
    )
    val Fajr = AnimatedPrayerGradient(
        startColors = listOf(Color(0xFF1B263B), Color(0xFF415A77)),
        endColors = listOf(Color(0xFF415A77), Color(0xFF1B263B)),
        accent = Color(0xFF7FD1FF),
        glow = Color(0xFFCAF7FF),
        pattern = BackgroundPattern.Waves
    )
    val Sunrise = AnimatedPrayerGradient(
        startColors = listOf(Color(0xFFF2C94C), Color(0xFFB88A4F)),
        endColors = listOf(Color(0xFFB88A4F), Color(0xFFF2C94C)),
        accent = Color(0xFFFFA64C),
        glow = Color(0xFFFFD9A1),
        pattern = BackgroundPattern.Rays
    )
    val Dhuhr = AnimatedPrayerGradient(
        startColors = listOf(Color(0xFF004D40), Color(0xFF00796B)),
        endColors = listOf(Color(0xFF00796B), Color(0xFF004D40)),
        accent = Color(0xFF2EE6B1),
        glow = Color(0xFF9CFFD9),
        pattern = BackgroundPattern.Mosaic
    )
    val Asr = AnimatedPrayerGradient(
        startColors = listOf(Color(0xFF00796B), Color(0xFFB88A4F)),
        endColors = listOf(Color(0xFFB88A4F), Color(0xFF00796B)),
        accent = Color(0xFFFFB26A),
        glow = Color(0xFFFFF0C7),
        pattern = BackgroundPattern.Skyline
    )
    val Maghrib = AnimatedPrayerGradient(
        startColors = listOf(Color(0xFF4A0072), Color(0xFF6A1B9A)),
        endColors = listOf(Color(0xFF6A1B9A), Color(0xFF4A0072)),
        accent = Color(0xFFCE93D8),
        glow = Color(0xFFE1BEE7),
        pattern = BackgroundPattern.Petals
    )
    val Default = Isha
}

@Composable
fun rememberAnimatedPrayerGradient(nextPrayerLabel: String?): AnimatedGradientPalette {
    val target = when (nextPrayerLabel) {
        "الفجر" -> PrayerAnimatedGradients.Isha
        "الشروق" -> PrayerAnimatedGradients.Fajr
        "الظهر" -> PrayerAnimatedGradients.Sunrise
        "العصر" -> PrayerAnimatedGradients.Dhuhr
        "المغرب" -> PrayerAnimatedGradients.Asr
        "العشاء" -> PrayerAnimatedGradients.Maghrib
        else -> PrayerAnimatedGradients.Default
    }
    val infinite = rememberInfiniteTransition(label = "GradientAnimation")
    val c1 by infinite.animateColor(
        initialValue = target.startColors.first(),
        targetValue = target.startColors.last(),
        animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "c1"
    )
    val c2 by infinite.animateColor(
        initialValue = target.endColors.first(),
        targetValue = target.endColors.last(),
        animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "c2"
    )
    val shimmer by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(12000, easing = LinearEasing)),
        label = "shimmer"
    )
    val accent = remember(c1, c2) { lerp(target.accent, lerp(c1, c2, 0.5f), 0.6f) }
    val glow = remember(c1, c2) { lerp(target.glow, c2, 0.35f) }
    val brush = remember(c1, c2, accent, shimmer) {
        Brush.linearGradient(
            colors = listOf(c1, accent, c2, accent.copy(alpha = 0.9f)),
            start = Offset(shimmer * 1200f, 0f),
            end = Offset(0f, (1f - shimmer) * 1200f),
            tileMode = TileMode.Mirror
        )
    }
    return AnimatedGradientPalette(
        brush = brush,
        primary = c1,
        secondary = c2,
        accent = accent,
        glow = glow,
        pattern = target.pattern
    )
}

@Composable
fun PrayerScreenBackground(
    nextPrayerLabel: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = rememberAnimatedPrayerGradient(nextPrayerLabel)
    val infinite = rememberInfiniteTransition(label = "AuroraMotion")
    val waveShift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(16000, easing = LinearEasing)),
        label = "wave"
    )
    val flicker by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker"
    )
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(brush = palette.brush, size = size)

            val width = size.width
            val height = size.height
            val minDim = size.minDimension

            when (palette.pattern) {
                BackgroundPattern.Nebula -> {
                    repeat(4) { layer ->
                        val shift = (waveShift + layer * 0.22f) % 1f
                        val centerX = width * shift
                        val centerY = height * (0.35f + 0.15f * sin((waveShift + layer * 0.35f) * 2f * PI).toFloat())
                        val radius = minDim * (0.45f + layer * 0.12f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(palette.glow.copy(alpha = 0.38f - layer * 0.06f), Color.Transparent),
                                center = Offset(centerX, centerY),
                                radius = radius
                            ),
                            center = Offset(centerX, centerY),
                            radius = radius,
                            blendMode = BlendMode.Screen
                        )
                    }
                    repeat(90) { index ->
                        val progress = (index / 90f + waveShift * 0.5f) % 1f
                        val sparkleX = width * progress
                        val sparkleY = (height * (index % 18 / 18f) + height * waveShift * 0.4f) % height
                        val twinkle = 0.25f + 0.75f * abs(sin((progress + flicker) * 2f * PI)).toFloat()
                        drawCircle(
                            color = Color.White.copy(alpha = 0.12f * twinkle),
                            radius = minDim * 0.0045f * (1f + twinkle),
                            center = Offset(sparkleX, sparkleY),
                            blendMode = BlendMode.Screen
                        )
                    }
                }

                BackgroundPattern.Waves -> {
                    val layers = 3
                    val segments = 80
                    repeat(layers) { layer ->
                        val amplitude = height * (0.04f + layer * 0.02f)
                        val baseline = height * (0.55f + layer * 0.08f)
                        val path = Path().apply {
                            moveTo(0f, height)
                            var x = 0f
                            val step = width / segments
                            while (x <= width + step) {
                                val progress = x / width
                                val angle = (progress * 2f * PI + waveShift * 2f * PI + layer * PI / 3)
                                val y = baseline + sin(angle).toFloat() * amplitude
                                lineTo(x, y)
                                x += step
                            }
                            lineTo(width, height)
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    palette.accent.copy(alpha = 0.24f - layer * 0.05f),
                                    palette.primary.copy(alpha = 0.16f - layer * 0.04f)
                                ),
                                startY = baseline - amplitude,
                                endY = height
                            )
                        )
                    }
                }

                BackgroundPattern.Rays -> {
                    val rayCount = 24
                    val origin = Offset(width * 0.5f, height * 0.18f)
                    repeat(rayCount) { index ->
                        val angle = (index / rayCount.toFloat() + waveShift) * 2f * PI
                        val length = minDim * (1.2f + 0.15f * abs(sin(angle + flicker * PI)).toFloat())
                        val end = Offset(
                            origin.x + cos(angle).toFloat() * length,
                            origin.y + sin(angle).toFloat() * length
                        )
                        val alpha = 0.08f + 0.28f * abs(sin(angle * 0.5f + flicker * PI)).toFloat()
                        drawLine(
                            color = palette.glow.copy(alpha = alpha),
                            start = origin,
                            end = end,
                            strokeWidth = width * 0.012f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                BackgroundPattern.Mosaic -> {
                    val columns = 8
                    val rows = 6
                    val cellWidth = width / columns
                    val cellHeight = height / rows
                    for (c in 0 until columns) {
                        for (r in 0 until rows) {
                            val progress = ((waveShift + c * 0.08f + r * 0.12f) % 1f)
                            val intensity = 0.12f + 0.25f * abs(sin((progress + flicker) * 2f * PI)).toFloat()
                            drawRoundRect(
                                color = lerp(palette.primary, palette.accent, progress),
                                topLeft = Offset(c * cellWidth - cellWidth * 0.1f, r * cellHeight),
                                size = Size(cellWidth * 1.2f, cellHeight * 1.05f),
                                cornerRadius = CornerRadius(cellWidth * 0.35f),
                                alpha = intensity
                            )
                        }
                    }
                }

                BackgroundPattern.Skyline -> {
                    val barCount = 18
                    val barWidth = width / (barCount * 1.4f)
                    repeat(barCount) { index ->
                        val progress = (index / barCount.toFloat() + waveShift) % 1f
                        val heightFactor = 0.35f + 0.45f * abs(sin((progress + flicker) * 2f * PI)).toFloat()
                        val barHeight = height * (0.25f + heightFactor)
                        val x = index * barWidth * 1.4f
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(palette.accent.copy(alpha = 0.4f), Color.Transparent),
                                startY = height - barHeight,
                                endY = height
                            ),
                            topLeft = Offset(x, height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth * 0.45f),
                            alpha = 0.4f
                        )
                    }
                }

                BackgroundPattern.Petals -> {
                    val petals = 9
                    val center = Offset(width * 0.5f, height * 0.7f)
                    repeat(petals) { index ->
                        val angle = (index / petals.toFloat()) * 2f * PI + waveShift * 2f * PI
                        val nextAngle = angle + PI / petals
                        val radius = minDim * (0.35f + 0.1f * abs(sin(angle + flicker * PI)).toFloat())
                        val controlRadius = radius * 0.6f
                        val path = Path().apply {
                            moveTo(center.x, center.y)
                            val end = Offset(
                                center.x + cos(angle).toFloat() * radius,
                                center.y + sin(angle).toFloat() * radius
                            )
                            val control1 = Offset(
                                center.x + cos(angle - PI / 6).toFloat() * controlRadius,
                                center.y + sin(angle - PI / 6).toFloat() * controlRadius
                            )
                            val control2 = Offset(
                                center.x + cos(nextAngle + PI / 6).toFloat() * controlRadius,
                                center.y + sin(nextAngle + PI / 6).toFloat() * controlRadius
                            )
                            val end2 = Offset(
                                center.x + cos(nextAngle).toFloat() * radius,
                                center.y + sin(nextAngle).toFloat() * radius
                            )
                            cubicTo(control1.x, control1.y, control2.x, control2.y, end2.x, end2.y)
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.radialGradient(
                                colors = listOf(palette.accent.copy(alpha = 0.3f), Color.Transparent),
                                center = center,
                                radius = radius
                            ),
                            blendMode = BlendMode.Screen,
                            alpha = 0.4f
                        )
                    }
                }
            }

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.28f + 0.12f * flicker),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.4f)
                    ),
                    startY = 0f,
                    endY = height
                ),
                size = size
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(palette.glow.copy(alpha = 0.16f + 0.12f * flicker), Color.Transparent),
                    center = Offset(width * 0.25f, height * 0.2f),
                    radius = minDim
                ),
                size = size,
                blendMode = BlendMode.Plus
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(palette.glow.copy(alpha = 0.1f), Color.Transparent),
                    center = Offset(width * 0.8f, height * 0.85f),
                    radius = minDim * 1.2f
                ),
                size = size,
                blendMode = BlendMode.Plus
            )
        }
        content()
    }
}

// =========================== الثيم ===============================
private val AppShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp)
)

// ================== تعديل: تعريف الخط الفخم ==================
// !!! هام: لتطبيق هذا الخط، يجب تحميل الخطوط ووضعها في المسار 'src/main/resources/font/'
// يمكنك تحميل خط Amiri من ( https://fonts.google.com/specimen/Amiri )
private val elegantArabicFontFamily = try {
    FontFamily(
        // ================== تعديل: استخدام دالة Font الصحيحة لسطح المكتب ==================
        Font("font/Amiri-Regular.ttf", FontWeight.Normal, FontStyle.Normal),
        Font("font/Amiri-Bold.ttf", FontWeight.Bold, FontStyle.Normal)
        // ======================================================================
    )
} catch (e: Exception) {
    println("!!! خطأ فادح: لم يتم العثور على ملفات الخط في 'src/main/resources/font/'. سيتم استخدام الخط الافتراضي.")
    FontFamily.Default // الرجوع للخط الافتراضي إذا لم يجد الملفات
}


// تعريف الـ Typography باستخدام الخط الجديد
// سيتم تطبيق هذا الخط على كل النصوص التي لا تقوم بتعريف fontFamily خاص بها
private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.Normal),
    displayMedium = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.Normal),
    displaySmall = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.Normal),
    headlineLarge = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = elegantArabicFontFamily, fontWeight = FontWeight.Medium)
)
// ======================================================

@Composable
fun PrayerTheme(useDarkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val lightScheme = lightColorScheme(
        primary = Color(0xFF00CBB5), onPrimary = Color(0xFF00211A),
        primaryContainer = Color(0xFF7CFFEA), onPrimaryContainer = Color(0xFF002923),
        secondary = Color(0xFFFF8BA7), onSecondary = Color(0xFF3D001D),
        background = Color(0xFFF5FBFF), onBackground = Color(0xFF061021),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF061021),
        surfaceVariant = Color(0xFFE0F3FF), onSurfaceVariant = Color(0xFF2D5165), outline = Color(0xFF78A3BD)
    )
    val darkScheme = darkColorScheme(
        primary = Color(0xFF00E1D4), onPrimary = Color(0xFF003732),
        primaryContainer = Color(0xFF00524B), onPrimaryContainer = Color(0xFF8CFCEB), // هذا اللون سيُستخدم
        secondary = Color(0xFFFF77A9), onSecondary = Color(0xFF360019),
        background = Color(0xFF040910), onBackground = Color(0xFFE4F7FF),
        surface = Color(0xFF07111E), onSurface = Color(0xFFE4F7FF),
        surfaceVariant = Color(0xFF13273A), onSurfaceVariant = Color(0xFF8FBBD1), outline = Color(0xFF3E5C6E)
    )
    MaterialTheme(
        colorScheme = if (useDarkTheme) darkScheme else lightScheme,
        typography = AppTypography, // ================== تطبيق الخط الجديد على الثيم ==================
        shapes = AppShapes,
        content = content
    )
}

// =========================== الواجهة + المنطق ===============================
@Composable
fun PrayerTimesApp() {
    val adhanPlayer = remember { AdhanPlayer() }
    DisposableEffect(Unit) { onDispose { adhanPlayer.release() } }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var dataset by remember { mutableStateOf<PrayerDataset?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    var currentRemoteUrl by remember { mutableStateOf(REMOTE_JSON_URL_DEFAULT) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInputText by remember { mutableStateOf("") }

    // ================== إضافة حالة جديدة للمعاينة ==================
    var previewPrayerLabel by remember { mutableStateOf<String?>(null) }
    var adhanSettings by remember { mutableStateOf(AdhanSettings()) }
    val scope = rememberCoroutineScope()
    val persistSettings: (AdhanSettings) -> Unit = { newSettings ->
        adhanSettings = newSettings
        scope.launch(Dispatchers.IO) { saveAdhanSettings(newSettings) }
    }
    // ========================================================

    val dateRange by remember(dataset) { derivedStateOf { dataset.getDateRangeStrings() } }
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1_000) }
    }

    val currentDateKey = remember(nowMillis) { dateKeyForMillis(nowMillis) }
    val dayAndDate = remember(nowMillis) { "${dayNameForMillis(nowMillis)} ${displayDateForMillis(nowMillis)}" }
    val todaysTimes = remember(dataset, currentDateKey) { dataset?.days?.get(currentDateKey) }
    val nextPrayerInfo = remember(dataset, nowMillis) { dataset?.findNextPrayer(nowMillis) }
    val noUpcomingPrayers = nextPrayerInfo == null

    // ================== تحديد الستايل الفعّال ==================
    // إذا كان وضع المعاينة شغالاً، استخدمه. وإلا، استخدم وقت الصلاة الفعلي.
    val effectiveNextPrayerLabel = previewPrayerLabel ?: nextPrayerInfo?.label
    // ========================================================

    var lastSeenNext by remember { mutableStateOf<NextPrayerInfo?>(null) }
    var lastPlayedSignature by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(nextPrayerInfo, currentDateKey, adhanSettings) {
        val prev = lastSeenNext
        if (prev != null && (nextPrayerInfo == null || prev.label != nextPrayerInfo.label || prev.time != nextPrayerInfo.time)) {
            val signature = "$currentDateKey|${prev.label}|${prev.time}"
            if (signature != lastPlayedSignature) {
                if (isAdhanEnabled(prev.label, adhanSettings)) {
                    adhanSoundForLabel(prev.label, adhanSettings)?.let { adhanPlayer.play(it) }
                }
                lastPlayedSignature = signature
            }
        }
        lastSeenNext = nextPrayerInfo
    }

    LaunchedEffect(Unit) {
        adhanSettings = loadAdhanSettings()
        currentRemoteUrl = loadCustomUrl()
        val loaded = loadPrayerDataset()
        if (loaded != null) {
            val trimmed = loaded.trimBefore(currentDateKey)
            dataset = trimmed
            if (trimmed != null && trimmed != loaded) savePrayerDataset(trimmed)
        } else dataset = null
    }

    LaunchedEffect(dataset, currentRemoteUrl) {
        while (true) {
            val now = System.currentTimeMillis()
            val currentKey = dateKeyForMillis(now)
            val lastDateKey = dataset.getDateRangeStrings()?.second

            val nextCheckDelay: Long = if (lastDateKey != null) {
                val lastMillis = DATE_KEY_FORMAT.parse(lastDateKey)?.time ?: 0L
                val diffDays = (lastMillis - now) / (1000 * 60 * 60 * 24)
                if (diffDays <= 7) {
                    val res = fetchAndApplyRemote(currentKey, currentRemoteUrl)
                    if (res.updated) dataset = loadPrayerDataset()
                    24 * 60 * 60 * 1000L
                } else {
                    6 * 60 * 60 * 1000L
                }
            } else {
                val res = fetchAndApplyRemote(currentKey, currentRemoteUrl)
                if (res.updated) dataset = loadPrayerDataset()
                24 * 60 * 60 * 1000L
            }

            delay(nextCheckDelay)
        }
    }

    val onManualRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            statusMessage = null
            scope.launch {
                val res = fetchAndApplyRemote(currentDateKey, currentRemoteUrl)
                if (res.updated) {
                    val newDataset = loadPrayerDataset()
                    dataset = newDataset
                    val newRange = newDataset.getDateRangeStrings()
                    statusMessage = newRange?.let { "تم التحديث بنجاح: ${it.first} إلى ${it.second}" }
                        ?: "تم التحديث، ولكن الملف فارغ."
                } else if (res.error != null) {
                    statusMessage = res.error
                } else if (!res.hadFresh) {
                    statusMessage = "الملف صالح، لكن لا توجد مواقيت مستقبلية (ابتداءً من اليوم)."
                } else {
                    statusMessage = "لم يتم التحديث."
                }
                isRefreshing = false
            }
        }
    }

    val onConfirmUrl: () -> Unit = {
        val convertedUrl = convertGoogleDriveLink(urlInputText)
        if (convertedUrl != null) {
            saveCustomUrl(convertedUrl)
            currentRemoteUrl = convertedUrl
            statusMessage = "تم حفظ الرابط بنجاح. يمكنك الآن الضغط على تحديث يدوي."
            urlInputText = ""
            showUrlDialog = false
        } else statusMessage = "الابط غير صالح. يرجى لصق رابط Google Drive صحيح."
    }

    // ================== تعريف دالة الضغط للمعاينة ==================
    val onPrayerCellClick: (String) -> Unit = { label ->
        scope.launch {
            // 1. أوقف أي أذان حالي
            adhanPlayer.stop()
            // 2. شغّل الأذان الخاص بالصلاة المضغوطة
            adhanSoundForLabel(label, adhanSettings)?.let { adhanPlayer.play(it) }
            // 3. اضبط حالة المعاينة (لتغيير الخلفية)
            previewPrayerLabel = label
            // 4. انتظر 5 ثوانٍ
            delay(5000)
            // 5. أعد الحالة لوضعها الطبيعي
            previewPrayerLabel = null
            // 6. أوقف الأذان (الذي استمر 5 ثوانٍ)
            adhanPlayer.stop()
        }
    }
    // ==========================================================

    // After
    val onSelectCustomAdhan: (String) -> Unit = selectAdhan@{ label ->
        val key = PRAYER_LABEL_KEYS[label] ?: return@selectAdhan
        when (val selection = showWavFileDialog("اختر ملف الأذان لصلاة $label")) {
            WavSelectionResult.Cancelled -> Unit
            WavSelectionResult.InvalidFormat -> statusMessage = "يجب اختيار ملف بصيغة WAV."
            is WavSelectionResult.Selected -> {
                scope.launch {
                    val copied = withContext(Dispatchers.IO) { storeCustomAdhanFile(key, selection.file) }
                    if (copied != null) {
                        persistSettings(adhanSettings.copy(customFiles = adhanSettings.customFiles + (key to copied.absolutePath)))
                        statusMessage = "تم حفظ أذان $label بنجاح."
                    } else {
                        statusMessage = "تعذّر حفظ ملف الأذان المختار."
                    }
                }
            }
        }
    }

    val onToggleFajrAdhan: () -> Unit = {
        val newSettings = adhanSettings.copy(disableFajr = !adhanSettings.disableFajr)
        persistSettings(newSettings)
        if (newSettings.disableFajr) adhanPlayer.stop()
        statusMessage = if (newSettings.disableFajr) "تم إيقاف أذان الفجر." else "تم تفعيل أذان الفجر."
    }

    val onToggleIshaAdhan: () -> Unit = {
        val newSettings = adhanSettings.copy(disableIsha = !adhanSettings.disableIsha)
        persistSettings(newSettings)
        if (newSettings.disableIsha) adhanPlayer.stop()
        statusMessage = if (newSettings.disableIsha) "تم إيقاف أذان العشاء." else "تم تفعيل أذان العشاء."
    }

    val fajrAdhanEnabled = !adhanSettings.disableFajr
    val ishaAdhanEnabled = !adhanSettings.disableIsha

    PrayerTimesScreen(
        statusMessage = statusMessage,
        onStatusDismiss = { statusMessage = null },
        dayAndDate = dayAndDate,
        nowMillis = nowMillis,
        nextPrayerInfo = nextPrayerInfo,
        todaysTimes = todaysTimes,
        waitingMode = noUpcomingPrayers,
        onRefreshClicked = onManualRefresh,
        isRefreshing = isRefreshing,
        dateRange = dateRange,
        showUrlDialog = showUrlDialog,
        onShowUrlDialog = { urlInputText = ""; showUrlDialog = true },
        onDismissUrlDialog = { showUrlDialog = false },
        urlInputText = urlInputText,
        onUrlInputChanged = { urlInputText = it },
        onConfirmUrl = onConfirmUrl,
        // ================== تمرير المتغيرات الجديدة ==================
        effectiveNextPrayerLabel = effectiveNextPrayerLabel,
        onPrayerCellClick = onPrayerCellClick,
        onSelectCustomAdhan = onSelectCustomAdhan,
        onToggleFajrAdhan = onToggleFajrAdhan,
        onToggleIshaAdhan = onToggleIshaAdhan,
        fajrAdhanEnabled = fajrAdhanEnabled,
        ishaAdhanEnabled = ishaAdhanEnabled
        // ========================================================
    )
}

// =========================== عناصر UI ===============================
@Composable
fun FloatingStatusMessage(message: String, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var visible by remember(message) { mutableStateOf(true) }
    LaunchedEffect(message) {
        visible = true; delay(6000); visible = false; onDismiss()
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(18.dp)
        Surface(
            modifier = Modifier,
            shape = shape,
            color = cs.surface.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            tonalElevation = 8.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                cs.primary.copy(alpha = 0.28f),
                                cs.primary.copy(alpha = 0.12f)
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth(),
                    // الخط سيتم تطبيقه من الثيم
                    style = typography.bodyLarge.copy(
                        shadow = Shadow(
                            color = cs.primary.copy(alpha = 0.35f),
                            blurRadius = 18f
                        )
                    ),
                    textAlign = TextAlign.Center,
                    color = cs.onSurface
                )
            }
        }
    }
}

@Composable
fun TimeDateHeader(dayAndDate: String) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(36.dp)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = cs.surface,
            contentColor = cs.onSurface
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            cs.primary.copy(alpha = 0.38f),
                            cs.primary.copy(alpha = 0.16f)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            val dynamicFontSize = (maxWidth.value / 11f).sp

            Text(
                text = dayAndDate,
                // الخط سيتم تطبيقه من الثيم
                style = typography.titleLarge.copy(
                    fontSize = dynamicFontSize,
                    fontWeight = FontWeight.Bold, // يمكن إبقاؤه لتمييزه
                    shadow = Shadow(
                        color = cs.primary.copy(alpha = 0.45f),
                        blurRadius = 26f
                    )
                ),
                color = cs.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}


// ================================================================
// ============== (TopRightMenu) =============
// ================================================================
@Composable
fun TopRightMenu(
    modifier: Modifier = Modifier,
    onRefreshClicked: () -> Unit,
    isRefreshing: Boolean,
    dateRange: Pair<String, String>?,
    onUpdateUrlClick: () -> Unit,
    onSelectCustomAdhan: (String) -> Unit,
    onToggleFajrAdhan: () -> Unit,
    onToggleIshaAdhan: () -> Unit,
    fajrAdhanEnabled: Boolean,
    ishaAdhanEnabled: Boolean
) {
    val cs = MaterialTheme.colorScheme
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = cs.surface.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "القائمة",
                    tint = Color.White
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            val rangeText = dateRange?.let { "مخزّن: ${it.first}\nإلى: ${it.second}" }
                ?: "لا توجد مواقيت مخزنة"

            DropdownMenuItem(
                text = {
                    Text(
                        text = rangeText,
                        style = MaterialTheme.typography.bodyMedium, // سيستخدم الخط الجديد
                        lineHeight = 18.sp,
                        color = cs.onSurface
                    )
                },
                onClick = {},
                enabled = false
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text(if (isRefreshing) "جارٍ التحديث..." else "تحديث يدوي") }, // سيستخدم الخط الجديد
                onClick = {
                    if (!isRefreshing) {
                        onRefreshClicked()
                        menuExpanded = false
                    }
                },
                enabled = !isRefreshing,
                leadingIcon = {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = cs.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            DropdownMenuItem(
                text = { Text("تغيير رابط المصدر") }, // سيستخدم الخط الجديد
                onClick = {
                    onUpdateUrlClick()
                    menuExpanded = false
                },
                enabled = !isRefreshing,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = "تغيير الرابط",
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "تخصيص الأذان",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant
            )

            CUSTOMIZABLE_PRAYER_LABELS.forEach { label ->
                DropdownMenuItem(
                    text = { Text("اختيار أذان $label") },
                    onClick = {
                        menuExpanded = false
                        onSelectCustomAdhan(label)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text("تفعيل أذان الفجر") },
                onClick = { onToggleFajrAdhan() },
                leadingIcon = {
                    Icon(
                        imageVector = if (fajrAdhanEnabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    Text(
                        text = if (fajrAdhanEnabled) "ON" else "OFF",
                        color = if (fajrAdhanEnabled) cs.primary else cs.onSurfaceVariant
                    )
                }
            )

            DropdownMenuItem(
                text = { Text("تفعيل أذان العشاء") },
                onClick = { onToggleIshaAdhan() },
                leadingIcon = {
                    Icon(
                        imageVector = if (ishaAdhanEnabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    Text(
                        text = if (ishaAdhanEnabled) "ON" else "OFF",
                        color = if (ishaAdhanEnabled) cs.primary else cs.onSurfaceVariant
                    )
                }
            )
        }
    }
}

@Composable
fun UpdateUrlDialog(onDismiss: () -> Unit, urlInputText: String, onUrlInputChanged: (String) -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحديث رابط المصدر") }, // سيستخدم الخط الجديد
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الصق رابط Google Drive الجديد:") // سيستخدم الخط الجديد
                OutlinedTextField(value = urlInputText, onValueChange = onUrlInputChanged, label = { Text(".../file/d/...") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = urlInputText.isNotBlank()) { Text("تأكيد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable private fun SubtleText(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = typography.bodyLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, // سيستخدم الخط الجديد
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp)
    )
}

// ================== تعديل: إصلاح مشكلة خروج الساعة من الإطار ==================
// ================== تعديل: تغيير خلفية الساعة ولون الخط والتوهج ==================
@Composable
fun BigDigitalClockCard(nowMillis: Long, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val timeStr = remember(nowMillis) { TIME_DISPLAY_FORMAT.format(Date(nowMillis)) }
    val shape = RoundedCornerShape(40.dp)
    val infinite = rememberInfiniteTransition(label = "ClockPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glowColor = lerp(cs.primary, Color.White, 0.55f)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = cs.surface,
            contentColor = cs.onSurface
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        // val density = LocalDensity.current // <-- تم الحذف
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                // ================== تعديل: تغيير الخلفية ==================
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            cs.primary.copy(alpha = 0.35f * pulse),
                            cs.primary.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                ),
            // ========================================================
            contentAlignment = Alignment.Center
        ) {

            // ================== تعديل: إزالة الخلفية القديمة ==================
            // val brush = remember(...) { ... }
            // Box(modifier = Modifier.matchParentSize().background(brush))
            // ==========================================================


            // ================== تعديل: زيادة المقام لضمان الهامش ==================
            // زدنا المقام (من 4.0) ليصغر الخط قليلاً ويناسب العرض
            val widthBasedValue = maxWidth.value / 4.2f
            // زدنا المقام (من 1.5) ليصغر الخط قليلاً ويناسب الطول
            val heightBasedValue = maxHeight.value / 1.55f
            // اختيار القيمة الأصغر (باستخدام kotlin.math.min) ثم تحويلها إلى sp
            val dynamicFontSize = kotlin.math.min(widthBasedValue, heightBasedValue).sp
            // ========================================================

            Text(
                text = timeStr,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                // ================== تعديل: تغيير ستايل الساعة ==================
                style = TextStyle(
                    color = Color.White, // لون أبيض واضح
                    fontSize = dynamicFontSize, // ================== تطبيق الحجم الجديد ==================
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    shadow = Shadow( // توهج قوي
                        color = glowColor.copy(alpha = 1f), // لون التوهج من الثيم
                        offset = Offset.Zero,
                        blurRadius = 50f + 20f * pulse // زيادة قوة التوهج
                    )
                )
                // ==========================================================
            )
        }
    }
}
// ========================================================================
// ========================================================================

// ================== تعديل: تكبير خط "الوقت المتبقي" ==================
@Composable
fun BigNextPrayerCard(info: NextPrayerInfo, modifier: Modifier = Modifier, countdownSize: Int = 56) {
    val cs = MaterialTheme.colorScheme
    val header = if (info.label == "الشروق") "الوقت المتبقي حتى الشروق" else "الوقت المتبقي لصلاة ${info.label}"
    val shape = RoundedCornerShape(40.dp)
    val infinite = rememberInfiniteTransition(label = "NextPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nextPulse"
    )
    Card(
        colors = CardDefaults.cardColors(
            containerColor = cs.surface,
            contentColor = cs.onSurface
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            cs.primary.copy(alpha = 0.35f * pulse),
                            cs.primary.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ================== جعل الخط ديناميكياً ==================
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // حساب الحجم بناءً على عرض المربع
                val dynamicHeaderSize = (maxWidth.value / 11f).sp

                Text(
                    header,
                    textAlign = TextAlign.Center,
                    style = typography.headlineMedium.copy(
                        fontSize = dynamicHeaderSize, // ================== تطبيق الحجم الديناميكي ==================
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = cs.primary.copy(alpha = 0.4f * pulse),
                            blurRadius = 26f
                        )
                    ),
                    color = cs.onSurface,
                    maxLines = 1, // ================== إضافة لضمان سطر واحد ==================
                    softWrap = false // ================== إضافة لضمان سطر واحد ==================
                )
            }
            // ======================================================

            Text(
                text = info.remaining,
                style = TextStyle(
                    color = lerp(cs.primary, Color.White, 0.85f),
                    fontSize = countdownSize.sp,
                    fontWeight = FontWeight.Black,
                    // الخط سيأتي من الثيم
                    shadow = Shadow(
                        color = cs.primary.copy(alpha = 0.5f + 0.2f * pulse),
                        blurRadius = 42f + 16f * pulse
                    )
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
// ========================================================================


@Composable
fun LandscapeLayout(
    modifier: Modifier,
    dayAndDate: String,
    nowMillis: Long,
    nextPrayerInfo: NextPrayerInfo?,
    todaysTimes: PrayerTimes?,
    waitingMode: Boolean,
    onPrayerCellClick: (String) -> Unit // إضافة
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            TimeDateHeader(dayAndDate)
            BigDigitalClockCard(nowMillis = nowMillis, modifier = Modifier.fillMaxWidth().weight(1f))
            nextPrayerInfo?.let { BigNextPrayerCard(info = it, modifier = Modifier.fillMaxWidth(), countdownSize = 110) }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                waitingMode -> SubtleText("بانتظار أوقات الشهر القادم…")
                todaysTimes != null -> PrayerSchedule(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    prayerTimes = todaysTimes,
                    nextLabel = nextPrayerInfo?.label,
                    useTwoColumnGrid = true,
                    largeTypography = true,
                    onPrayerCellClick = onPrayerCellClick // تمرير
                )
                else -> SubtleText("لا توجد بيانات لعرضها حالياً.")
            }
        }
    }
}
@Composable
fun PrayerSchedule(
    modifier: Modifier = Modifier,
    prayerTimes: PrayerTimes,
    nextLabel: String?,
    useTwoColumnGrid: Boolean = false,
    largeTypography: Boolean = false,
    onPrayerCellClick: (String) -> Unit // إضافة
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(36.dp)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = cs.surface,
            contentColor = cs.onSurface
        ),
        shape = shape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (useTwoColumnGrid) {
            val items = prayerTimes.asList()
            val rows = (items.size + 1) / 2
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                cs.primary.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(rows) { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        listOf(r * 2, r * 2 + 1).forEach { idx ->
                            if (idx < items.size) {
                                val (label, time) = items[idx]
                                TimeCell(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    label = label,
                                    value = time,
                                    highlight = label == nextLabel,
                                    large = largeTypography,
                                    onClick = onPrayerCellClick // تمرير
                                )
                            } else Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ================== تعديل: إصلاح مشكلة خروج أوقات الصلوات من الإطار ==================
@Composable
private fun TimeCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    highlight: Boolean,
    large: Boolean,
    onClick: (String) -> Unit // إضافة
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (large) 30.dp else 22.dp)
    val infinite = rememberInfiniteTransition(label = "CellShimmer")
    val shimmer by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2800, easing = LinearEasing)),
        label = "cell"
    )
    val brush = remember(highlight, shimmer, cs) {
        val colors = if (highlight) {
            listOf(
                cs.primary.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.12f),
                cs.primaryContainer.copy(alpha = 0.75f)
            )
        } else {
            listOf(
                cs.surfaceVariant.copy(alpha = 0.4f),
                cs.surfaceVariant.copy(alpha = 0.2f),
                cs.surfaceVariant.copy(alpha = 0.4f)
            )
        }
        Brush.linearGradient(
            colors = colors,
            start = Offset(shimmer * 600f, 0f),
            end = Offset(0f, (1f - shimmer) * 600f),
            tileMode = TileMode.Mirror
        )
    }
    val borderAlpha = if (highlight) 0.42f else 0.22f
    val labelColor = if (highlight) Color.White else cs.onSurface.copy(alpha = 0.9f)
    val valueColor = if (highlight) cs.onPrimaryContainer else cs.onSurface

    BoxWithConstraints(modifier = modifier) {
        val cellWidth = maxWidth
        val cellHeight = maxHeight // ================== جلب ارتفاع الخلية ==================

        // ================== تعديل: إصلاح حساب min ==================
        // الحجم بناءً على العرض
        val widthBasedLabelValue = cellWidth.value / 5.2f
        // الحجم بناءً على الطول (نخصص له جزء من الارتفاع، مثلا 40%)
        val heightBasedLabelValue = cellHeight.value / 4.0f
        // اختيار القيمة الأصغر (باستخدام kotlin.math.min) ثم تحويلها إلى sp
        val dynamicLabelSize = kotlin.math.min(widthBasedLabelValue, heightBasedLabelValue).sp

        // الحجم بناءً على العرض
        val widthBasedValueValue = cellWidth.value / 3.5f
        // الحجم بناءً على الطول (نخصص له جزء أكبر، مثلا 60% مع هامش)
        // ================== تعديل: زيادة المقام لمنع قص النص السفلي ==================
        val heightBasedValueValue = cellHeight.value / 3.0f // تم التعديل من 2.8f
        // ======================================================================
        // اختيار القيمة الأصغر (باستخدام kotlin.math.min) ثم تحويلها إلى sp
        val dynamicValueSize = kotlin.math.min(widthBasedValueValue, heightBasedValueValue).sp
        // ========================================================

        val baseLabelStyle = if (large) typography.titleLarge else typography.titleMedium
        val baseValueStyle = if (large) typography.headlineMedium else typography.headlineSmall

        val labelStyle = baseLabelStyle.copy(
            fontSize = dynamicLabelSize, // ================== تطبيق الحجم الجديد ==================
            fontWeight = if (large) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.35.sp,
            shadow = Shadow(color = cs.primary.copy(alpha = if (highlight) 0.35f else 0.18f), blurRadius = 14f)
        )
        val valueStyle = baseValueStyle.copy(
            fontSize = dynamicValueSize, // ================== تطبيق الحجم الجديد ==================
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            shadow = Shadow(color = cs.primary.copy(alpha = if (highlight) 0.35f else 0.18f), blurRadius = 24f)
        )

        Column(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .clickable { onClick(label) }
                .background(brush)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = borderAlpha)), shape)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                label,
                color = labelColor,
                style = labelStyle,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
            Text(
                value,
                color = valueColor,
                style = valueStyle,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
// ========================================================================

// =========================== البيانات + التخزين + الشبكة ===============================
data class PrayerTimes(
    val fajr: String, val sunrise: String, val dhuhr: String, val asr: String, val maghrib: String, val isha: String
) {
    fun asList(): List<Pair<String, String>> =
        listOf("الفجر" to fajr, "الشروق" to sunrise, "الظهر" to dhuhr, "العصر" to asr, "المغرب" to maghrib, "العشاء" to isha)
}
data class PrayerDataset(val days: Map<String, PrayerTimes>)
data class NextPrayerInfo(val label: String, val time: String, val remaining: String)

fun PrayerDataset?.getDateRangeStrings(): Pair<String, String>? {
    if (this == null || this.days.isEmpty()) return null
    return try {
        val sorted = (this.days as? SortedMap<String, PrayerTimes>) ?: this.days.toSortedMap()
        sorted.firstKey() to sorted.lastKey()
    } catch (_: NoSuchElementException) { null }
}

fun parsePrayerDataset(json: String): PrayerDataset? = runCatching {
    val root = JSONObject(json)
    val collected = mutableMapOf<String, PrayerTimes>()
    val keys = root.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (isDateKey(key)) root.optJSONObject(key)?.toPrayerTimesOrNull()?.let { collected[key] = it }
    }
    if (root.has("days") && root.opt("days") is JSONArray) {
        val arr = root.getJSONArray("days")
        for (i in 0 until arr.length()) {
            val dayObject = arr.optJSONObject(i) ?: continue
            val date = dayObject.optString("date")
            if (isDateKey(date)) dayObject.toPrayerTimesOrNull()?.let { collected[date] = it }
        }
    }
    if (root.has("days") && root.opt("days") is JSONObject) {
        val obj = root.getJSONObject("days")
        val dateKeys = obj.keys()
        while (dateKeys.hasNext()) {
            val date = dateKeys.next()
            if (isDateKey(date)) obj.optJSONObject(date)?.toPrayerTimesOrNull()?.let { collected[date] = it }
        }
    }
    if (collected.isEmpty()) null else PrayerDataset(collected.toSortedMap())
}.getOrNull()

fun JSONObject.toPrayerTimesOrNull(): PrayerTimes? {
    val fajr = optString("fajr"); val sunrise = optString("sunrise"); val dhuhr = optString("dhuhr")
    val asr = optString("asr"); val maghrib = optString("maghrib"); val isha = optString("isha")
    return if (listOf(fajr, sunrise, dhuhr, asr, maghrib, isha).all { it.isNotBlank() })
        PrayerTimes(fajr, sunrise, dhuhr, asr, maghrib, isha) else null
}

fun PrayerDataset.trimBefore(dateKey: String): PrayerDataset? {
    val filtered = days.filterKeys { it >= dateKey }
    return if (filtered.isEmpty()) null else PrayerDataset(filtered)
}

fun PrayerDataset.findNextPrayer(nowMillis: Long): NextPrayerInfo? {
    val orderedDays = days.keys.sorted()
    for (dateKey in orderedDays) {
        val times = days[dateKey] ?: continue
        for ((label, time) in times.asList()) {
            val dateTime = DATE_TIME_PARSER.parse("$dateKey $time")?.time ?: continue
            if (dateTime > nowMillis) return NextPrayerInfo(label, time, formatDuration(dateTime - nowMillis))
        }
    }
    return null
}

private val PREFS_DIR by lazy { File(System.getProperty("user.home"), ".prayer-app-desktop").apply { mkdirs() } }
private val JSON_FILE = File(PREFS_DIR, "prayer_times.json")
private val URL_FILE = File(PREFS_DIR, "custom_url.txt")
private val ADHAN_SETTINGS_FILE = File(PREFS_DIR, "adhan_settings.json")
private val ADHAN_AUDIO_DIR = File(PREFS_DIR, "adhans").apply { mkdirs() }

fun savePrayerDataset(dataset: PrayerDataset) {
    try { JSON_FILE.writeText(dataset.toJsonString(), Charsets.UTF_8) } catch (e: Exception) { e.printStackTrace() }
}

fun loadPrayerDataset(): PrayerDataset? = try {
    if (!JSON_FILE.exists()) null else parsePrayerDataset(JSON_FILE.readText(Charsets.UTF_8))
} catch (e: Exception) { e.printStackTrace(); null }

private fun PrayerDataset.toJsonString(): String {
    val root = JSONObject(); val daysObject = JSONObject()
    for ((date, t) in days) {
        val dayObject = JSONObject()
            .put("fajr", t.fajr).put("sunrise", t.sunrise).put("dhuhr", t.dhuhr)
            .put("asr", t.asr).put("maghrib", t.maghrib).put("isha", t.isha)
        daysObject.put(date, dayObject)
    }
    root.put("days", daysObject)
    return root.toString()
}

fun saveCustomUrl(directUrl: String) { try { URL_FILE.writeText(directUrl) } catch (e: Exception) { e.printStackTrace() } }
fun loadCustomUrl(): String = try {
    if (!URL_FILE.exists()) REMOTE_JSON_URL_DEFAULT
    else URL_FILE.readText().trim().ifBlank { REMOTE_JSON_URL_DEFAULT }
} catch (e: Exception) { e.printStackTrace(); REMOTE_JSON_URL_DEFAULT }

fun saveAdhanSettings(settings: AdhanSettings) {
    runCatching {
        if (!PREFS_DIR.exists()) PREFS_DIR.mkdirs()
        val root = JSONObject()
        val custom = JSONObject()
        settings.customFiles.forEach { (key, path) -> custom.put(key, path) }
        root.put("custom", custom)
        root.put("disableFajr", settings.disableFajr)
        root.put("disableIsha", settings.disableIsha)
        ADHAN_SETTINGS_FILE.writeText(root.toString(), Charsets.UTF_8)
    }.onFailure { it.printStackTrace() }
}

fun loadAdhanSettings(): AdhanSettings {
    if (!ADHAN_SETTINGS_FILE.exists()) return AdhanSettings()
    return runCatching {
        val obj = JSONObject(ADHAN_SETTINGS_FILE.readText(Charsets.UTF_8))
        val customMap = mutableMapOf<String, String>()
        obj.optJSONObject("custom")?.let { customObj ->
            val keys = customObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val path = customObj.optString(key)
                if (path.isNotBlank()) customMap[key] = path
            }
        }
        var settings = AdhanSettings(
            customFiles = customMap,
            disableFajr = obj.optBoolean("disableFajr", false),
            disableIsha = obj.optBoolean("disableIsha", false)
        )
        val sanitizedCustom = settings.customFiles.filterValues { File(it).exists() }
        if (sanitizedCustom.size != settings.customFiles.size) {
            settings = settings.copy(customFiles = sanitizedCustom)
            saveAdhanSettings(settings)
        }
        settings
    }.getOrElse {
        it.printStackTrace()
        AdhanSettings()
    }
}

private fun storeCustomAdhanFile(labelKey: String, sourceFile: File): File? {
    if (!sourceFile.exists()) return null
    if (!sourceFile.extension.equals("wav", ignoreCase = true)) return null
    return runCatching {
        if (!ADHAN_AUDIO_DIR.exists()) ADHAN_AUDIO_DIR.mkdirs()
        val destFile = File(ADHAN_AUDIO_DIR, "${labelKey}_custom.wav")
        if (!sourceFile.absolutePath.equals(destFile.absolutePath, ignoreCase = true)) {
            sourceFile.copyTo(destFile, overwrite = true)
        }
        destFile
    }.getOrElse {
        it.printStackTrace()
        null
    }
}

private sealed class WavSelectionResult {
    object Cancelled : WavSelectionResult()
    object InvalidFormat : WavSelectionResult()
    data class Selected(val file: File) : WavSelectionResult()
}

private fun showWavFileDialog(title: String): WavSelectionResult {
    return try {
        val dialog = FileDialog(null as java.awt.Frame?, title, FileDialog.LOAD).apply {
            file = "*.wav"
            isMultipleMode = false
            isVisible = true
        }
        val fileName = dialog.file
        val directory = dialog.directory
        dialog.dispose()
        if (fileName == null || directory == null) {
            WavSelectionResult.Cancelled
        } else {
            val selected = File(directory, fileName)
            if (selected.extension.equals("wav", ignoreCase = true)) WavSelectionResult.Selected(selected)
            else WavSelectionResult.InvalidFormat
        }
    } catch (e: Exception) {
        e.printStackTrace()
        WavSelectionResult.Cancelled
    }
}

suspend fun fetchAndApplyRemote(currentDateKey: String, remoteUrl: String): UpdateResult = runCatching {
    val json = fetchUrl(remoteUrl) ?: return UpdateResult(false, "تعذّر الوصول إلى الرابط.", false)
    val parsed = parsePrayerDataset(json) ?: return UpdateResult(false, "تعذّر قراءة الملف: JSON غير صالح.", false)
    val trimmed = parsed.trimBefore(currentDateKey)
    if (trimmed != null) {
        savePrayerDataset(trimmed)
        UpdateResult(true, null, true)
    } else UpdateResult(false, null, false)
}.getOrElse { e -> UpdateResult(false, "فشل التحديث: ${e.message ?: "خطأ غير معروف"}", false) }

private suspend fun fetchUrl(url: String, connectTimeoutMs: Int = 10000, readTimeoutMs: Int = 15000): String? =
    withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs; readTimeout = readTimeoutMs; requestMethod = "GET"
            setRequestProperty("Accept", "application/json,*/*")
        }
        try {
            if (conn.responseCode in 200..299)
                BufferedInputStream(conn.inputStream).bufferedReader(Charsets.UTF_8).use { it.readText() }
            else null
        } finally { conn.disconnect() }
    }

// ================== هذا هو المكان الذي تم تصحيحه ==================
@Composable
fun PrayerTimesScreen(
    statusMessage: String?, onStatusDismiss: () -> Unit,
    dayAndDate: String, nowMillis: Long,
    nextPrayerInfo: NextPrayerInfo?, todaysTimes: PrayerTimes?, waitingMode: Boolean,
    onRefreshClicked: () -> Unit, isRefreshing: Boolean, dateRange: Pair<String, String>?,
    showUrlDialog: Boolean, onShowUrlDialog: () -> Unit, onDismissUrlDialog: () -> Unit,
    urlInputText: String, onUrlInputChanged: (String) -> Unit, onConfirmUrl: () -> Unit,
    effectiveNextPrayerLabel: String?,
    onPrayerCellClick: (String) -> Unit,
    onSelectCustomAdhan: (String) -> Unit,
    onToggleFajrAdhan: () -> Unit,
    onToggleIshaAdhan: () -> Unit,
    fajrAdhanEnabled: Boolean,
    ishaAdhanEnabled: Boolean
) {
    if (showUrlDialog) {
        // تم تغيير "onDismiss = onDismiss" إلى "onDismiss = onDismissUrlDialog"
        UpdateUrlDialog(onDismiss = onDismissUrlDialog, urlInputText = urlInputText, onUrlInputChanged = onUrlInputChanged, onConfirm = onConfirmUrl)
    }
    PrayerScreenBackground(nextPrayerLabel = effectiveNextPrayerLabel, modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            LandscapeLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                dayAndDate = dayAndDate,
                nowMillis = nowMillis,
                nextPrayerInfo = nextPrayerInfo,
                todaysTimes = todaysTimes,
                waitingMode = waitingMode,
                onPrayerCellClick = onPrayerCellClick
            )
        }
        TopRightMenu(
            modifier = Modifier.align(Alignment.TopEnd).zIndex(2f).padding(16.dp),
            onRefreshClicked = onRefreshClicked,
            isRefreshing = isRefreshing,
            dateRange = dateRange,
            onUpdateUrlClick = onShowUrlDialog,
            onSelectCustomAdhan = onSelectCustomAdhan,
            onToggleFajrAdhan = onToggleFajrAdhan,
            onToggleIshaAdhan = onToggleIshaAdhan,
            fajrAdhanEnabled = fajrAdhanEnabled,
            ishaAdhanEnabled = ishaAdhanEnabled
        )
        if (statusMessage != null) {
            FloatingStatusMessage(
                message = statusMessage,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(3f).padding(top = 16.dp),
                onDismiss = onStatusDismiss
            )
        }
    }
}
// ========================================================================

// =========================== أدوات مساعدة ===============================
private val DATE_KEY_FORMAT by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getDefault() } }
private val DATE_TIME_PARSER by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = TimeZone.getDefault() } }
private val ARABIC_WEEKDAY_NAMES = mapOf(
    Calendar.SATURDAY to "السبت",
    Calendar.SUNDAY to "الأحد",
    Calendar.MONDAY to "الإثنين",
    Calendar.TUESDAY to "الثلاثاء",
    Calendar.WEDNESDAY to "الأربعاء",
    Calendar.THURSDAY to "الخميس",
    Calendar.FRIDAY to "الجمعة"
)

private val ARABIC_MONTH_NAMES = arrayOf(
    "يناير",
    "فبراير",
    "مارس",
    "ابريل",
    "مايو",
    "يونيو",
    "يوليو",
    "اغسطس",
    "سبتمبر",
    "اكتوبر",
    "نوفمبر",
    "ديسمبر"
)
private val TIME_DISPLAY_FORMAT by lazy { SimpleDateFormat("HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getDefault() } }

private fun isDateKey(value: String?): Boolean =
    !value.isNullOrBlank() && runCatching { DATE_KEY_FORMAT.parse(value) != null }.getOrDefault(false)

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
}

fun dateKeyForMillis(millis: Long): String = DATE_KEY_FORMAT.format(Date(millis))
fun dayNameForMillis(millis: Long): String {
    val calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.ROOT).apply { timeInMillis = millis }
    return ARABIC_WEEKDAY_NAMES[calendar.get(Calendar.DAY_OF_WEEK)] ?: ""
}

fun displayDateForMillis(millis: Long): String {
    val calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.ROOT).apply { timeInMillis = millis }
    val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
    val monthName = ARABIC_MONTH_NAMES.getOrNull(calendar.get(Calendar.MONTH)) ?: ""
    return String.format(Locale.ROOT, "%d %s", dayOfMonth, monthName).toEnglishDigits()
}

private fun String.toEnglishDigits(): String {
    if (isEmpty()) return this
    val builder = StringBuilder(length)
    for (char in this) {
        val mapped = when (char) {
            '٠', '۰' -> '0'
            '١', '۱' -> '1'
            '٢', '۲' -> '2'
            '٣', '۳' -> '3'
            // ================== تعديل: إصلاح الخطأ الإملائي ==================
            '٤', '۴' -> '4' // كانت '4"'
            // ========================================================
            '٥', '۵' -> '5'
            '٦', '۶' -> '6'
            '٧', '۷' -> '7'
            '٨', '۸' -> '8'
            '٩', '۹' -> '9'
            else -> char
        }
        builder.append(mapped)
    }
    return builder.toString()
}

data class UpdateResult(val updated: Boolean, val error: String?, val hadFresh: Boolean)

fun convertGoogleDriveLink(driveUrl: String): String? {
    val trimmedUrl = driveUrl.trim()
    if (trimmedUrl.contains("/uc?export=download&id=")) return trimmedUrl
    // ================== هذا هو السطر الذي تم تصحيحه (من المرة السابقة) ==================
    // الخطأ كان: [a-zA-Z0.9_-] (كان ينقص - بين 0 و 9)
    // الصحيح هو: [a-zA-Z0-9_-]
    val pattern = Pattern.compile("(?:/file/d/|id=)([a-zA-Z0-9_-]{28,})")
    // ==============================================================
    val matcher = pattern.matcher(trimmedUrl)
    return if (matcher.find()) "https://drive.google.com/uc?export=download&id=${matcher.group(1)}" else null
}