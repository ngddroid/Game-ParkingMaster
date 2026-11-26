package com.ngstudio.inchperfect.game.render

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngstudio.inchperfect.game.physics.CarDesign
import com.ngstudio.inchperfect.game.physics.CarSpec

enum class Finish(val id: String, val title: String) {
    SOLID("solid", "Gloss"), METALLIC("metallic", "Metallic"), PEARL("pearl", "Pearl"), MATTE("matte", "Matte"), CHROME("chrome", "Chrome");
    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: SOLID }
}

enum class Decal(val id: String, val title: String) {
    NONE("none", "Clean"), STRIPES("stripes", "Twin Stripes"), TWO_TONE("twotone", "Two-Tone"), CHECKER("checker", "Checker Roof"),
    BOLT("bolt", "Lightning"), NUMBER("number", "Race Number"), CARBON("carbon", "Carbon Hood");
    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: NONE }
}

data class CarLook(
    val design: CarDesign, val color: Int, val finish: Finish = Finish.SOLID, val decal: Decal = Decal.NONE,
    val accent: Int = 0xFFFFFF, val number: Int = 7, val axleR: Float = 0.2f, val axleF: Float = 0.8f,
)

fun CarSpec.look(color: Int = defaultColor, finish: Finish = Finish.SOLID, decal: Decal = Decal.NONE, accent: Int = 0xFFFFFF) =
    CarLook(design, color, finish, decal, accent, axleR = axleRear, axleF = axleFront)

/** Procedural top-down sprites (car points to +x). Cached by look + scale. */
object Sprites {
    private val cache = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            if (size > 90) { eldest?.value?.recycle(); return true }
            return false
        }
    }
    const val PAD = 0.35f // metres of padding around sprites

    @Synchronized
    fun car(look: CarLook, lengthM: Double, widthM: Double, ppm: Float): Bitmap {
        val key = "car-$look-$lengthM-$widthM-$ppm"
        cache[key]?.let { if (!it.isRecycled) return it }
        val bmp = drawCar(look, lengthM.toFloat(), widthM.toFloat(), ppm)
        cache[key] = bmp
        return bmp
    }

    @Synchronized
    fun shadow(lengthM: Double, widthM: Double, ppm: Float, round: Float = 0.25f): Bitmap {
        val key = "shadow-$lengthM-$widthM-$ppm-$round"
        cache[key]?.let { if (!it.isRecycled) return it }
        val pad = 0.6f
        val w = ((lengthM.toFloat() + pad * 2) * ppm).toInt(); val h = ((widthM.toFloat() + pad * 2) * ppm).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; maskFilter = BlurMaskFilter(0.22f * ppm, BlurMaskFilter.Blur.NORMAL) }
        val r = widthM.toFloat() * ppm * round
        c.drawRoundRect(RectF(pad * ppm, pad * ppm, w - pad * ppm, h - pad * ppm), r, r, p)
        cache[key] = bmp
        return bmp
    }

    @Synchronized
    fun get(key: String, w: Int, h: Int, painter: (Canvas) -> Unit): Bitmap {
        cache[key]?.let { if (!it.isRecycled) return it }
        val bmp = Bitmap.createBitmap(maxOf(1, w), maxOf(1, h), Bitmap.Config.ARGB_8888)
        painter(Canvas(bmp))
        cache[key] = bmp
        return bmp
    }

    @Synchronized
    fun clear() { cache.values.forEach { it.recycle() }; cache.clear() }

    // ------------------------------------------------------------ colour utils
    fun lighten(c: Int, f: Float): Int {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return Color.rgb((r + (255 - r) * f).toInt(), (g + (255 - g) * f).toInt(), (b + (255 - b) * f).toInt())
    }
    fun darken(c: Int, f: Float): Int = Color.rgb((Color.red(c) * (1 - f)).toInt(), (Color.green(c) * (1 - f)).toInt(), (Color.blue(c) * (1 - f)).toInt())
    fun hueShift(c: Int, deg: Float): Int { val hsv = FloatArray(3); Color.colorToHSV(c, hsv); hsv[0] = (hsv[0] + deg + 360) % 360; return Color.HSVToColor(hsv) }
    fun desat(c: Int, f: Float): Int { val hsv = FloatArray(3); Color.colorToHSV(c or 0xFF000000.toInt(), hsv); hsv[1] *= (1 - f); return Color.HSVToColor(hsv) }
    fun luminance(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0

    // --------------------------------------------------------------- car art
    private fun drawCar(look: CarLook, lengthM: Float, widthM: Float, ppm: Float): Bitmap {
        val L = lengthM * ppm; val W = widthM * ppm
        val pad = PAD * ppm
        val bmp = Bitmap.createBitmap((L + pad * 2).toInt(), (W + pad * 2).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(pad + L / 2, pad + W / 2)
        CarPainter.paint(c, look, L, W)
        return bmp
    }

    /** Paints a car centred at the origin, nose to +x (used by the garage UI too). */
    fun paintCar(c: Canvas, look: CarLook, L: Float, W: Float) = CarPainter.paint(c, look, L, W)

    // ------------------------------------------------------------- props
    fun tree(ppm: Float, radiusM: Float, hue: Int, variant: Int): Bitmap {
        val key = "tree-$ppm-$radiusM-$hue-${variant % 4}"
        val size = ((radiusM * 2 + 0.8f) * ppm).toInt()
        return get(key, size, size) { c ->
            val cx = size / 2f; val cy = size / 2f; val R = radiusM * ppm
            val rnd = java.util.Random(variant.toLong() % 4 + 11)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val baseC = hue or 0xFF000000.toInt()
            p.color = darken(baseC, 0.45f)
            c.drawCircle(cx, cy, R, p)
            repeat(9) {
                val a = rnd.nextFloat() * 6.283f; val d = R * 0.45f * rnd.nextFloat()
                val rr = R * (0.45f + rnd.nextFloat() * 0.3f)
                p.shader = RadialGradient(cx + kotlin.math.cos(a) * d - rr * 0.25f, cy + kotlin.math.sin(a) * d - rr * 0.25f, rr,
                    intArrayOf(lighten(baseC, 0.25f), baseC, darken(baseC, 0.3f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx + kotlin.math.cos(a) * d, cy + kotlin.math.sin(a) * d, rr, p)
            }
            p.shader = null
            repeat(140) {
                val a = rnd.nextFloat() * 6.283f; val d = R * kotlin.math.sqrt(rnd.nextFloat()) * 0.92f
                p.color = if (rnd.nextBoolean()) lighten(baseC, 0.35f) and 0x99FFFFFF.toInt() else darken(baseC, 0.4f) and 0x88FFFFFF.toInt()
                c.drawCircle(cx + kotlin.math.cos(a) * d, cy + kotlin.math.sin(a) * d, R * 0.05f, p)
            }
        }
    }

    fun cone(ppm: Float): Bitmap {
        val s = (0.75f * ppm).toInt()
        return get("cone-$ppm", s, s) { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val m = s / 2f
            p.color = 0xFF1C1C1C.toInt(); c.drawRoundRect(RectF(m - s * 0.42f, m - s * 0.42f, m + s * 0.42f, m + s * 0.42f), s * 0.08f, s * 0.08f, p)
            p.shader = RadialGradient(m - s * 0.08f, m - s * 0.08f, s * 0.34f, intArrayOf(0xFFFFB066.toInt(), 0xFFFF6A00.toInt(), 0xFFB34400.toInt()), null, Shader.TileMode.CLAMP)
            c.drawCircle(m, m, s * 0.33f, p); p.shader = null
            p.color = 0xFFF5F5F5.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.07f
            c.drawCircle(m, m, s * 0.2f, p); p.style = Paint.Style.FILL
            p.color = 0xFFFF8A2A.toInt(); c.drawCircle(m, m, s * 0.09f, p)
        }
    }
}
