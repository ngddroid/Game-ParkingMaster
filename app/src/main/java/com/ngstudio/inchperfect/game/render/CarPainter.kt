package com.ngstudio.inchperfect.game.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngstudio.inchperfect.game.physics.CarDesign
import com.ngstudio.inchperfect.game.physics.Hood
import com.ngstudio.inchperfect.game.physics.Lamps
import com.ngstudio.inchperfect.game.physics.Rear
import com.ngstudio.inchperfect.game.physics.Roof
import kotlin.math.max
import kotlin.math.min

/**
 * HD top-down car painter. The car is painted centred on the origin with the nose pointing to +x,
 * length [L] and width [W] in pixels. Everything is vector-drawn, so it is sharp at any resolution.
 */
object CarPainter {
    private fun lighten(c: Int, f: Float) = Sprites.lighten(c, f)
    private fun darken(c: Int, f: Float) = Sprites.darken(c, f)
    private fun alpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    fun paint(c: Canvas, look: CarLook, L: Float, W: Float) {
        val d = look.design
        val base = look.color or 0xFF000000.toInt()
        val matte = look.finish == Finish.MATTE
        val chrome = look.finish == Finish.CHROME
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val x0 = -L / 2; val x1 = L / 2; val hw = W / 2
        val xr = x0 + look.axleR * L
        val xf = x0 + look.axleF * L
        val tl = 0.145f * L.coerceAtMost(W * 2.6f)       // tyre length
        val tw = 0.135f * W                              // tyre width
        val ar = d.arch * W
        val bw = hw - ar

        // ------------------------------------------------------------ tyres
        for (wx in floatArrayOf(xf, xr)) for (sy in floatArrayOf(-1f, 1f)) {
            val cy = sy * (hw - tw * 0.42f)
            p.color = 0xFF111215.toInt()
            c.drawRoundRect(RectF(wx - tl / 2, cy - tw / 2, wx + tl / 2, cy + tw / 2), tw * 0.35f, tw * 0.35f, p)
            p.color = 0xFF2A2D33.toInt(); p.strokeWidth = max(1f, tl * 0.05f)
            var k = wx - tl / 2 + tl * 0.12f
            while (k < wx + tl / 2 - tl * 0.08f) { c.drawLine(k, cy - tw * 0.42f, k, cy + tw * 0.42f, p); k += tl * 0.16f }
        }

        // ------------------------------------------------------- body shape
        val al = min(tl * 0.82f, (xr - x0) * 0.9f)                 // arch half length
        val tail = min(d.tailRound * W, max(4f, (xr - al) - x0 - 2f))
        val nose = min(d.noseRound * W, max(4f, x1 - (xf + al) - 2f))
        val waist = d.waist * W
        val a0 = xf + al; val a1 = xf - al; val r0 = xr + al; val r1 = xr - al
        val body = Path().apply {
            moveTo(x1, 0f)
            cubicTo(x1, -bw * 0.62f, x1 - nose * 0.22f, -bw, x1 - nose, -bw)
            lineTo(a0, -bw)
            cubicTo(a0 - al * 0.35f, -hw, a1 + al * 0.35f, -hw, a1, -bw)
            cubicTo(a1 - (a1 - r0) * 0.33f, -bw + waist, r0 + (a1 - r0) * 0.33f, -bw + waist, r0, -bw)
            cubicTo(r0 - al * 0.35f, -hw, r1 + al * 0.35f, -hw, r1, -bw)
            lineTo(x0 + tail, -bw)
            cubicTo(x0 + tail * 0.22f, -bw, x0, -bw * 0.62f, x0, 0f)
            cubicTo(x0, bw * 0.62f, x0 + tail * 0.22f, bw, x0 + tail, bw)
            lineTo(r1, bw)
            cubicTo(r1 + al * 0.35f, hw, r0 - al * 0.35f, hw, r0, bw)
            cubicTo(r0 + (a1 - r0) * 0.33f, bw - waist, a1 - (a1 - r0) * 0.33f, bw - waist, a1, bw)
            cubicTo(a1 + al * 0.35f, hw, a0 - al * 0.35f, hw, a0, bw)
            lineTo(x1 - nose, bw)
            cubicTo(x1 - nose * 0.22f, bw, x1, bw * 0.62f, x1, 0f)
            close()
        }

        // base paint with a "rounded body" cross gradient
        p.color = -1; p.shader = when {
            chrome -> LinearGradient(0f, -hw, 0f, hw, intArrayOf(0xFF3A3F47.toInt(), 0xFFFFFFFF.toInt(), lighten(base, 0.35f), darken(base, 0.35f), 0xFFF2F4F7.toInt(), 0xFF5A6068.toInt(), 0xFF22252B.toInt()),
                floatArrayOf(0f, 0.12f, 0.3f, 0.5f, 0.68f, 0.86f, 1f), Shader.TileMode.CLAMP)
            matte -> LinearGradient(0f, -hw, 0f, hw, intArrayOf(darken(base, 0.35f), darken(base, 0.08f), lighten(base, 0.06f), base, darken(base, 0.1f), darken(base, 0.38f)),
                floatArrayOf(0f, 0.1f, 0.3f, 0.55f, 0.85f, 1f), Shader.TileMode.CLAMP)
            else -> LinearGradient(0f, -hw, 0f, hw, intArrayOf(darken(base, 0.55f), darken(base, 0.12f), lighten(base, 0.3f), base, base, darken(base, 0.2f), darken(base, 0.58f)),
                floatArrayOf(0f, 0.07f, 0.24f, 0.4f, 0.64f, 0.88f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawPath(body, p); p.shader = null

        c.save(); c.clipPath(body)
        // rounded bumpers: darken both ends
        p.color = -1; p.shader = LinearGradient(x0, 0f, x1, 0f, intArrayOf(0x66000000, 0x00000000, 0x00000000, 0x55000000), floatArrayOf(0f, 0.1f, 0.88f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(x0, -hw, x1, hw, p); p.shader = null
        if (look.finish == Finish.PEARL) {
            p.color = -1; p.shader = LinearGradient(x0, -hw, x1, hw, intArrayOf(0x00000000, Sprites.hueShift(base, 50f) and 0x77FFFFFF, 0x00000000, Sprites.hueShift(base, -45f) and 0x66FFFFFF, 0x00000000), null, Shader.TileMode.CLAMP)
            c.drawRect(x0, -hw, x1, hw, p); p.shader = null
        }
        if (look.finish == Finish.METALLIC || look.finish == Finish.PEARL) {
            val rnd = java.util.Random(look.color.toLong() * 31)
            repeat((L * W / 14).toInt()) {
                p.color = if (it % 3 == 0) 0x40FFFFFF else 0x22FFFFFF
                c.drawCircle(x0 + rnd.nextFloat() * L, -hw + rnd.nextFloat() * W, max(0.6f, W * 0.004f), p)
            }
        }
        // factory stripes / wraps under the glass
        if (d.stripes) {
            val sc = if (Sprites.luminance(base) > 0.6) 0xE6111418.toInt() else 0xEEF4F6FA.toInt()
            p.color = sc
            c.drawRect(x0, -W * 0.13f, x1, -W * 0.055f, p); c.drawRect(x0, W * 0.055f, x1, W * 0.13f, p)
        }
        decalUnder(c, look, L, W, p)
        // trim: black bumpers / fender flares
        if (d.blackTrim) {
            p.color = alpha(d.trimColor, 235)
            c.drawRect(x1 - W * 0.075f, -hw, x1, hw, p)
            c.drawRect(x0, -hw, x0 + W * 0.06f, hw, p)
        }
        if (d.chrome) {
            p.color = -1; p.shader = LinearGradient(0f, -hw, 0f, hw, intArrayOf(0xFF8A9099.toInt(), 0xFFFFFFFF.toInt(), 0xFFB8BEC6.toInt(), 0xFF6A7078.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(x1 - W * 0.045f, -hw, x1, hw, p); c.drawRect(x0, -hw, x0 + W * 0.04f, hw, p); p.shader = null
        }
        if (d.flares) {
            p.color = alpha(d.trimColor, 240)
            for (wx in floatArrayOf(xf, xr)) for (sy in floatArrayOf(-1f, 1f)) c.drawOval(RectF(wx - al * 1.05f, sy * hw - W * 0.07f, wx + al * 1.05f, sy * hw + W * 0.07f), p)
        }
        c.restore()

        // outline: lit top edge, shaded bottom edge
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1.2f, W * 0.022f)
        p.color = -1; p.shader = LinearGradient(0f, -hw, 0f, hw, intArrayOf(alpha(lighten(base, 0.5f), 150), 0x33000000, 0x99000000.toInt()), null, Shader.TileMode.CLAMP)
        c.drawPath(body, p); p.shader = null; p.style = Paint.Style.FILL

        // ------------------------------------------------------- cabin layout
        val cabStart = x0 + L * (d.cabinCenter - d.cabinLen / 2)
        val cabEnd = x0 + L * (d.cabinCenter + d.cabinLen / 2)
        val wsStart = cabEnd - L * d.cabinLen * d.windshield
        val rgEnd = cabStart + L * d.cabinLen * d.rearGlass
        val cw = W * d.cabinWidth / 2
        val gw = min(cw * 1.13f, bw * 0.9f)

        // panel lines on the hood / boot
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, W * 0.008f); p.color = 0x38000000
        if (d.hood != Hood.ENGINE_REAR) {
            c.drawLine(cabEnd + L * 0.01f, -gw * 0.92f, x1 - nose * 0.55f, -bw * 0.62f, p)
            c.drawLine(cabEnd + L * 0.01f, gw * 0.92f, x1 - nose * 0.55f, bw * 0.62f, p)
        }
        if (d.rear != Rear.BED) c.drawLine(cabStart - L * 0.012f, -gw * 0.85f, cabStart - L * 0.012f, gw * 0.85f, p)
        // door seams + handles
        val doorA = wsStart - L * 0.01f; val doorB = (wsStart + rgEnd) / 2
        for (dx in floatArrayOf(doorA, doorB)) for (sy in floatArrayOf(-1f, 1f)) c.drawLine(dx, sy * (bw - W * 0.012f), dx, sy * (gw + W * 0.03f), p)
        p.style = Paint.Style.FILL
        p.color = alpha(lighten(base, 0.45f), 200)
        for (dx in floatArrayOf(doorA - L * 0.05f, doorB - L * 0.05f)) for (sy in floatArrayOf(-1f, 1f))
            c.drawRoundRect(RectF(dx - L * 0.02f, sy * (bw - W * 0.04f) - W * 0.012f, dx, sy * (bw - W * 0.04f) + W * 0.012f), 2f, 2f, p)

        // hood features (before the glass)
        when (d.hood) {
            Hood.SCOOP -> {
                val sx0 = cabEnd + (x1 - cabEnd) * 0.25f; val sx1 = cabEnd + (x1 - cabEnd) * 0.62f
                p.color = -1; p.shader = LinearGradient(sx0, 0f, sx1, 0f, intArrayOf(darken(base, 0.25f), lighten(base, 0.15f)), null, Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(sx0, -W * 0.13f, sx1, W * 0.13f), W * 0.04f, W * 0.04f, p); p.shader = null
                p.color = 0xFF0C0D10.toInt(); c.drawRoundRect(RectF(sx1 - (sx1 - sx0) * 0.3f, -W * 0.09f, sx1 - W * 0.01f, W * 0.09f), W * 0.02f, W * 0.02f, p)
            }
            Hood.VENTS -> {
                p.color = 0xFF0E1013.toInt()
                for (sy in floatArrayOf(-1f, 1f)) for (k in 0 until 3) {
                    val vx = cabEnd + (x1 - cabEnd) * (0.28f + k * 0.09f)
                    c.drawRoundRect(RectF(vx, sy * W * 0.22f - W * 0.03f, vx + L * 0.025f, sy * W * 0.22f + W * 0.03f), 2f, 2f, p)
                }
            }
            Hood.BULGE -> {
                p.color = -1; p.shader = LinearGradient(0f, -W * 0.16f, 0f, W * 0.16f, intArrayOf(0x00000000, alpha(lighten(base, 0.4f), 110), 0x00000000, 0x33000000), floatArrayOf(0f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(cabEnd + L * 0.02f, -W * 0.16f, x1 - nose * 0.5f, W * 0.16f), W * 0.08f, W * 0.08f, p); p.shader = null
            }
            Hood.ENGINE_REAR -> {
                // louvred engine cover behind the cabin
                val e0 = x0 + L * 0.1f; val e1 = cabStart - L * 0.01f
                p.color = 0xFF0E1013.toInt(); c.drawRoundRect(RectF(e0, -gw * 0.72f, e1, gw * 0.72f), W * 0.05f, W * 0.05f, p)
                p.color = alpha(lighten(base, 0.1f), 255)
                var lx = e0 + L * 0.02f
                while (lx < e1 - L * 0.015f) { c.drawRect(lx, -gw * 0.66f, lx + L * 0.012f, gw * 0.66f, p); lx += L * 0.028f }
            }
            Hood.PLAIN -> {}
        }
        if (d.sideIntakes) {
            p.color = 0xFF0C0D10.toInt()
            for (sy in floatArrayOf(-1f, 1f)) {
                val ip = Path().apply { moveTo(r0 + L * 0.01f, sy * (bw - W * 0.01f)); lineTo(r0 + L * 0.13f, sy * (bw - W * 0.015f)); lineTo(r0 + L * 0.06f, sy * (bw - W * 0.12f)); close() }
                c.drawPath(ip, p)
            }
        }

        // ------------------------------------------------------------ glass
        val green = Path().apply { addRoundRect(RectF(cabStart, -gw, cabEnd, gw), W * 0.16f, W * 0.16f, Path.Direction.CW) }
        if (d.roof == Roof.OPEN) {
            openCockpit(c, d, base, cabStart, cabEnd, wsStart, gw, cw, L, W, p)
        } else {
            glass(c, green, cabStart, cabEnd, W, p)
            // windshield (convex front edge)
            val ws = Path().apply {
                moveTo(wsStart, -cw); lineTo(cabEnd - L * 0.012f, -gw * 0.98f)
                quadTo(cabEnd + L * 0.022f, 0f, cabEnd - L * 0.012f, gw * 0.98f); lineTo(wsStart, cw); close()
            }
            glass(c, ws, wsStart, cabEnd, W, p, strong = true)
            // dashboard + steering wheel seen through the windshield
            p.color = 0x99101218.toInt(); c.drawRect(cabEnd - L * 0.05f, -gw * 0.8f, cabEnd - L * 0.022f, gw * 0.8f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, W * 0.018f); p.color = 0xCC1C1F25.toInt()
            c.drawCircle(wsStart + (cabEnd - wsStart) * 0.42f, -cw * 0.45f, W * 0.075f, p); p.style = Paint.Style.FILL
            // rear glass with defroster lines
            if (d.rearGlass > 0.02f) {
                val rg = Path().apply {
                    moveTo(rgEnd, -cw); lineTo(cabStart + L * 0.01f, -gw * 0.9f)
                    quadTo(cabStart - L * 0.012f, 0f, cabStart + L * 0.01f, gw * 0.9f); lineTo(rgEnd, cw); close()
                }
                glass(c, rg, cabStart, rgEnd, W, p)
                p.color = 0x40B8542A; p.strokeWidth = max(0.8f, W * 0.005f)
                var lx = cabStart + L * 0.015f
                while (lx < rgEnd - L * 0.01f) { c.drawLine(lx, -cw * 0.8f, lx, cw * 0.8f, p); lx += L * 0.012f }
            }
            // roof
            if (d.roof != Roof.CANOPY) {
                val roofCol = if (d.roofColor != 0) d.roofColor or 0xFF000000.toInt() else base
                val roof = RectF(rgEnd - L * 0.004f, -cw, wsStart + L * 0.004f, cw)
                p.color = -1; p.shader = LinearGradient(0f, -cw, 0f, cw, intArrayOf(lighten(roofCol, if (matte) 0.08f else 0.32f), roofCol, darken(roofCol, 0.22f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                c.drawRoundRect(roof, W * 0.1f, W * 0.1f, p); p.shader = null
                if (!matte) {
                    p.color = -1; p.shader = RadialGradient(roof.centerX() + roof.width() * 0.1f, -cw * 0.35f, roof.width() * 0.55f, intArrayOf(0x40FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                    c.drawRoundRect(roof, W * 0.1f, W * 0.1f, p); p.shader = null
                }
                if (d.roof == Roof.VINYL) {
                    p.color = 0x22FFFFFF
                    var k = roof.left + 3; while (k < roof.right) { c.drawLine(k, roof.top + 2, k, roof.bottom - 2, p); k += max(3f, L * 0.01f) }
                }
                when (d.roof) {
                    Roof.SUNROOF -> glass(c, Path().apply { addRoundRect(RectF(roof.centerX() - roof.width() * 0.08f, -cw * 0.55f, roof.right - roof.width() * 0.12f, cw * 0.55f), W * 0.04f, W * 0.04f, Path.Direction.CW) }, roof.centerX(), roof.right, W, p, strong = true)
                    Roof.PANORAMA -> {
                        glass(c, Path().apply { addRoundRect(RectF(roof.left + roof.width() * 0.08f, -cw * 0.78f, roof.right - roof.width() * 0.06f, cw * 0.78f), W * 0.06f, W * 0.06f, Path.Direction.CW) }, roof.left, roof.right, W, p, strong = true)
                        p.color = roofCol; c.drawRect(roof.centerX() - L * 0.008f, -cw * 0.8f, roof.centerX() + L * 0.008f, cw * 0.8f, p)
                    }
                    Roof.TARGA -> glass(c, Path().apply { addRoundRect(RectF(roof.left, -cw * 0.85f, roof.centerX() - roof.width() * 0.05f, cw * 0.85f), W * 0.05f, W * 0.05f, Path.Direction.CW) }, roof.left, roof.centerX(), W, p)
                    else -> {}
                }
                // pillars between windshield and side glass
                p.color = darken(roofCol, 0.05f); p.strokeWidth = max(1.5f, W * 0.03f); p.strokeCap = Paint.Cap.ROUND
                c.drawLine(wsStart, -cw, cabEnd - L * 0.02f, -gw * 0.95f, p); c.drawLine(wsStart, cw, cabEnd - L * 0.02f, gw * 0.95f, p)
                if (d.rearGlass > 0.02f) { c.drawLine(rgEnd, -cw, cabStart + L * 0.012f, -gw * 0.88f, p); c.drawLine(rgEnd, cw, cabStart + L * 0.012f, gw * 0.88f, p) }
                val bx = (rgEnd + wsStart) / 2
                c.drawLine(bx, -cw, bx, -gw, p); c.drawLine(bx, cw, bx, gw, p)
                p.strokeCap = Paint.Cap.BUTT
                if (d.roofRails) {
                    p.color = 0xFF2A2D33.toInt(); p.strokeWidth = max(1.5f, W * 0.035f)
                    c.drawLine(roof.left + L * 0.01f, -cw * 0.86f, roof.right - L * 0.01f, -cw * 0.86f, p)
                    c.drawLine(roof.left + L * 0.01f, cw * 0.86f, roof.right - L * 0.01f, cw * 0.86f, p)
                    p.strokeWidth = max(1f, W * 0.02f)
                    for (k in 1..2) { val rx = roof.left + roof.width() * k / 3; c.drawLine(rx, -cw * 0.86f, rx, cw * 0.86f, p) }
                }
                if (d.taxi) {
                    val sx = roof.centerX()
                    p.color = 0xFF1A1C21.toInt(); c.drawRoundRect(RectF(sx - L * 0.035f, -W * 0.16f, sx + L * 0.035f, W * 0.16f), W * 0.04f, W * 0.04f, p)
                    p.color = -1; p.shader = LinearGradient(sx - L * 0.03f, 0f, sx + L * 0.03f, 0f, intArrayOf(0xFFFFF4C2.toInt(), 0xFFFFD84A.toInt()), null, Shader.TileMode.CLAMP)
                    c.drawRoundRect(RectF(sx - L * 0.028f, -W * 0.13f, sx + L * 0.028f, W * 0.13f), W * 0.03f, W * 0.03f, p); p.shader = null
                }
                // shark-fin antenna
                p.color = 0xFF16181C.toInt(); c.drawRoundRect(RectF(roof.left + L * 0.012f, -W * 0.022f, roof.left + L * 0.045f, W * 0.022f), 3f, 3f, p)
            } else {
                // canopy: one big glass bubble with a light spine
                p.color = 0x33FFFFFF; c.drawRect(rgEnd, -W * 0.012f, wsStart, W * 0.012f, p)
            }
        }

        // ------------------------------------------------------------ rear
        when (d.rear) {
            Rear.LIP -> { p.color = 0xFF16181C.toInt(); c.drawRoundRect(RectF(x0 + W * 0.015f, -bw * 0.82f, x0 + W * 0.06f, bw * 0.82f), W * 0.02f, W * 0.02f, p) }
            Rear.WING -> {
                val wx0 = x0 - W * 0.01f; val wx1 = x0 + L * 0.075f
                p.color = 0x55000000; c.drawRoundRect(RectF(wx0 + 3, -hw * 0.98f + 3, wx1 + 3, hw * 0.98f + 3), W * 0.03f, W * 0.03f, p)
                p.color = -1; p.shader = LinearGradient(wx0, 0f, wx1, 0f, intArrayOf(0xFF0E1013.toInt(), 0xFF2C3038.toInt(), 0xFF16181C.toInt()), null, Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(wx0, -hw * 0.98f, wx1, hw * 0.98f), W * 0.03f, W * 0.03f, p); p.shader = null
                p.color = alpha(base, 255)
                c.drawRect(wx0, -hw * 0.98f, wx1, -hw * 0.86f, p); c.drawRect(wx0, hw * 0.86f, wx1, hw * 0.98f, p)
            }
            Rear.DUCKTAIL -> {
                p.color = -1; p.shader = LinearGradient(x0, 0f, x0 + L * 0.07f, 0f, intArrayOf(lighten(base, 0.35f), base), null, Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(x0 + W * 0.01f, -bw * 0.85f, x0 + L * 0.07f, bw * 0.85f), W * 0.05f, W * 0.05f, p); p.shader = null
            }
            Rear.BED -> {
                val b0 = x0 + L * 0.03f; val b1 = cabStart - L * 0.02f
                p.color = 0xFF1C1E23.toInt(); c.drawRoundRect(RectF(b0, -bw * 0.82f, b1, bw * 0.82f), W * 0.04f, W * 0.04f, p)
                p.color = 0xFF2C2F36.toInt()
                for (k in 0 until 6) { val yy = -bw * 0.7f + bw * 1.4f * k / 5; c.drawRect(b0 + 3, yy - W * 0.012f, b1 - 3, yy + W * 0.012f, p) }
                p.color = 0xFF24262C.toInt()
                c.drawRoundRect(RectF(xr - al * 0.8f, -bw * 0.82f, xr + al * 0.8f, -bw * 0.55f), W * 0.03f, W * 0.03f, p)
                c.drawRoundRect(RectF(xr - al * 0.8f, bw * 0.55f, xr + al * 0.8f, bw * 0.82f), W * 0.03f, W * 0.03f, p)
            }
            Rear.SPARE -> {
                p.color = 0xFF111215.toInt(); c.drawCircle(x0 + W * 0.02f, 0f, W * 0.2f, p)
                p.color = 0xFF3A3E46.toInt(); c.drawCircle(x0 + W * 0.02f, 0f, W * 0.12f, p)
                p.color = alpha(base, 255); c.drawCircle(x0 + W * 0.02f, 0f, W * 0.06f, p)
            }
            Rear.BOXY -> {
                p.color = 0x33000000; p.strokeWidth = max(1f, W * 0.01f)
                c.drawLine(x0 + W * 0.02f, 0f, x0 + L * 0.06f, 0f, p)
                p.color = 0xFF2A2D33.toInt()
                c.drawRoundRect(RectF(cabStart + L * 0.06f, -W * 0.08f, cabStart + L * 0.12f, W * 0.08f), 3f, 3f, p)
            }
            Rear.PLAIN -> {}
        }

        // ---------------------------------------------------------- mirrors
        for (sy in floatArrayOf(-1f, 1f)) {
            val mx = cabEnd - L * 0.03f
            p.color = 0xFF16181C.toInt(); c.drawRect(mx - L * 0.012f, sy * gw, mx + L * 0.005f, sy * (bw + W * 0.02f), p)
            p.color = -1; p.shader = LinearGradient(0f, sy * bw, 0f, sy * (bw + W * 0.09f), intArrayOf(lighten(base, 0.2f), darken(base, 0.3f)), null, Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(mx - L * 0.03f, min(sy * bw, sy * (bw + W * 0.085f)), mx + L * 0.008f, max(sy * bw, sy * (bw + W * 0.085f))), W * 0.03f, W * 0.03f, p); p.shader = null
        }

        lamps(c, d, L, W, bw, nose, tail, p)
        decalOver(c, look, (rgEnd + wsStart) / 2, cw, L, W, p)

        // clear-coat sparkle highlights (gloss finishes)
        if (!matte) {
            p.color = -1; p.shader = RadialGradient(cabEnd + (x1 - cabEnd) * 0.45f, -bw * 0.35f, W * 0.42f, intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.save(); c.clipPath(body); c.drawCircle(cabEnd + (x1 - cabEnd) * 0.45f, -bw * 0.35f, W * 0.42f, p); c.restore(); p.shader = null
        }
    }

    private fun glass(c: Canvas, path: Path, xa: Float, xb: Float, W: Float, p: Paint, strong: Boolean = false) {
        p.color = -1; p.shader = LinearGradient(0f, -W / 2, 0f, W / 2, intArrayOf(0xFF3E5675.toInt(), 0xFF15203A.toInt(), 0xFF0C1222.toInt(), 0xFF1B2944.toInt()), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, p); p.shader = null
        c.save(); c.clipPath(path)
        val m = (xa + xb) / 2; val span = kotlin.math.abs(xb - xa)
        p.color = if (strong) 0x55FFFFFF else 0x38FFFFFF
        c.drawPath(Path().apply { moveTo(m - span * 0.55f, -W / 2); lineTo(m - span * 0.18f, -W / 2); lineTo(m + span * 0.1f, W / 2); lineTo(m - span * 0.27f, W / 2); close() }, p)
        p.color = if (strong) 0x26FFFFFF else 0x18FFFFFF
        c.drawPath(Path().apply { moveTo(m + span * 0.05f, -W / 2); lineTo(m + span * 0.16f, -W / 2); lineTo(m + span * 0.44f, W / 2); lineTo(m + span * 0.33f, W / 2); close() }, p)
        c.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, W * 0.008f); p.color = 0x40FFFFFF
        c.drawPath(path, p); p.style = Paint.Style.FILL
    }

    private fun openCockpit(c: Canvas, d: CarDesign, base: Int, cabStart: Float, cabEnd: Float, wsStart: Float, gw: Float, cw: Float, L: Float, W: Float, p: Paint) {
        // interior tub
        p.color = 0xFF1E2026.toInt(); c.drawRoundRect(RectF(cabStart, -gw, cabEnd - L * 0.03f, gw), W * 0.12f, W * 0.12f, p)
        // seats
        val seat = 0xFF2E1F1A.toInt()
        for (sy in floatArrayOf(-1f, 1f)) {
            p.color = -1; p.shader = LinearGradient(cabStart, 0f, cabStart + L * 0.15f, 0f, intArrayOf(darken(seat, 0.2f), lighten(seat, 0.25f)), null, Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(cabStart + L * 0.03f, sy * gw * 0.5f - W * 0.13f, cabStart + L * 0.15f, sy * gw * 0.5f + W * 0.13f), W * 0.06f, W * 0.06f, p); p.shader = null
            p.color = 0x55FFFFFF; c.drawLine(cabStart + L * 0.06f, sy * gw * 0.5f - W * 0.1f, cabStart + L * 0.06f, sy * gw * 0.5f + W * 0.1f, p)
        }
        // console + steering wheel
        p.color = 0xFF121418.toInt(); c.drawRect(cabStart + L * 0.06f, -W * 0.03f, cabEnd - L * 0.06f, W * 0.03f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1.2f, W * 0.022f); p.color = 0xFF0C0D10.toInt()
        c.drawCircle(cabStart + L * 0.2f, -gw * 0.5f, W * 0.085f, p); p.style = Paint.Style.FILL
        // roll hoops
        p.color = darken(base, 0.1f)
        for (sy in floatArrayOf(-1f, 1f)) c.drawRoundRect(RectF(cabStart - L * 0.01f, sy * gw * 0.5f - W * 0.12f, cabStart + L * 0.02f, sy * gw * 0.5f + W * 0.12f), W * 0.04f, W * 0.04f, p)
        // short windscreen
        val ws = Path().apply { moveTo(cabEnd - L * 0.05f, -gw * 0.95f); lineTo(cabEnd - L * 0.012f, -gw * 0.98f); quadTo(cabEnd + L * 0.012f, 0f, cabEnd - L * 0.012f, gw * 0.98f); lineTo(cabEnd - L * 0.05f, gw * 0.95f); close() }
        glass(c, ws, cabEnd - L * 0.05f, cabEnd, W, p, strong = true)
    }

    private fun lamps(c: Canvas, d: CarDesign, L: Float, W: Float, bw: Float, nose: Float, tail: Float, p: Paint) {
        val x0 = -L / 2; val x1 = L / 2
        val head = Paint(Paint.ANTI_ALIAS_FLAG)
        head.shader = LinearGradient(x1 - W * 0.12f, 0f, x1, 0f, intArrayOf(0xFFB9C6D8.toInt(), 0xFFF7FBFF.toInt()), null, Shader.TileMode.CLAMP)
        val tailP = Paint(Paint.ANTI_ALIAS_FLAG)
        tailP.shader = LinearGradient(x0, 0f, x0 + W * 0.1f, 0f, intArrayOf(0xFFFF2A3A.toInt(), 0xFF8A0E18.toInt()), null, Shader.TileMode.CLAMP)
        val amber = 0xFFFFA62B.toInt()
        val inset = max(nose * 0.3f, W * 0.02f)
        when (d.lamps) {
            Lamps.ROUND, Lamps.QUAD -> {
                for (sy in floatArrayOf(-1f, 1f)) {
                    val cx = x1 - inset - W * 0.05f; val cy = sy * bw * 0.62f
                    p.color = 0xFF6A7078.toInt(); c.drawCircle(cx, cy, W * 0.085f, p)
                    c.drawCircle(cx, cy, W * 0.07f, head)
                    if (d.lamps == Lamps.QUAD) { p.color = 0xFF6A7078.toInt(); c.drawCircle(cx - W * 0.01f, cy - sy * W * 0.16f, W * 0.06f, p); c.drawCircle(cx - W * 0.01f, cy - sy * W * 0.16f, W * 0.048f, head) }
                    c.drawCircle(x0 + max(tail * 0.3f, W * 0.03f) + W * 0.04f, sy * bw * 0.62f, W * 0.07f, tailP)
                }
            }
            Lamps.BAR -> {
                c.drawRoundRect(RectF(x1 - inset - W * 0.05f, -bw * 0.78f, x1 - inset - W * 0.015f, bw * 0.78f), W * 0.02f, W * 0.02f, head)
                c.drawRoundRect(RectF(x0 + max(tail * 0.3f, W * 0.02f), -bw * 0.82f, x0 + max(tail * 0.3f, W * 0.02f) + W * 0.04f, bw * 0.82f), W * 0.02f, W * 0.02f, tailP)
            }
            Lamps.SLIM, Lamps.ANGULAR -> {
                for (sy in floatArrayOf(-1f, 1f)) {
                    val hp = Path()
                    if (d.lamps == Lamps.SLIM) hp.addRoundRect(RectF(x1 - inset - W * 0.07f, sy * bw * 0.8f - W * 0.07f, x1 - inset, sy * bw * 0.8f + W * 0.07f).sortY(), W * 0.03f, W * 0.03f, Path.Direction.CW)
                    else { hp.moveTo(x1 - inset, sy * bw * 0.45f); hp.lineTo(x1 - inset - W * 0.03f, sy * bw * 0.9f); hp.lineTo(x1 - inset - W * 0.13f, sy * bw * 0.86f); hp.close() }
                    c.drawPath(hp, head)
                    p.color = 0xFFFFFFFF.toInt(); p.strokeWidth = max(1f, W * 0.012f)
                    c.drawLine(x1 - inset - W * 0.02f, sy * bw * 0.45f, x1 - inset - W * 0.02f, sy * bw * 0.86f, p)
                    val tp = Path()
                    tp.addRoundRect(RectF(x0 + max(tail * 0.3f, W * 0.02f), sy * bw * 0.84f - W * 0.09f, x0 + max(tail * 0.3f, W * 0.02f) + W * 0.05f, sy * bw * 0.84f + W * 0.09f).sortY(), W * 0.02f, W * 0.02f, Path.Direction.CW)
                    c.drawPath(tp, tailP)
                }
            }
        }
        if (d.lightBar) {
            p.color = 0xFFE8F4FF.toInt(); c.drawRect(x1 - inset - W * 0.012f, -bw * 0.5f, x1 - inset, bw * 0.5f, p)
            p.color = 0xFFFF2A3A.toInt(); c.drawRect(x0 + max(tail * 0.3f, W * 0.02f), -bw * 0.6f, x0 + max(tail * 0.3f, W * 0.02f) + W * 0.015f, bw * 0.6f, p)
        }
        // amber corner indicators
        p.color = amber
        for (sy in floatArrayOf(-1f, 1f)) c.drawCircle(x1 - nose * 0.55f, sy * bw * 0.9f, W * 0.022f, p)
        // grille for sedans/suvs
        if (d.lamps != Lamps.BAR && d.cabinCenter < 0.5f && d.roof != Roof.CANOPY) {
            p.color = 0xCC0E1013.toInt()
            c.drawRoundRect(RectF(x1 - W * 0.045f, -bw * 0.32f, x1 - W * 0.012f, bw * 0.32f), W * 0.015f, W * 0.015f, p)
        }
    }

    private fun RectF.sortY(): RectF { if (top > bottom) { val t = top; top = bottom; bottom = t }; return this }

    private fun decalUnder(c: Canvas, look: CarLook, L: Float, W: Float, p: Paint) {
        val x0 = -L / 2; val x1 = L / 2
        val acc = look.accent or 0xFF000000.toInt()
        when (look.decal) {
            Decal.STRIPES -> { p.color = alpha(acc, 235); c.drawRect(x0, -W * 0.14f, x1, -W * 0.06f, p); c.drawRect(x0, W * 0.06f, x1, W * 0.14f, p) }
            Decal.TWO_TONE -> { p.color = darken(acc, 0.05f); c.drawRect(x0, -W * 0.22f, x1, W * 0.22f, p) }
            Decal.BOLT -> {
                p.color = acc
                c.drawPath(Path().apply {
                    moveTo(x1 - L * 0.07f, -W * 0.06f); lineTo(x1 - L * 0.21f, W * 0.08f); lineTo(x1 - L * 0.19f, -W * 0.01f)
                    lineTo(x1 - L * 0.31f, W * 0.05f); lineTo(x1 - L * 0.17f, -W * 0.13f); lineTo(x1 - L * 0.19f, -W * 0.03f); close()
                }, p)
            }
            Decal.CARBON -> {
                p.color = 0xFF1A1C21.toInt(); c.drawRect(x1 - L * 0.3f, -W * 0.36f, x1 - L * 0.06f, W * 0.36f, p)
                p.color = 0x1FFFFFFF
                var yy = -W * 0.36f
                while (yy < W * 0.36f) { c.drawLine(x1 - L * 0.3f, yy, x1 - L * 0.06f, yy + W * 0.05f, p); yy += W * 0.035f }
            }
            else -> {}
        }
    }

    private fun decalOver(c: Canvas, look: CarLook, roofCx: Float, cw: Float, L: Float, W: Float, p: Paint) {
        when (look.decal) {
            Decal.CHECKER -> {
                val n = 6; val rw = L * 0.16f; val left = roofCx - rw / 2; val cwid = rw / n; val ch = cw * 2 / 3
                for (i in 0 until n) for (j in 0 until 3) {
                    p.color = if ((i + j) % 2 == 0) 0xEEFFFFFF.toInt() else 0xEE111111.toInt()
                    c.drawRect(left + i * cwid, -cw + j * ch, left + (i + 1) * cwid, -cw + (j + 1) * ch, p)
                }
            }
            Decal.NUMBER -> {
                val r = cw * 0.7f
                p.color = 0xFFFFFFFF.toInt(); c.drawCircle(roofCx, 0f, r, p)
                p.color = 0xFF111111.toInt(); p.textSize = r * 1.25f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
                c.save(); c.rotate(90f, roofCx, 0f); c.drawText(look.number.toString(), roofCx, r * 0.44f, p); c.restore()
                p.isFakeBoldText = false
            }
            else -> {}
        }
    }

    @Suppress("unused") private fun unused(x: Int) = Color.red(x)
}
