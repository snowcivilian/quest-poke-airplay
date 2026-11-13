package io.github.snowcivilian.questairplay.pokemon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/** A line of text and where it is, as fractions (0..1) of the image it was read from. */
data class TextLine(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val cx get() = (left + right) / 2
    val cy get() = (top + bottom) / 2
    val height get() = bottom - top
}

/** Reads text from a bitmap (S4). Blocking; call off the main and GL threads. */
interface TextReader {
    fun read(bitmap: Bitmap): String
    /** Each line with its position, for reading a whole screen at once. */
    fun lines(bitmap: Bitmap): List<TextLine>
}

/**
 * Google ML Kit text recognition with the Latin model bundled in the APK. Whether it runs on a
 * Quest without Google Play Services is checked by [OcrSelfTest].
 */
class MlKitTextReader : TextReader {
    private val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    override fun read(bitmap: Bitmap): String =
        Tasks.await(client.process(InputImage.fromBitmap(bitmap, 0)), 3, TimeUnit.SECONDS).text

    override fun lines(bitmap: Bitmap): List<TextLine> {
        val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
        return Tasks.await(client.process(InputImage.fromBitmap(bitmap, 0)), 3, TimeUnit.SECONDS).textBlocks
            .flatMap { it.lines }
            .mapNotNull { l -> l.boundingBox?.let { b -> TextLine(l.text, b.left / w, b.top / h, b.right / w, b.bottom / h) } }
    }
}

/** Renders game-like blocky text and reads it back, to check OCR works on this device. */
object OcrSelfTest {
    const val SAMPLE = "CHARMANDER Lv5"

    fun run(reader: TextReader): String {
        // draw small, then scale up without smoothing so it looks like a pixel font on a light HP box
        val small = Bitmap.createBitmap(120, 16, Bitmap.Config.ARGB_8888)
        Canvas(small).apply {
            drawColor(Color.rgb(248, 248, 216))
            drawText(SAMPLE, 2f, 12f, Paint().apply {
                color = Color.rgb(64, 64, 64); textSize = 11f; typeface = Typeface.MONOSPACE; isAntiAlias = false
            })
        }
        val big = Bitmap.createScaledBitmap(small, small.width * 6, small.height * 6, false)
        val t0 = System.nanoTime()
        val text = runCatching { reader.read(big) }.getOrElse { return "failed: ${it.javaClass.simpleName}: ${it.message}" }
        val ms = (System.nanoTime() - t0) / 1_000_000
        return "read \"${text.replace('\n', ' ')}\" in $ms ms (expected \"$SAMPLE\")"
    }
}
