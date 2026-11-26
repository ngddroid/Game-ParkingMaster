package com.ngstudio.inchperfect.game.input

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

enum class RimShape { ROUND, FLAT_BOTTOM, YOKE, F1, BUTTERFLY, DEEP_DISH, OCTAGON, HEXAGON }
enum class Spokes { TWO, THREE, Y, FOUR, SIX_HOLE, BANJO, NONE }
enum class Hub { BADGE, BUTTONS, SCREEN, STAR }
enum class Mat {
    CARBON, LEATHER, ALCANTARA, WOOD, CHROME, GOLD, BRASS, HOLO, GALAXY, LAVA, ICE, CAMO, PASTEL, SUNSET, TIGER, CHECKER, PIXEL, MATTE
}

/** A steering-wheel cosmetic. [color] tints leather/alcantara/matte, [accent] is the top marker. */
data class WheelDesign(
    val id: String, val name: String, val rim: RimShape, val spokes: Spokes, val mat: Mat,
    val color: Int, val accent: Int, val hub: Hub = Hub.BADGE, val price: Int = 0, val starPrice: Int = 0, val glow: Boolean = false,
)

object Wheels {
    val ALL = listOf(
        WheelDesign("classic", "Pro Classic", RimShape.ROUND, Spokes.THREE, Mat.LEATHER, 0x23262D, 0x19E3FF),
        WheelDesign("sportd", "Sport D", RimShape.FLAT_BOTTOM, Spokes.THREE, Mat.LEATHER, 0x1A1C21, 0xFF3B3B, price = 800),
        WheelDesign("kart", "Kart Mini", RimShape.OCTAGON, Spokes.TWO, Mat.MATTE, 0xD7263D, 0xFFFFFF, price = 1_000),
        WheelDesign("tan", "Tan Tourer", RimShape.ROUND, Spokes.FOUR, Mat.LEATHER, 0x8B5A33, 0xF2E3C6, price = 1_200),
        WheelDesign("trucker", "Big Rig", RimShape.ROUND, Spokes.FOUR, Mat.LEATHER, 0x2B2F36, 0xFFC21A, Hub.STAR, price = 1_500),
        WheelDesign("wood", "Wood Cruiser", RimShape.ROUND, Spokes.TWO, Mat.WOOD, 0x8A5A2B, 0xE8C15A, price = 1_800),
        WheelDesign("pastel", "Pastel Pop", RimShape.ROUND, Spokes.TWO, Mat.PASTEL, 0xFFB7C5, 0x7FD8F0, price = 2_000),
        WheelDesign("mint", "Mint Breeze", RimShape.FLAT_BOTTOM, Spokes.THREE, Mat.ALCANTARA, 0x2EF2B0, 0x0B1026, price = 2_200),
        WheelDesign("chrome", "Chrome Retro", RimShape.ROUND, Spokes.BANJO, Mat.CHROME, 0xC9CED6, 0xFF3B3B, price = 2_500),
        WheelDesign("navy", "Midnight Blue", RimShape.ROUND, Spokes.Y, Mat.LEATHER, 0x1C2A44, 0x19E3FF, price = 2_600),
        WheelDesign("rally", "Rally Dish", RimShape.DEEP_DISH, Spokes.SIX_HOLE, Mat.ALCANTARA, 0xB3202A, 0xFFFFFF, price = 3_000),
        WheelDesign("camo", "Camo Ranger", RimShape.ROUND, Spokes.FOUR, Mat.CAMO, 0x4A5A2E, 0xFF7A1A, price = 3_000),
        WheelDesign("octa", "Octa Drift", RimShape.OCTAGON, Spokes.THREE, Mat.ALCANTARA, 0x2F5D8A, 0x9BFF3C, price = 3_500),
        WheelDesign("hex", "Hex Racer", RimShape.HEXAGON, Spokes.Y, Mat.CARBON, 0x1A1C21, 0xFF3DA5, price = 3_500),
        WheelDesign("royal", "Royal Velvet", RimShape.ROUND, Spokes.THREE, Mat.ALCANTARA, 0x4B2A7A, 0xE8C15A, Hub.STAR, price = 3_800),
        WheelDesign("yoke", "Carbon Yoke", RimShape.YOKE, Spokes.TWO, Mat.CARBON, 0x1A1C21, 0xFF7A1A, Hub.SCREEN, price = 4_000),
        WheelDesign("pixel", "Pixel Arcade", RimShape.OCTAGON, Spokes.FOUR, Mat.PIXEL, 0x3A2A6A, 0xFFE27A, price = 4_000),
        WheelDesign("sunset", "Sunset Drive", RimShape.FLAT_BOTTOM, Spokes.Y, Mat.SUNSET, 0xFF7A1A, 0xFFFFFF, price = 4_200),
        WheelDesign("ocean", "Ocean Blue", RimShape.ROUND, Spokes.Y, Mat.ALCANTARA, 0x1F6FB2, 0xFFFFFF, price = 4_500),
        WheelDesign("racingred", "Racing Red", RimShape.FLAT_BOTTOM, Spokes.THREE, Mat.LEATHER, 0xB3202A, 0xFFFFFF, price = 4_500),
        WheelDesign("forest", "Forest Green", RimShape.OCTAGON, Spokes.FOUR, Mat.LEATHER, 0x2E5E4E, 0xE8C15A, price = 4_500),
        WheelDesign("tiger", "Tiger Claw", RimShape.ROUND, Spokes.THREE, Mat.TIGER, 0xFF8A1A, 0x111111, price = 4_800),
        WheelDesign("butterfly", "Butterfly", RimShape.BUTTERFLY, Spokes.TWO, Mat.CARBON, 0x1A1C21, 0x9BFF3C, Hub.BUTTONS, price = 5_000),
        WheelDesign("brass", "Steampunk Brass", RimShape.ROUND, Spokes.BANJO, Mat.BRASS, 0xB5862E, 0x5A3A1A, Hub.STAR, price = 5_200),
        WheelDesign("checker", "Checkered Flag", RimShape.F1, Spokes.NONE, Mat.CHECKER, 0xFFFFFF, 0xFF3B3B, Hub.SCREEN, price = 5_500),
        WheelDesign("f1", "F1 Pro", RimShape.F1, Spokes.NONE, Mat.CARBON, 0x1A1C21, 0x19E3FF, Hub.SCREEN, price = 6_000),
        WheelDesign("lava", "Lava Core", RimShape.DEEP_DISH, Spokes.SIX_HOLE, Mat.LAVA, 0x2A0E08, 0xFF5E2B, price = 6_000),
        WheelDesign("ice", "Ice Crystal", RimShape.HEXAGON, Spokes.Y, Mat.ICE, 0xBFE9FF, 0xFFFFFF, price = 6_000),
        WheelDesign("ivory", "Ivory Luxe", RimShape.BUTTERFLY, Spokes.TWO, Mat.LEATHER, 0xF2E8D5, 0xE8C15A, Hub.STAR, price = 6_200),
        WheelDesign("galaxy", "Galaxy", RimShape.FLAT_BOTTOM, Spokes.THREE, Mat.GALAXY, 0x2A1450, 0xB08CFF, price = 6_500),
        WheelDesign("holo", "Holo Wave", RimShape.ROUND, Spokes.Y, Mat.HOLO, 0xFFFFFF, 0xFFFFFF, price = 7_000),
        WheelDesign("gold", "Gold Legend", RimShape.ROUND, Spokes.THREE, Mat.GOLD, 0xE8C15A, 0x111111, Hub.STAR, starPrice = 500),
        WheelDesign("diamond", "Diamond", RimShape.HEXAGON, Spokes.THREE, Mat.ICE, 0xE8F8FF, 0xE8C15A, Hub.STAR, starPrice = 1_200),
        WheelDesign("signature", "Inch Perfect Signature", RimShape.YOKE, Spokes.TWO, Mat.GOLD, 0xE8C15A, 0x19E3FF, Hub.SCREEN, starPrice = 3_000),
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: ALL[0]
}

/** Paints a steering wheel (used in-game by Controls and in the garage). */
object WheelPainter {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tiles = HashMap<Mat, Bitmap>()

    private fun tile(m: Mat): Bitmap = synchronized(tiles) {
        tiles.getOrPut(m) {
            val s = 64
            val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(b); val q = Paint(Paint.ANTI_ALIAS_FLAG)
            val rnd = java.util.Random(m.ordinal * 97L)
            when (m) {
                Mat.CARBON -> {
                    c.drawColor(0xFF15171B.toInt())
                    for (y in 0 until s step 8) for (x in 0 until s step 8) {
                        q.color = if ((x / 8 + y / 8) % 2 == 0) 0xFF2A2E35.toInt() else 0xFF1C1F24.toInt()
                        c.drawRect(x.toFloat(), y.toFloat(), x + 8f, y + 4f, q)
                        q.color = if ((x / 8 + y / 8) % 2 == 0) 0xFF1C1F24.toInt() else 0xFF2A2E35.toInt()
                        c.drawRect(x.toFloat(), y + 4f, x + 8f, y + 8f, q)
                    }
                }
                Mat.WOOD -> {
                    c.drawColor(0xFF7A4A22.toInt())
                    for (k in 0 until 26) { q.color = if (k % 2 == 0) 0x40FFD9A0 else 0x40301806; q.strokeWidth = 1f + rnd.nextFloat() * 2; val y = rnd.nextFloat() * s; c.drawLine(0f, y, s.toFloat(), y + rnd.nextFloat() * 6 - 3, q) }
                }
                Mat.CAMO -> {
                    c.drawColor(0xFF4A5A2E.toInt())
                    val cols = intArrayOf(0xFF2E3A1C.toInt(), 0xFF7A6A44.toInt(), 0xFF1E2416.toInt())
                    repeat(22) { q.color = cols[it % 3]; c.drawOval(RectF(rnd.nextFloat() * s - 8, rnd.nextFloat() * s - 6, rnd.nextFloat() * s + 8, rnd.nextFloat() * s + 6).apply { sort() }, q) }
                }
                Mat.TIGER -> {
                    c.drawColor(0xFFFF8A1A.toInt()); q.color = 0xFF111111.toInt(); q.strokeWidth = 4f
                    for (k in 0 until 7) { val x = k * 10f; c.drawLine(x, 0f, x + 6, s.toFloat(), q) }
                }
                Mat.CHECKER -> { for (y in 0 until s step 8) for (x in 0 until s step 8) { q.color = if ((x / 8 + y / 8) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF111111.toInt(); c.drawRect(x.toFloat(), y.toFloat(), x + 8f, y + 8f, q) } }
                Mat.PIXEL -> { val cols = intArrayOf(0xFF3A2A6A.toInt(), 0xFFFF3DA5.toInt(), 0xFF19E3FF.toInt(), 0xFFFFE27A.toInt()); for (y in 0 until s step 8) for (x in 0 until s step 8) { q.color = cols[rnd.nextInt(4)]; c.drawRect(x.toFloat(), y.toFloat(), x + 8f, y + 8f, q) } }
                Mat.GALAXY -> {
                    c.drawColor(0xFF1A0A3A.toInt())
                    q.shader = RadialGradient(20f, 30f, 30f, intArrayOf(0x88B08CFF.toInt(), 0x00000000), null, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, 64f, 64f, q); q.shader = null
                    repeat(40) { q.color = 0xFFFFFFFF.toInt(); q.alpha = 120 + rnd.nextInt(135); c.drawCircle(rnd.nextFloat() * s, rnd.nextFloat() * s, 0.4f + rnd.nextFloat(), q) }
                }
                Mat.LAVA -> {
                    c.drawColor(0xFF2A0E08.toInt()); q.color = 0xFFFF5E2B.toInt(); q.strokeWidth = 1.6f
                    repeat(10) { var x = rnd.nextFloat() * s; var y = rnd.nextFloat() * s; repeat(5) { val nx = x + rnd.nextFloat() * 14 - 7; val ny = y + rnd.nextFloat() * 14 - 7; c.drawLine(x, y, nx, ny, q); x = nx; y = ny } }
                }
                else -> c.drawColor(0xFF808080.toInt())
            }
            b
        }
    }

    private fun rimPath(shape: RimShape, r: Float): Path = Path().apply {
        when (shape) {
            RimShape.ROUND, RimShape.DEEP_DISH -> addCircle(0f, 0f, r, Path.Direction.CW)
            RimShape.FLAT_BOTTOM -> {
                val yb = r * 0.74f; val xb = kotlin.math.sqrt(r * r - yb * yb)
                val start = Math.toDegrees(kotlin.math.atan2(yb.toDouble(), xb.toDouble())).toFloat()
                arcTo(RectF(-r, -r, r, r), start, 360f - (180f - 2 * start) - 0.01f, true)
                lineTo(xb, yb); close()
            }
            RimShape.OCTAGON, RimShape.HEXAGON -> {
                val n = if (shape == RimShape.OCTAGON) 8 else 6
                for (i in 0..n) {
                    val a = Math.PI * 2 * i / n + if (n == 8) Math.PI / 8 else 0.0
                    val x = (cos(a) * r).toFloat(); val y = (sin(a) * r).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            RimShape.YOKE -> {
                moveTo(-r, -r * 0.3f); lineTo(-r, r * 0.3f); quadTo(-r, r * 0.82f, -r * 0.55f, r * 0.82f); lineTo(r * 0.55f, r * 0.82f)
                quadTo(r, r * 0.82f, r, r * 0.3f); lineTo(r, -r * 0.3f)
            }
            RimShape.BUTTERFLY -> {
                moveTo(-r * 0.55f, -r * 0.55f); quadTo(-r * 1.05f, -r * 0.55f, -r, 0f); quadTo(-r * 0.95f, r * 0.8f, -r * 0.4f, r * 0.8f); lineTo(r * 0.4f, r * 0.8f)
                quadTo(r * 0.95f, r * 0.8f, r, 0f); quadTo(r * 1.05f, -r * 0.55f, r * 0.55f, -r * 0.55f); close()
            }
            RimShape.F1 -> addRoundRect(RectF(-r, -r * 0.48f, r, r * 0.6f), r * 0.3f, r * 0.3f, Path.Direction.CW)
        }
    }

    private fun rimShader(d: WheelDesign, r: Float): Shader? = when (d.mat) {
        Mat.CHROME -> SweepGradient(0f, 0f, intArrayOf(0xFF6A7078.toInt(), 0xFFFFFFFF.toInt(), 0xFF8A9099.toInt(), 0xFFE6EAF0.toInt(), 0xFF5A6068.toInt(), 0xFFFFFFFF.toInt(), 0xFF6A7078.toInt()), null)
        Mat.GOLD -> SweepGradient(0f, 0f, intArrayOf(0xFF9A6A10.toInt(), 0xFFFFF1A6.toInt(), 0xFFC9962A.toInt(), 0xFFFFE27A.toInt(), 0xFF8A5A08.toInt(), 0xFFFFF1A6.toInt(), 0xFF9A6A10.toInt()), null)
        Mat.BRASS -> SweepGradient(0f, 0f, intArrayOf(0xFF6A4A1A.toInt(), 0xFFE8C77A.toInt(), 0xFF8A6A2A.toInt(), 0xFFD9B060.toInt(), 0xFF6A4A1A.toInt()), null)
        Mat.HOLO -> SweepGradient(0f, 0f, intArrayOf(0xFFFF6AD5.toInt(), 0xFFC774E8.toInt(), 0xFFAD8CFF.toInt(), 0xFF8795E8.toInt(), 0xFF94D0FF.toInt(), 0xFF9BFFDA.toInt(), 0xFFFFF59B.toInt(), 0xFFFF6AD5.toInt()), null)
        Mat.SUNSET -> SweepGradient(0f, 0f, intArrayOf(0xFFFF7A1A.toInt(), 0xFFFF3DA5.toInt(), 0xFF7B61FF.toInt(), 0xFFFF3DA5.toInt(), 0xFFFF7A1A.toInt()), null)
        Mat.ICE -> RadialGradient(-r * 0.3f, -r * 0.3f, r * 1.6f, intArrayOf(0xFFFFFFFF.toInt(), d.color or 0xFF000000.toInt(), 0xFF6FA8C8.toInt()), null, Shader.TileMode.CLAMP)
        Mat.CARBON, Mat.WOOD, Mat.CAMO, Mat.TIGER, Mat.CHECKER, Mat.PIXEL, Mat.GALAXY, Mat.LAVA ->
            BitmapShader(tile(d.mat), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(Matrix().apply { setScale(r / 90f, r / 90f) }) }
        else -> RadialGradient(-r * 0.35f, -r * 0.4f, r * 1.7f, intArrayOf(Sprites2.lighten(d.color or 0xFF000000.toInt(), if (d.mat == Mat.MATTE) 0.1f else 0.25f), d.color or 0xFF000000.toInt(), Sprites2.darken(d.color or 0xFF000000.toInt(), 0.45f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    /** cx,cy centre; r outer radius (px); rot degrees; a alpha 0..255 */
    fun paint(c: Canvas, d: WheelDesign, cx: Float, cy: Float, r: Float, rot: Float, a: Int, typeface: Typeface?, gearText: String? = null) {
        c.save(); c.translate(cx, cy)
        // soft shadow
        p.reset(); p.isAntiAlias = true; p.color = 0x66000000; p.alpha = (a * 0.45f).toInt()
        c.drawCircle(4f, 7f, r * 0.98f, p)
        c.rotate(rot)
        val rr = r * 0.86f
        val thick = r * if (d.rim == RimShape.F1) 0.0f else 0.2f
        val path = rimPath(d.rim, rr)
        val accent = d.accent or 0xFF000000.toInt()

        // F1 body plate
        if (d.rim == RimShape.F1) {
            p.style = Paint.Style.FILL; p.shader = rimShader(d, rr); p.alpha = a
            c.drawPath(path, p); p.shader = null
            p.color = 0x55000000; p.alpha = (a * 0.6f).toInt(); c.drawRoundRect(RectF(-rr * 0.55f, -rr * 0.3f, rr * 0.55f, rr * 0.42f), rr * 0.12f, rr * 0.12f, p)
            // side grips
            p.color = 0xFF111215.toInt(); p.alpha = a
            for (sx in floatArrayOf(-1f, 1f)) c.drawRoundRect(RectF(sx * rr - rr * 0.24f, -rr * 0.42f, sx * rr + rr * 0.24f, rr * 0.55f).sortX(), rr * 0.2f, rr * 0.2f, p)
        }
        // dish behind spokes
        if (d.spokes == Spokes.SIX_HOLE) {
            val dish = Path().apply { fillType = Path.FillType.EVEN_ODD; addCircle(0f, 0f, rr * 0.78f, Path.Direction.CW)
                for (k in 0 until 6) { val ang = Math.PI * 2 * k / 6 + Math.PI / 6; addCircle((cos(ang) * rr * 0.52f).toFloat(), (sin(ang) * rr * 0.52f).toFloat(), rr * 0.13f, Path.Direction.CW) } }
            p.style = Paint.Style.FILL; p.shader = SweepGradient(0f, 0f, intArrayOf(0xFF3A3F47.toInt(), 0xFF8A9099.toInt(), 0xFF2A2E35.toInt(), 0xFF6A7078.toInt(), 0xFF3A3F47.toInt()), null); p.alpha = a
            c.drawPath(dish, p); p.shader = null
        }
        // spokes
        val spokeCol = when (d.mat) { Mat.CHROME, Mat.GOLD, Mat.BRASS -> null; else -> 0xFF2C313B.toInt() }
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        if (spokeCol == null) { p.shader = rimShader(d, rr) } else { p.shader = LinearGradient(0f, -rr, 0f, rr, intArrayOf(0xFF4A5262.toInt(), spokeCol, 0xFF14171C.toInt()), null, Shader.TileMode.CLAMP) }
        p.alpha = a
        val angles: List<Float> = when (d.spokes) {
            Spokes.TWO -> listOf(0f, 180f); Spokes.THREE -> listOf(0f, 180f, 90f); Spokes.Y -> listOf(30f, 150f, 270f)
            Spokes.FOUR -> listOf(45f, 135f, 225f, 315f); Spokes.BANJO -> (0 until 12).map { it * 30f }; else -> emptyList()
        }
        p.strokeWidth = if (d.spokes == Spokes.BANJO) r * 0.035f else r * 0.15f
        for (ang in angles) {
            val rad = Math.toRadians(ang.toDouble())
            val reach = if (d.rim == RimShape.YOKE || d.rim == RimShape.BUTTERFLY) rr * 0.95f else rr * 0.9f
            c.drawLine((cos(rad) * rr * 0.26f).toFloat(), (sin(rad) * rr * 0.26f).toFloat(), (cos(rad) * reach).toFloat(), (sin(rad) * reach).toFloat(), p)
        }
        p.shader = null
        // rim
        if (d.rim != RimShape.F1) {
            p.strokeWidth = thick; p.strokeJoin = Paint.Join.ROUND; p.alpha = a
            run {
                p.shader = rimShader(d, rr); c.drawPath(path, p); p.shader = null
                if (d.mat != Mat.CHROME && d.mat != Mat.GOLD && d.mat != Mat.BRASS && d.mat != Mat.HOLO) {
                    // soft shading over textures
                    p.shader = LinearGradient(-rr, -rr, rr, rr, intArrayOf(0x40FFFFFF, 0x00000000, 0x66000000), null, Shader.TileMode.CLAMP)
                    c.drawPath(path, p); p.shader = null
                }
                // grip stitching for leather & alcantara
                if (d.mat == Mat.LEATHER || d.mat == Mat.ALCANTARA) {
                    p.color = accent; p.alpha = (a * 0.7f).toInt(); p.strokeWidth = r * 0.012f
                    p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(r * 0.04f, r * 0.03f), 0f)
                    c.drawPath(path, p); p.pathEffect = null
                }
            }
            // inner and outer edge lines
            p.shader = null; p.color = 0x66000000; p.alpha = (a * 0.6f).toInt(); p.strokeWidth = r * 0.012f
            c.drawPath(path, p)
            // deep dish ring
            if (d.rim == RimShape.DEEP_DISH) { p.color = 0x88000000.toInt(); p.strokeWidth = r * 0.03f; c.drawCircle(0f, 0f, rr * 0.82f, p) }
        }
        // top marker
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.BUTT; p.color = accent; p.alpha = a
        when (d.rim) {
            RimShape.YOKE, RimShape.BUTTERFLY, RimShape.F1 -> { p.style = Paint.Style.FILL; c.drawRoundRect(RectF(-rr * 0.1f, -rr * 0.62f, rr * 0.1f, -rr * 0.48f), 4f, 4f, p) }
            else -> { p.strokeWidth = thick * 1.02f; c.drawArc(RectF(-rr, -rr, rr, rr), -97f, 14f, false, p) }
        }
        p.style = Paint.Style.FILL
        // hub
        p.shader = RadialGradient(-r * 0.08f, -r * 0.1f, r * 0.34f, intArrayOf(0xFF4A5262.toInt(), 0xFF181B22.toInt()), null, Shader.TileMode.CLAMP); p.alpha = a
        c.drawCircle(0f, 0f, r * 0.29f, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.02f; p.color = accent; p.alpha = (a * 0.7f).toInt(); c.drawCircle(0f, 0f, r * 0.29f, p); p.style = Paint.Style.FILL
        when (d.hub) {
            Hub.BADGE -> label(c, "IP", r * 0.2f, 0xFFFFFFFF.toInt(), a, typeface)
            Hub.STAR -> {
                val sp = Path(); for (i in 0 until 10) { val rad = if (i % 2 == 0) r * 0.17f else r * 0.07f; val ang = Math.toRadians(-90.0 + i * 36); val x = (cos(ang) * rad).toFloat(); val y = (sin(ang) * rad).toFloat(); if (i == 0) sp.moveTo(x, y) else sp.lineTo(x, y) }
                sp.close(); p.color = accent; p.alpha = a; c.drawPath(sp, p)
            }
            Hub.BUTTONS -> {
                val cols = intArrayOf(0xFFFF4D4D.toInt(), 0xFF19E3FF.toInt(), 0xFF9BFF3C.toInt(), 0xFFFFC93C.toInt())
                for (k in 0 until 4) { p.color = cols[k]; p.alpha = a; c.drawCircle(if (k % 2 == 0) -r * 0.12f else r * 0.12f, if (k < 2) -r * 0.1f else r * 0.1f, r * 0.055f, p) }
            }
            Hub.SCREEN -> {
                p.color = 0xFF05070C.toInt(); p.alpha = a; c.drawRoundRect(RectF(-r * 0.2f, -r * 0.13f, r * 0.2f, r * 0.13f), r * 0.04f, r * 0.04f, p)
                label(c, gearText ?: "D", r * 0.17f, accent, a, typeface)
            }
        }
        c.restore()
    }

    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private fun label(c: Canvas, t: String, size: Float, col: Int, a: Int, tf: Typeface?) {
        tp.typeface = tf; tp.textSize = size; tp.color = col; tp.alpha = a
        c.drawText(t, 0f, size * 0.36f, tp)
    }

    private fun RectF.sortX(): RectF { if (left > right) { val t = left; left = right; right = t }; return this }
}

/** colour helpers (local copy so input doesn't depend on render) */
private object Sprites2 {
    fun lighten(c: Int, f: Float): Int {
        val r = (c shr 16) and 0xff; val g = (c shr 8) and 0xff; val b = c and 0xff
        return (0xff shl 24) or ((r + (255 - r) * f).toInt() shl 16) or ((g + (255 - g) * f).toInt() shl 8) or (b + (255 - b) * f).toInt()
    }
    fun darken(c: Int, f: Float): Int {
        val r = (c shr 16) and 0xff; val g = (c shr 8) and 0xff; val b = c and 0xff
        return (0xff shl 24) or ((r * (1 - f)).toInt() shl 16) or ((g * (1 - f)).toInt() shl 8) or (b * (1 - f)).toInt()
    }
}
