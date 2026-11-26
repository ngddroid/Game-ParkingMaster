package com.ngstudio.inchperfect.game.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.ngstudio.inchperfect.game.GameSession
import com.ngstudio.inchperfect.game.SessionState
import com.ngstudio.inchperfect.game.level.Level
import com.ngstudio.inchperfect.game.level.Lighting
import com.ngstudio.inchperfect.game.level.MarkKind
import com.ngstudio.inchperfect.game.level.ObKind
import com.ngstudio.inchperfect.game.level.Obstacle
import com.ngstudio.inchperfect.game.level.PropKind
import com.ngstudio.inchperfect.game.level.Weather
import com.ngstudio.inchperfect.game.level.ZoneKind
import com.ngstudio.inchperfect.game.math.Box
import com.ngstudio.inchperfect.game.math.Disc
import com.ngstudio.inchperfect.game.physics.CarSpec
import com.ngstudio.inchperfect.game.physics.Cars
import com.ngstudio.inchperfect.game.sim.World
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

enum class Quality(val ppm: Float, val shadows: Boolean, val lights: Boolean, val particles: Boolean, val extrude: Boolean) {
    LOW(40f, false, true, false, false), MEDIUM(52f, true, true, true, true), HIGH(64f, true, true, true, true), ULTRA(80f, true, true, true, true)
}

/**
 * Draws one frame of the playfield: textured ground, markings, 2.5D extruded walls and buildings,
 * sprite cars with soft shadows, animated bay, lighting (night light-map), weather and particles.
 */
class Renderer(private val display: Typeface, var quality: Quality, var skin: WorldSkin) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val mtx = Matrix()
    private val corners = DoubleArray(8)
    private val vis = DoubleArray(4)
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textAlign = Paint.Align.CENTER }

    private var level: Level? = null
    private lateinit var look: DistrictLook
    private var groundShader: Shader? = null
    private var outsideShader: Shader? = null
    private val zoneShaders = HashMap<ZoneKind, Shader>()
    private var sidewalkShader: Shader? = null
    private var hedgeShader: Shader? = null
    private var roofShader: Shader? = null
    private val lightMtx = Matrix()
    private val wearPath = Path()
    private val wearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private var time = 0f
    private var rainDrops = FloatArray(0)
    private var snowFlakes = FloatArray(0)
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); isAntiAlias = true }
    private val addPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }

    // sun direction for shadows (metres of offset per metre of height)
    private var sunX = 0.35f; private var sunY = 0.55f

    /** Must be called before the first frame of a level (off the main thread). */
    fun prepare(l: Level) {
        level = l
        look = Looks.of(l.district).let { lk -> if (skin == WorldSkin.AUTUMN) Looks.autumn(lk) else lk }
        Textures.warm(neededTextures(l, skin))
        val ppm = quality.ppm
        groundShader = ground(look.groundKind, look.asphalt, 7)
        outsideShader = ground(look.outsideKind, look.outside, 3)
        zoneShaders.clear()
        for (z in l.zones) if (!zoneShaders.containsKey(z.kind)) zoneShaders[z.kind] = zoneShader(z.kind)
        sidewalkShader = ground(GroundKind.PAVERS, look.sidewalk, 21)
        hedgeShader = ground(GroundKind.GRASS, look.hedge, 5)
        roofShader = ground(GroundKind.CONCRETE, 0x8A8F96, 13)
        when (l.lighting) {
            Lighting.DAY -> { sunX = 0.32f; sunY = 0.5f }
            Lighting.GOLDEN -> { sunX = 0.9f; sunY = 0.45f }
            Lighting.DUSK -> { sunX = 1.1f; sunY = 0.2f }
            Lighting.NIGHT -> { sunX = 0.15f; sunY = 0.25f }
        }
        wearPath.reset()
        val rp = l.refPath
        for (i in 0 until rp.size / 2) { if (i == 0) wearPath.moveTo(rp[0], rp[1]) else wearPath.lineTo(rp[i * 2], rp[i * 2 + 1]) }
        val rnd = java.util.Random(l.number.toLong())
        rainDrops = FloatArray(if (l.weather == Weather.RAIN) 140 * 3 else 0) { rnd.nextFloat() }
        snowFlakes = FloatArray(if (l.weather == Weather.SNOW) 160 * 3 else 0) { rnd.nextFloat() }
        // warm the sprite cache
        Sprites.cone(ppm)
        for (o in l.obstacles) if (o.kind == ObKind.CAR) npcBitmap(o)
    }

    companion object Needs {
        /** every ground texture a level will use (so they can be built in parallel / pre-warmed) */
        fun neededTextures(l: Level, skin: WorldSkin = WorldSkin.CLASSIC): List<Triple<GroundKind, Int, Int>> {
            val lk = Looks.of(l.district).let { if (skin == WorldSkin.AUTUMN) Looks.autumn(it) else it }
            val out = arrayListOf(Triple(lk.groundKind, lk.asphalt, 7), Triple(lk.outsideKind, lk.outside, 3), Triple(GroundKind.PAVERS, lk.sidewalk, 21),
                Triple(GroundKind.GRASS, lk.hedge, 5), Triple(GroundKind.CONCRETE, 0x8A8F96, 13))
            for (z in l.zones) out.add(when (z.kind) {
                ZoneKind.ROAD -> Triple(GroundKind.ASPHALT, lk.road, 17); ZoneKind.LOT -> Triple(GroundKind.ASPHALT, lk.lot, 19)
                ZoneKind.CONCRETE -> Triple(GroundKind.CONCRETE, 0x8E939A, 23); ZoneKind.GRASS -> Triple(GroundKind.GRASS, 0x4F8A3C, 25)
                ZoneKind.ICE -> Triple(GroundKind.SNOW, 0xBFE3F7, 27); ZoneKind.SAND -> Triple(GroundKind.SAND, 0xD9B77A, 29)
                ZoneKind.EPOXY -> Triple(GroundKind.EPOXY, 0x5E646C, 31); ZoneKind.WOOD -> Triple(GroundKind.WOOD, 0x8A6A4A, 33)
                ZoneKind.PLATE -> Triple(GroundKind.PLATE, 0x6E747C, 35)
            })
            return out.distinct()
        }
    }

    private fun ground(kind: GroundKind, color: Int, seed: Int): Shader {
        val tex = Textures.get(kind, color, seed)
        val s = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val m = Matrix(); m.setScale((Textures.TILE_M / Textures.SIZE).toFloat(), (Textures.TILE_M / Textures.SIZE).toFloat())
        s.setLocalMatrix(m)
        if (kind == GroundKind.WATER || quality == Quality.LOW) return s
        val macro = BitmapShader(Textures.macro(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val mm = Matrix(); mm.setScale(48f / 128f, 48f / 128f); macro.setLocalMatrix(mm)
        return ComposeShader(s, macro, PorterDuff.Mode.SRC_OVER)
    }

    private fun zoneShader(k: ZoneKind): Shader = when (k) {
        ZoneKind.ROAD -> ground(GroundKind.ASPHALT, look.road, 17)
        ZoneKind.LOT -> ground(GroundKind.ASPHALT, look.lot, 19)
        ZoneKind.CONCRETE -> ground(GroundKind.CONCRETE, 0x8E939A, 23)
        ZoneKind.GRASS -> ground(GroundKind.GRASS, 0x4F8A3C, 25)
        ZoneKind.ICE -> ground(GroundKind.SNOW, 0xBFE3F7, 27)
        ZoneKind.SAND -> ground(GroundKind.SAND, 0xD9B77A, 29)
        ZoneKind.EPOXY -> ground(GroundKind.EPOXY, 0x5E646C, 31)
        ZoneKind.WOOD -> ground(GroundKind.WOOD, 0x8A6A4A, 33)
        ZoneKind.PLATE -> ground(GroundKind.PLATE, 0x6E747C, 35)
    }

    private fun skinColor(c: Int): Int = when (skin) {
        WorldSkin.TOY -> Sprites.lighten(Sprites.hueShift(c, 0f), 0.15f)
        WorldSkin.GOLDEN -> Sprites.lighten(c, 0.02f)
        else -> c
    }

    private var pixelBmp: Bitmap? = null
    private val pixelPaint = Paint().apply { isFilterBitmap = false }

    // =========================================================== frame
    fun draw(c: Canvas, s: GameSession, dt: Float) {
        if (skin == WorldSkin.PIXEL) {
            val cam = s.camera
            val w = (cam.viewW / 4).toInt().coerceAtLeast(1); val h = (cam.viewH / 4).toInt().coerceAtLeast(1)
            val bmp = pixelBmp?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { pixelBmp = it }
            val pc = Canvas(bmp)
            pc.save(); pc.scale(0.25f, 0.25f); drawFrame(pc, s, dt); pc.restore()
            c.drawBitmap(bmp, null, RectF(0f, 0f, cam.viewW, cam.viewH), pixelPaint)
            return
        }
        drawFrame(c, s, dt)
    }

    private fun drawFrame(c: Canvas, s: GameSession, dt: Float) {
        val l = level ?: return
        time += dt
        val cam = s.camera
        c.drawColor(0xFF0B1026.toInt())
        c.save()
        cam.apply(c)
        cam.visible(vis, 3.0)
        camX = cam.x.toFloat(); camY = cam.y.toFloat()

        if (skin == WorldSkin.BLUEPRINT) { drawBlueprint(c, s, l); c.restore(); overlays(c, s, l); return }

        // ---------------------------------------------------------- ground
        p.color = -1; p.shader = outsideShader
        c.drawRect(vis[0].toFloat(), vis[1].toFloat(), vis[2].toFloat(), vis[3].toFloat(), p)
        p.color = -1; p.shader = groundShader
        c.drawRect(l.minX.toFloat(), l.minY.toFloat(), l.maxX.toFloat(), l.maxY.toFloat(), p)
        p.shader = null
        // soft edge between world and outside
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.5f; p.color = 0x55000000
        c.drawRect(l.minX.toFloat(), l.minY.toFloat(), l.maxX.toFloat(), l.maxY.toFloat(), p)
        p.style = Paint.Style.FILL

        for (z in l.zones) {
            if (!onScreen(z.box.cx, z.box.cy, z.box.radius)) continue
            p.color = -1; p.shader = zoneShaders[z.kind]
            boxPath(z.box, 0.0)
            c.drawPath(path, p)
            p.shader = null
            if (z.kind == ZoneKind.ICE) {
                p.color = 0x33FFFFFF; c.drawPath(path, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.06f; p.color = 0x88E8F8FF.toInt(); c.drawPath(path, p); p.style = Paint.Style.FILL
            }
        }
        if (l.weather == Weather.RAIN) { p.color = 0x22000814; c.drawRect(l.minX.toFloat(), l.minY.toFloat(), l.maxX.toFloat(), l.maxY.toFloat(), p) }

        // tyre-wear lanes along the main driving line
        if (quality != Quality.LOW) {
            wearPaint.color = 0x14000000; wearPaint.strokeWidth = 2.1f; c.drawPath(wearPath, wearPaint)
            wearPaint.color = 0x10000000; wearPaint.strokeWidth = 0.5f
            c.save(); c.translate(0f, 0f); c.drawPath(wearPath, wearPaint); c.restore()
        }
        // oil drips in the bay
        p.color = -1; p.shader = RadialGradient(l.bay.cx.toFloat(), l.bay.cy.toFloat(), 1.1f, intArrayOf(0x2A000000, 0x14000000, 0x00000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(l.bay.cx.toFloat(), l.bay.cy.toFloat(), 1.1f, p); p.shader = null
        for (m in l.marks) drawMark(c, m.kind, m.box)
        for (pr in l.props) if (pr.kind.isDecal()) drawDecal(c, pr)
        // ambient-occlusion contact shadows around everything solid
        if (quality != Quality.LOW) for (o in l.obstacles) {
            if (!o.kind.solid || o.kind == ObKind.CAR || o.kind == ObKind.SIDEWALK) continue
            if (!onScreen(o.shape.cx, o.shape.cy, o.shape.radius + 1)) continue
            contactShadow(c, o)
        }
        s.skids.draw(c, 0.22f)

        // turntables
        l.turntables.forEachIndexed { i, t -> drawTable(c, t.cx.toFloat(), t.cy.toFloat(), t.r.toFloat(), s.world.tableAngles[i].toFloat(), s.world.tableActive == i) }

        drawBay(c, s, l)

        // --------------------------------------------------------- shadows
        if (quality.shadows) {
            for (o in l.obstacles) if (onScreen(o.shape.cx, o.shape.cy, o.shape.radius + 4)) drawShadow(c, o)
            carShadow(c, s.world.body().cx, s.world.body().cy, s.world.car.a, s.spec.length, s.spec.width)
            s.world.trailerBody()?.let { carShadow(c, it.cx, it.cy, it.a, it.hl * 2, it.hw * 2) }
            for (lane in l.traffic) lane.carsAt(s.world.time) { _, x, y -> if (onScreen(x, y, 4.0)) carShadow(c, x, y, lane.angle, lane.carLen, lane.carWidth) }
        }

        // ------------------------------------------------- ground objects
        for (o in l.obstacles) {
            if (!onScreen(o.shape.cx, o.shape.cy, o.shape.radius + 1)) continue
            when (o.kind) {
                ObKind.SIDEWALK -> drawSidewalk(c, o)
                ObKind.CAR -> drawNpc(c, o)
                ObKind.TRUCK, ObKind.VAN, ObKind.BUS -> drawTruck(c, o)
                ObKind.BOAT -> drawBoat(c, o)
                ObKind.PLANTER -> drawPlanter(c, o)
                ObKind.DUMPSTER, ObKind.CRATES -> drawCrate(c, o)
                else -> {}
            }
        }
        // cones (dynamic)
        val coneBmp = Sprites.cone(quality.ppm)
        for (cs in s.world.cones) {
            if (!onScreen(cs.x, cs.y, 1.0)) continue
            drawBitmapWorld(c, coneBmp, cs.x, cs.y, cs.ang, 1f, if (cs.knocked) 0.75f else 1f)
        }
        // gates
        for (g in l.gates) {
            val up = g.upAmount(s.world.time).toFloat()
            for (d in g.bollards) drawBollard(c, d, up)
        }
        // coins
        l.coins.forEachIndexed { i, co -> if (!s.world.coinsTaken[i]) drawCoin(c, co[0].toFloat(), co[1].toFloat(), i) }
        // traffic
        for (lane in l.traffic) lane.carsAt(s.world.time) { idx, x, y ->
            if (onScreen(x, y, 4.0)) {
                val look = Cars.CIVILIAN[Math.floorMod(idx * 7 + lane.seed, Cars.CIVILIAN.size)].look(Styles.TRAFFIC_COLORS[Math.floorMod(idx * 13 + lane.seed, Styles.TRAFFIC_COLORS.size)])
                val bmp = Sprites.car(look, lane.carLen, lane.carWidth, quality.ppm)
                drawBitmapWorld(c, bmp, x, y, lane.angle, 1f, 1f)
            }
        }
        // ghost
        s.ghostWorld?.let { g -> drawCar(c, g, Cars.REF, Cars.REF.look(0x19E3FF, Finish.CHROME), 0.38f) }
        // player (+ underglow)
        if (s.underglow != 0) {
            val b = s.world.body()
            val strength = if (s.level.lighting == Lighting.NIGHT || s.level.lighting == Lighting.DUSK) 0xB0 else 0x60
            val col = (strength shl 24) or s.underglow
            c.save(); c.translate(b.cx.toFloat(), b.cy.toFloat()); c.rotate(Math.toDegrees(b.a).toFloat())
            p.color = -1; p.shader = RadialGradient(0f, 0f, (b.hl * 1.25).toFloat(), intArrayOf(col, col and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.scale(1f, (b.hw / b.hl * 1.6).toFloat())
            c.drawCircle(0f, 0f, (b.hl * 1.25).toFloat(), p); p.shader = null
            c.restore()
        }
        drawCar(c, s.world, s.spec, s.look, 1f, s.bodyShift)

        // ------------------------------------------ 2.5D tall objects
        if (quality.extrude) {
            drawTall(c, s, l)
        } else {
            for (o in l.obstacles) if (isTall(o.kind) && onScreen(o.shape.cx, o.shape.cy, o.shape.radius)) flatTop(c, o)
        }
        // canopies & lamp heads
        for (pr in l.props) {
            if (pr.kind.isDecal() || !onScreen(pr.x, pr.y, 3.0)) continue
            drawProp(c, pr)
        }
        if (quality.particles) s.particles.draw(c)
        drawSensors(c, s)

        // ---------------------------------------------------- lighting
        if (quality.lights) drawLighting(c, s, l)
        c.restore()
        overlays(c, s, l)
    }

    private fun isTall(k: ObKind) = k == ObKind.BUILDING || k == ObKind.WALL || k == ObKind.BARRIER || k == ObKind.CONTAINER ||
        k == ObKind.PILLAR || k == ObKind.HEDGE || k == ObKind.FENCE

    private fun PropKind.isDecal() = this == PropKind.MANHOLE || this == PropKind.OIL || this == PropKind.CRACK || this == PropKind.PUDDLE ||
        this == PropKind.DRAIN || this == PropKind.LEAVES || this == PropKind.FLOWERS

    private fun onScreen(x: Double, y: Double, r: Double) = x + r >= vis[0] && x - r <= vis[2] && y + r >= vis[1] && y - r <= vis[3]

    private fun boxPath(b: Box, inflate: Double) {
        val bb = if (inflate != 0.0) Box(b.cx, b.cy, b.hl + inflate, b.hw + inflate, b.a) else b
        bb.corners(corners)
        path.reset()
        path.moveTo(corners[0].toFloat(), corners[1].toFloat())
        for (k in 1 until 4) path.lineTo(corners[k * 2].toFloat(), corners[k * 2 + 1].toFloat())
        path.close()
    }

    private fun drawBitmapWorld(c: Canvas, bmp: Bitmap, x: Double, y: Double, a: Double, scale: Float, alpha: Float) {
        c.save()
        c.translate(x.toFloat(), y.toFloat())
        c.rotate(Math.toDegrees(a).toFloat())
        val k = scale / quality.ppm
        c.scale(k, k)
        bmpPaint.alpha = (alpha * 255).toInt()
        c.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, bmpPaint)
        bmpPaint.alpha = 255
        c.restore()
    }

    // ===================================================== markings
    private fun drawMark(c: Canvas, k: MarkKind, b: Box) {
        if (!onScreen(b.cx, b.cy, b.radius)) return
        val lineCol = look.line or 0xFF000000.toInt()
        when (k) {
            MarkKind.LINE, MarkKind.LINE_YELLOW, MarkKind.BAY_OUTLINE -> {
                p.color = if (k == MarkKind.LINE_YELLOW) look.lineYellow or 0xFF000000.toInt() else lineCol
                p.alpha = 215
                boxPath(Box(b.cx, b.cy, maxOf(b.hl, 0.06), maxOf(b.hw, 0.06), b.a), 0.0); c.drawPath(path, p)
                p.alpha = 255
            }
            MarkKind.DASH -> {
                // dashes along the longer axis
                val alongLong = b.hw > b.hl
                val len = if (alongLong) b.hw else b.hl
                val ux = if (alongLong) -b.s else b.c; val uy = if (alongLong) b.c else b.s
                p.color = lineCol; p.alpha = 200; p.strokeWidth = 0.14f
                var t = -len
                while (t < len) {
                    c.drawLine((b.cx + ux * t).toFloat(), (b.cy + uy * t).toFloat(), (b.cx + ux * (t + 1.6)).toFloat(), (b.cy + uy * (t + 1.6)).toFloat(), p)
                    t += 3.6
                }
                p.alpha = 255
            }
            MarkKind.STOP -> {
                p.color = lineCol; boxPath(b, 0.0); c.drawPath(path, p)
            }
            MarkKind.CROSSWALK -> {
                // stripes perpendicular to the crossing, spanning the corridor width
                p.color = lineCol; p.alpha = 220
                val n = (b.hw * 2 / 0.9).toInt().coerceAtLeast(2)
                for (i in 0 until n) {
                    val w = -b.hw + 0.45 + i * 0.9
                    val cx = b.cx - b.s * w; val cy = b.cy + b.c * w
                    boxPath(Box(cx, cy, 1.1, 0.22, b.a), 0.0); c.drawPath(path, p)
                }
                p.alpha = 255
            }
            MarkKind.HATCH -> {
                c.save(); boxPath(b, 0.0); c.clipPath(path)
                p.color = 0xFF1B1B1B.toInt(); c.drawPath(path, p)
                p.color = 0xFFFFC21A.toInt(); p.strokeWidth = 0.22f
                var t = -b.radius * 1.5
                while (t < b.radius * 1.5) {
                    val x0 = b.cx + b.c * t; val y0 = b.cy + b.s * t
                    c.drawLine((x0 - 2).toFloat(), (y0 - 2).toFloat(), (x0 + 2).toFloat(), (y0 + 2).toFloat(), p)
                    t += 0.6
                }
                c.restore()
            }
            MarkKind.ARROW, MarkKind.CHEVRON -> {
                p.color = lineCol; p.alpha = 170
                c.save(); c.translate(b.cx.toFloat(), b.cy.toFloat()); c.rotate(Math.toDegrees(b.a).toFloat())
                path.reset(); path.moveTo(1.2f, 0f); path.lineTo(0.2f, -0.7f); path.lineTo(0.2f, -0.25f); path.lineTo(-1.2f, -0.25f)
                path.lineTo(-1.2f, 0.25f); path.lineTo(0.2f, 0.25f); path.lineTo(0.2f, 0.7f); path.close()
                c.drawPath(path, p); c.restore(); p.alpha = 255
            }
        }
    }

    private fun drawDecal(c: Canvas, pr: com.ngstudio.inchperfect.game.level.Prop) {
        if (!onScreen(pr.x, pr.y, 2.0)) return
        val x = pr.x.toFloat(); val y = pr.y.toFloat(); val s = pr.size.toFloat()
        when (pr.kind) {
            PropKind.MANHOLE -> {
                p.color = 0xFF2A2C30.toInt(); c.drawCircle(x, y, 0.36f * s, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.04f; p.color = 0xFF55595F.toInt(); c.drawCircle(x, y, 0.33f * s, p)
                c.drawCircle(x, y, 0.2f * s, p); p.style = Paint.Style.FILL
            }
            PropKind.DRAIN -> {
                p.color = 0xFF1E2023.toInt(); c.drawRect(x - 0.3f * s, y - 0.18f * s, x + 0.3f * s, y + 0.18f * s, p)
                p.color = 0xFF4A4E54.toInt()
                for (k in 0 until 5) c.drawRect(x - 0.26f * s + k * 0.12f * s, y - 0.14f * s, x - 0.22f * s + k * 0.12f * s, y + 0.14f * s, p)
            }
            PropKind.OIL -> {
                p.color = -1; p.shader = RadialGradient(x, y, 0.8f * s, intArrayOf(0x50000000, 0x28000000, 0x00000000), null, Shader.TileMode.CLAMP)
                c.drawOval(x - 0.9f * s, y - 0.6f * s, x + 0.9f * s, y + 0.6f * s, p); p.shader = null
            }
            PropKind.PUDDLE -> {
                p.color = -1; p.shader = RadialGradient(x, y, 1.2f * s, intArrayOf(0x6680A8C8, 0x3360809A, 0x00000000), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
                c.drawOval(x - 1.3f * s, y - 0.8f * s, x + 1.3f * s, y + 0.8f * s, p); p.shader = null
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.03f; p.color = 0x55FFFFFF
                val ph = (time * 0.8f + pr.variant % 10) % 1f
                c.drawCircle(x + 0.3f, y - 0.1f, 0.6f * ph * s, p); p.style = Paint.Style.FILL
            }
            PropKind.CRACK -> {
                p.color = 0x55000000; p.strokeWidth = 0.035f
                val rnd = java.util.Random(pr.variant.toLong())
                var cx = x; var cy = y
                repeat(6) { val nx = cx + (rnd.nextFloat() - 0.5f) * 0.9f * s; val ny = cy + (rnd.nextFloat() - 0.5f) * 0.9f * s; c.drawLine(cx, cy, nx, ny, p); cx = nx; cy = ny }
            }
            PropKind.LEAVES, PropKind.FLOWERS -> {
                val rnd = java.util.Random(pr.variant.toLong())
                repeat(10) {
                    p.color = if (pr.kind == PropKind.FLOWERS) intArrayOf(0xFFFF7AA8.toInt(), 0xFFFFE27A.toInt(), 0xFFFFFFFF.toInt())[it % 3] else intArrayOf(0xAAB5651D.toInt(), 0xAAD9A43A.toInt(), 0xAA8E3B1F.toInt())[it % 3]
                    c.drawCircle(x + (rnd.nextFloat() - 0.5f) * 1.6f * s, y + (rnd.nextFloat() - 0.5f) * 1.6f * s, 0.08f, p)
                }
            }
            else -> {}
        }
    }

    // ========================================================== bay
    private val dash = DashPathEffect(floatArrayOf(0.5f, 0.3f), 0f)
    private fun drawBay(c: Canvas, s: GameSession, l: Level) {
        val b = l.bay
        val ok = s.world.bayContainsCar() && s.world.headingOk()
        val pulse = (0.5f + 0.5f * sin(time * 3.2f))
        val col = if (ok) 0xFF9BFF3C.toInt() else 0xFF19E3FF.toInt()
        boxPath(b, 0.0)
        p.color = col; p.alpha = (if (ok) 70 else 34 + (18 * pulse).toInt())
        c.drawPath(path, p)
        // glow border
        p.style = Paint.Style.STROKE
        p.alpha = 60; p.strokeWidth = 0.32f; c.drawPath(path, p)
        p.alpha = 255; p.strokeWidth = 0.1f
        p.pathEffect = DashPathEffect(floatArrayOf(0.55f, 0.32f), -time * 1.4f)
        c.drawPath(path, p)
        p.pathEffect = null; p.style = Paint.Style.FILL
        // nose arrow (chevrons) in the bay
        c.save(); c.translate(b.cx.toFloat(), b.cy.toFloat()); c.rotate(Math.toDegrees(b.a).toFloat())
        val len = (b.hl * 0.55).toFloat()
        p.color = col
        for (k in 0 until 3) {
            val off = -len * 0.4f + k * len * 0.45f
            val a = ((pulse + k * 0.33f) % 1f)
            p.alpha = (120 + 120 * a).toInt()
            path.reset(); path.moveTo(off + 0.55f, 0f); path.lineTo(off, -0.6f); path.lineTo(off - 0.28f, -0.6f); path.lineTo(off + 0.27f, 0f)
            path.lineTo(off - 0.28f, 0.6f); path.lineTo(off, 0.6f); path.close()
            c.drawPath(path, p)
        }
        // parking progress ring
        val prog = s.world.parkProgress.toFloat()
        if (prog > 0f) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 0.18f; p.alpha = 255; p.color = 0xFF9BFF3C.toInt()
            rect.set(-0.9f, -0.9f, 0.9f, 0.9f); c.drawArc(rect, -90f, 360f * prog, false, p); p.style = Paint.Style.FILL
        }
        c.restore()
        p.alpha = 255
    }

    // ================================================ contact shadows
    private fun contactShadow(c: Canvas, o: Obstacle) {
        p.color = 0xFF000000.toInt()
        val layers = if (quality == Quality.ULTRA || quality == Quality.HIGH) 3 else 2
        when (val sh = o.shape) {
            is Box -> for (k in layers downTo 1) { p.alpha = 22; boxPath(sh, 0.16 * k); c.drawPath(path, p) }
            is Disc -> for (k in layers downTo 1) { p.alpha = 22; c.drawCircle(sh.cx.toFloat(), sh.cy.toFloat(), sh.r.toFloat() + 0.16f * k, p) }
        }
        p.alpha = 255
    }

    // ===================================================== shadows
    private fun drawShadow(c: Canvas, o: Obstacle) {
        val h = o.kind.height.toFloat().coerceAtMost(8f)
        val ox = sunX * h * 0.35f; val oy = sunY * h * 0.35f
        p.color = 0x000000; p.alpha = if (o.kind == ObKind.HEDGE || o.kind == ObKind.FENCE) 60 else 85
        when (val sh = o.shape) {
            is Box -> {
                if (o.kind == ObKind.CAR || o.kind == ObKind.SIDEWALK || o.kind == ObKind.CONE) return
                c.save(); c.translate(ox, oy); boxPath(sh, 0.05); c.drawPath(path, p); c.restore()
            }
            is Disc -> if (o.kind != ObKind.CONE) c.drawCircle((sh.cx + ox).toFloat(), (sh.cy + oy).toFloat(), sh.r.toFloat() * 1.05f, p)
        }
        if (o.kind == ObKind.CAR) {
            val b = o.shape as Box
            carShadow(c, b.cx, b.cy, b.a, b.hl * 2, b.hw * 2)
        }
        p.alpha = 255
    }

    private fun carShadow(c: Canvas, x: Double, y: Double, a: Double, len: Double, w: Double) {
        val sb = Sprites.shadow(Math.round(len * 10) / 10.0, Math.round(w * 10) / 10.0, quality.ppm / 2)
        c.save()
        c.translate((x + sunX * 0.45).toFloat(), (y + sunY * 0.45).toFloat())
        c.rotate(Math.toDegrees(a).toFloat())
        val k = 2f / quality.ppm
        c.scale(k, k)
        bmpPaint.alpha = 150
        c.drawBitmap(sb, -sb.width / 2f, -sb.height / 2f, bmpPaint)
        bmpPaint.alpha = 255
        c.restore()
    }

    // ============================================== ground objects
    private fun drawSidewalk(c: Canvas, o: Obstacle) {
        val b = o.shape as Box
        boxPath(b, 0.0)
        p.color = -1; p.shader = sidewalkShader; c.drawPath(path, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.22f; p.color = 0xFFD9DDE2.toInt(); c.drawPath(path, p)
        p.strokeWidth = 0.05f; p.color = 0x66000000; boxPath(b, 0.13); c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun npcBitmap(o: Obstacle): Bitmap {
        val b = o.shape as Box
        val spec = Cars.CIVILIAN[Math.floorMod(o.style, Cars.CIVILIAN.size)]
        val col = if (o.color != 0) o.color else 0x8899AA
        return Sprites.car(spec.look(skinColor(col)), Math.round(b.hl * 20) / 10.0, Math.round(b.hw * 20) / 10.0, quality.ppm)
    }

    private fun drawNpc(c: Canvas, o: Obstacle) {
        val b = o.shape as Box
        drawBitmapWorld(c, npcBitmap(o), b.cx, b.cy, if (o.style and 1 == 0) b.a else b.a + Math.PI, 1f, 1f)
    }

    private fun drawTruck(c: Canvas, o: Obstacle) {
        val b = o.shape as Box
        c.save(); c.translate(b.cx.toFloat(), b.cy.toFloat()); c.rotate(Math.toDegrees(b.a).toFloat())
        val hl = b.hl.toFloat(); val hw = b.hw.toFloat()
        // cab at the +x end (towards the aisle)
        val cab = 2.2f
        val cabCol = Styles.TRUCK_CABS[Math.floorMod(o.style, Styles.TRUCK_CABS.size)]
        p.color = 0xFF16181C.toInt()
        c.drawRoundRect(-hl, -hw + 0.05f, hl, hw - 0.05f, 0.2f, 0.2f, p)
        // trailer box
        p.color = -1; p.shader = LinearGradient(0f, -hw, 0f, hw, intArrayOf(0xFFF4F6F8.toInt(), 0xFFDDE1E6.toInt(), 0xFFB9BEC5.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(-hl, -hw, hl - cab - 0.15f, hw, 0.12f, 0.12f, p); p.shader = null
        p.color = 0x22000000; p.strokeWidth = 0.05f
        var x = -hl + 0.6f
        while (x < hl - cab - 0.4f) { c.drawLine(x, -hw + 0.08f, x, hw - 0.08f, p); x += 0.7f }
        p.color = Styles.TRUCK_STRIPES[Math.floorMod(o.style / 3, Styles.TRUCK_STRIPES.size)]
        c.drawRect(-hl, hw - 0.28f, hl - cab - 0.15f, hw - 0.12f, p)
        // cab
        p.color = -1; p.shader = LinearGradient(0f, -hw, 0f, hw, intArrayOf(Sprites.lighten(cabCol, 0.3f), cabCol, Sprites.darken(cabCol, 0.35f)), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(hl - cab, -hw + 0.1f, hl, hw - 0.1f, 0.35f, 0.35f, p); p.shader = null
        p.color = 0xFF1B2333.toInt(); c.drawRoundRect(hl - 0.55f, -hw + 0.25f, hl - 0.15f, hw - 0.25f, 0.12f, 0.12f, p)
        p.color = 0x55FFFFFF; c.drawRect(hl - 0.5f, -hw + 0.3f, hl - 0.42f, hw - 0.3f, p)
        p.color = 0xFF2A2E35.toInt(); c.drawRoundRect(hl - cab + 0.3f, -0.5f, hl - 0.8f, 0.5f, 0.1f, 0.1f, p)
        c.restore()
    }

    private fun drawBoat(c: Canvas, o: Obstacle) {
        val b = o.shape as Box
        c.save(); c.translate(b.cx.toFloat(), b.cy.toFloat()); c.rotate(Math.toDegrees(b.a).toFloat())
        val hl = b.hl.toFloat(); val hw = b.hw.toFloat()
        p.color = 0xFF2A2C30.toInt(); c.drawRect(-hl, -0.15f, hl, 0.15f, p)
        path.reset(); path.moveTo(hl, 0f); path.quadTo(hl * 0.6f, -hw, 0f, -hw); path.lineTo(-hl, -hw * 0.85f); path.lineTo(-hl, hw * 0.85f)
        path.lineTo(0f, hw); path.quadTo(hl * 0.6f, hw, hl, 0f); path.close()
        p.color = -1; p.shader = LinearGradient(0f, -hw, 0f, hw, intArrayOf(0xFFFFFFFF.toInt(), 0xFFE6ECF2.toInt(), 0xFFB8C4D0.toInt()), null, Shader.TileMode.CLAMP)
        c.drawPath(path, p); p.shader = null
        p.color = Styles.BOAT_DECKS[Math.floorMod(o.style, Styles.BOAT_DECKS.size)]
        c.drawRoundRect(-hl * 0.7f, -hw * 0.6f, hl * 0.35f, hw * 0.6f, 0.3f, 0.3f, p)
        p.color = 0xFF1B2333.toInt(); c.drawRoundRect(-hl * 0.2f, -hw * 0.45f, hl * 0.15f, hw * 0.45f, 0.2f, 0.2f, p)
        c.restore()
    }

    private fun drawPlanter(c: Canvas, o: Obstacle) {
        val d = o.shape as? Disc ?: return
        val r = d.r.toFloat()
        p.color = 0xFF9EA3A9.toInt(); c.drawCircle(d.cx.toFloat(), d.cy.toFloat(), r, p)
        p.color = 0xFF5A4636.toInt(); c.drawCircle(d.cx.toFloat(), d.cy.toFloat(), r * 0.86f, p)
        val tree = Sprites.tree(quality.ppm / 2, minOf(3.5f, r * 0.95f), look.hedge, o.style)
        drawBitmapWorld(c, tree, d.cx, d.cy, (o.style % 360) * 0.0174, 2f, 1f)
    }

    private fun drawCrate(c: Canvas, o: Obstacle) {
        val b = o.shape as? Box ?: return
        boxPath(b, 0.0); p.color = if (o.kind == ObKind.DUMPSTER) 0xFF2E6B3A.toInt() else 0xFF9A6A3A.toInt(); c.drawPath(path, p)
    }

    private fun drawBollard(c: Canvas, d: Disc, up: Float) {
        val x = d.cx.toFloat(); val y = d.cy.toFloat(); val r = d.r.toFloat()
        p.color = 0xFF2A2C30.toInt(); c.drawCircle(x, y, r * 1.25f, p)
        if (up > 0.02f) {
            val ox = (x - camX) * 0.02f * up; val oy = (y - camY) * 0.02f * up
            p.color = 0xFF8E949B.toInt(); c.drawCircle(x + ox * 0.5f, y + oy * 0.5f, r, p)
            p.color = 0xFFE34B4B.toInt(); c.drawCircle(x + ox, y + oy, r * 0.85f, p)
            p.color = 0xFFFFFFFF.toInt(); c.drawCircle(x + ox, y + oy, r * 0.4f, p)
        } else {
            p.color = 0xFF6E747C.toInt(); c.drawCircle(x, y, r * 0.9f, p)
            p.color = 0xFF9BFF3C.toInt(); p.alpha = 160; c.drawCircle(x, y, r * 0.35f, p); p.alpha = 255
        }
    }

    private fun drawCoin(c: Canvas, x: Float, y: Float, i: Int) {
        val spin = abs(cos(time * 3.0f + i))
        val r = 0.42f
        p.color = 0x55000000; c.drawOval(x - r * spin + 0.12f, y - r + 0.15f, x + r * spin + 0.12f, y + r + 0.15f, p)
        p.color = -1; p.shader = LinearGradient(x - r, y - r, x + r, y + r, intArrayOf(0xFFFFF0A0.toInt(), 0xFFFFC21A.toInt(), 0xFFB87A00.toInt()), null, Shader.TileMode.CLAMP)
        c.drawOval(x - r * spin, y - r, x + r * spin, y + r, p); p.shader = null
        p.color = 0xFFFFE27A.toInt(); c.drawOval(x - r * 0.62f * spin, y - r * 0.62f, x + r * 0.62f * spin, y + r * 0.62f, p)
        p.color = 0xFFB87A00.toInt(); p.alpha = 150
        c.drawOval(x - r * 0.18f * spin, y - r * 0.35f, x + r * 0.18f * spin, y + r * 0.35f, p); p.alpha = 255
    }

    private fun drawTable(c: Canvas, x: Float, y: Float, r: Float, ang: Float, active: Boolean) {
        p.color = 0xFF2B2E33.toInt(); c.drawCircle(x, y, r + 0.25f, p)
        p.color = -1; p.shader = RadialGradient(x, y, r, intArrayOf(0xFF6E747C.toInt(), 0xFF50555C.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p); p.shader = null
        c.save(); c.translate(x, y); c.rotate(Math.toDegrees(ang.toDouble()).toFloat())
        p.color = 0x33000000; p.strokeWidth = 0.06f
        for (k in 0 until 24) { c.rotate(15f); c.drawLine(r * 0.2f, 0f, r * 0.96f, 0f, p) }
        c.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.12f
        p.color = if (active) 0xFF9BFF3C.toInt() else 0xFFFFC21A.toInt(); p.alpha = (150 + 100 * sin(time * 4f)).toInt().coerceIn(0, 255)
        c.drawCircle(x, y, r + 0.12f, p)
        p.style = Paint.Style.FILL; p.alpha = 255
    }

    // ========================================================= cars
    private var camX = 0f; private var camY = 0f

    private fun drawCar(c: Canvas, w: World, spec: CarSpec, carLook: CarLook, alpha: Float, shift: Double = 0.0) {
        val car = w.car
        val b = w.body()
        val ppm = quality.ppm
        // trailer first
        w.trailerBody()?.let { t -> drawTrailer(c, t, alpha) }
        // front wheels (steering) under the body
        val front = (spec.length * 0.31)
        val steerDeg = (car.steer * spec.maxSteerDeg).toFloat()
        c.save(); c.translate(b.cx.toFloat(), b.cy.toFloat()); c.rotate(Math.toDegrees(b.a).toFloat())
        p.color = 0xFF101114.toInt(); p.alpha = (alpha * 255).toInt()
        for (sy in floatArrayOf(-1f, 1f)) {
            c.save(); c.translate(front.toFloat(), sy * (spec.width.toFloat() / 2 - 0.16f)); c.rotate(steerDeg)
            c.drawRoundRect(-0.34f, -0.13f, 0.34f, 0.13f, 0.06f, 0.06f, p)
            c.restore()
        }
        c.restore()
        p.alpha = 255
        val bmp = Sprites.car(carLook, spec.length, spec.width, ppm)
        drawBitmapWorld(c, bmp, b.cx + kotlin.math.cos(b.a) * shift, b.cy + kotlin.math.sin(b.a) * shift, b.a, 1f, alpha)
        if (alpha < 1f) return
        // brake / reverse light glows
        val braking = w.input.brake > 0.1 || (w.input.throttle > 0.05 && car.v * car.gear < -0.05)
        val reversing = car.gear < 0
        c.save(); c.translate(b.cx.toFloat(), b.cy.toFloat()); c.rotate(Math.toDegrees(b.a).toFloat())
        val hl = b.hl.toFloat(); val hw = b.hw.toFloat()
        if (braking || reversing) {
            val col = if (braking) 0xCCFF2030.toInt() else 0xCCF2F6FF.toInt()
            for (sy in floatArrayOf(-1f, 1f)) {
                p.color = -1; p.shader = RadialGradient(-hl, sy * hw * 0.7f, 0.7f, intArrayOf(col, col and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                c.drawCircle(-hl, sy * hw * 0.7f, 0.7f, p)
            }
            p.shader = null
        }
        c.restore()
    }

    private fun drawTrailer(c: Canvas, t: Box, alpha: Float) {
        c.save(); c.translate(t.cx.toFloat(), t.cy.toFloat()); c.rotate(Math.toDegrees(t.a).toFloat())
        val hl = t.hl.toFloat(); val hw = t.hw.toFloat()
        p.alpha = (alpha * 255).toInt()
        p.color = 0xFF2A2C30.toInt(); c.drawRect(hl, -0.08f, hl + 0.75f, 0.08f, p)
        p.color = 0xFF101114.toInt()
        for (sy in floatArrayOf(-1f, 1f)) c.drawRoundRect(-0.45f, sy * hw - 0.14f, 0.45f, sy * hw + 0.14f, 0.06f, 0.06f, p)
        p.color = -1; p.shader = LinearGradient(0f, -hw, 0f, hw, intArrayOf(0xFFF2F4F7.toInt(), 0xFFD5DAE0.toInt(), 0xFFA9B0B8.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(-hl, -hw, hl, hw, 0.18f, 0.18f, p); p.shader = null
        p.color = 0x22000000; p.strokeWidth = 0.04f
        var x = -hl + 0.5f
        while (x < hl - 0.2f) { c.drawLine(x, -hw + 0.1f, x, hw - 0.1f, p); x += 0.55f }
        p.color = 0xFFFF7A1A.toInt(); c.drawRect(-hl, -hw, -hl + 0.12f, hw, p)
        p.alpha = 255
        c.restore()
    }

    // ===================================================== 2.5D
    private class TallItem(val o: Obstacle, val d: Double)
    private val tall = ArrayList<TallItem>(128)
    private val top = DoubleArray(8)

    private fun drawTall(c: Canvas, s: GameSession, l: Level) {
        camX = s.camera.x.toFloat(); camY = s.camera.y.toFloat()
        tall.clear()
        for (o in l.obstacles) {
            if (!isTall(o.kind)) continue
            if (!onScreen(o.shape.cx, o.shape.cy, o.shape.radius + 4)) continue
            tall.add(TallItem(o, -hypot(o.shape.cx - camX, o.shape.cy - camY)))
        }
        tall.sortBy { it.d }
        val k = 0.018 * (60.0 / s.camera.zoom).coerceIn(0.7, 1.4)
        for (it in tall) {
            val o = it.o
            when (val sh = o.shape) {
                is Disc -> extrudeDisc(c, o, sh, k)
                is Box -> extrudeBox(c, o, sh, k)
            }
        }
    }

    private fun sideColor(o: Obstacle): Int = when (o.kind) {
        ObKind.BUILDING -> Sprites.darken(look.wall or 0xFF000000.toInt(), 0.1f)
        ObKind.WALL -> look.wall or 0xFF000000.toInt()
        ObKind.BARRIER -> 0xFFB8BCC2.toInt()
        ObKind.CONTAINER -> look.container[Math.floorMod(o.style, look.container.size)] or 0xFF000000.toInt()
        ObKind.PILLAR -> 0xFFA4A9AF.toInt()
        ObKind.HEDGE -> Sprites.darken(look.hedge or 0xFF000000.toInt(), 0.35f)
        ObKind.FENCE -> 0xFF6E747C.toInt()
        else -> 0xFF888888.toInt()
    }

    private fun extrudeBox(c: Canvas, o: Obstacle, b: Box, k: Double) {
        var h = o.kind.height
        if (o.kind == ObKind.BUILDING) h = 4.0 + (Math.floorMod(o.style, 5)) * 1.6
        b.corners(corners)
        val f = k * h
        for (i in 0 until 4) {
            top[i * 2] = corners[i * 2] + (corners[i * 2] - camX) * f
            top[i * 2 + 1] = corners[i * 2 + 1] + (corners[i * 2 + 1] - camY) * f
        }
        val base = skinColor(sideColor(o))
        // visible side faces: outward normal facing the camera centre
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val ex = corners[j * 2] - corners[i * 2]; val ey = corners[j * 2 + 1] - corners[i * 2 + 1]
            // outward normal for clockwise-on-screen corner order (y down): (ey, -ex)
            var nx = ey; var ny = -ex
            val mx = (corners[i * 2] + corners[j * 2]) / 2 - b.cx; val my = (corners[i * 2 + 1] + corners[j * 2 + 1]) / 2 - b.cy
            if (nx * mx + ny * my < 0) { nx = -nx; ny = -ny }
            val toCamX = camX - (corners[i * 2] + corners[j * 2]) / 2; val toCamY = camY - (corners[i * 2 + 1] + corners[j * 2 + 1]) / 2
            if (nx * toCamX + ny * toCamY <= 0) continue
            val ln = sqrt(nx * nx + ny * ny)
            val lit = (0.62 + 0.28 * ((-nx * 0.5 - ny * 0.7) / ln)).toFloat()
            path.reset()
            path.moveTo(corners[i * 2].toFloat(), corners[i * 2 + 1].toFloat())
            path.lineTo(corners[j * 2].toFloat(), corners[j * 2 + 1].toFloat())
            path.lineTo(top[j * 2].toFloat(), top[j * 2 + 1].toFloat())
            path.lineTo(top[i * 2].toFloat(), top[i * 2 + 1].toFloat())
            path.close()
            p.color = Sprites.darken(base, (1f - lit).coerceIn(0f, 0.7f))
            c.drawPath(path, p)
            faceDetail(c, o, i, j, h)
        }
        // top
        path.reset()
        path.moveTo(top[0].toFloat(), top[1].toFloat())
        for (i in 1 until 4) path.lineTo(top[i * 2].toFloat(), top[i * 2 + 1].toFloat())
        path.close()
        topFace(c, o, b, h)
    }

    /** windows / ribs / mortar on a side face */
    private fun faceDetail(c: Canvas, o: Obstacle, i: Int, j: Int, h: Double) {
        when (o.kind) {
            ObKind.BUILDING -> {
                val night = level?.lighting == Lighting.NIGHT || level?.lighting == Lighting.DUSK
                val ax = corners[i * 2]; val ay = corners[i * 2 + 1]; val bx = corners[j * 2]; val by = corners[j * 2 + 1]
                val atx = top[i * 2]; val aty = top[i * 2 + 1]; val btx = top[j * 2]; val bty = top[j * 2 + 1]
                val len = hypot(bx - ax, by - ay)
                val cols = (len / 1.6).toInt(); val rows = (h / 2.6).toInt()
                if (cols < 1 || rows < 1) return
                for (r in 0 until rows) for (q in 0 until cols) {
                    val u0 = (q + 0.25) / cols; val u1 = (q + 0.75) / cols
                    val v0 = (r + 0.3) / rows; val v1 = (r + 0.75) / rows
                    fun px(u: Double, v: Double) = (ax + (bx - ax) * u) + ((atx + (btx - atx) * u) - (ax + (bx - ax) * u)) * v
                    fun py(u: Double, v: Double) = (ay + (by - ay) * u) + ((aty + (bty - aty) * u) - (ay + (by - ay) * u)) * v
                    val lit = night && ((o.style + r * 7 + q * 13) % 5 != 0)
                    p.color = if (lit) 0xFFFFD98A.toInt() else 0xFF2A3346.toInt()
                    p.alpha = if (lit) 230 else 200
                    path.reset()
                    path.moveTo(px(u0, v0).toFloat(), py(u0, v0).toFloat()); path.lineTo(px(u1, v0).toFloat(), py(u1, v0).toFloat())
                    path.lineTo(px(u1, v1).toFloat(), py(u1, v1).toFloat()); path.lineTo(px(u0, v1).toFloat(), py(u0, v1).toFloat()); path.close()
                    c.drawPath(path, p)
                }
                p.alpha = 255
            }
            ObKind.CONTAINER -> {
                p.color = 0x33000000; p.strokeWidth = 0.05f
                val ax = corners[i * 2]; val ay = corners[i * 2 + 1]; val bx = corners[j * 2]; val by = corners[j * 2 + 1]
                val n = (hypot(bx - ax, by - ay) / 0.35).toInt()
                for (q in 1 until n) {
                    val u = q.toDouble() / n
                    c.drawLine((ax + (bx - ax) * u).toFloat(), (ay + (by - ay) * u).toFloat(), (top[i * 2] + (top[j * 2] - top[i * 2]) * u).toFloat(), (top[i * 2 + 1] + (top[j * 2 + 1] - top[i * 2 + 1]) * u).toFloat(), p)
                }
            }
            else -> {}
        }
    }

    private fun topFace(c: Canvas, o: Obstacle, b: Box, h: Double) {
        when (o.kind) {
            ObKind.BUILDING -> {
                val roof = skinColor(look.roofs[Math.floorMod(o.style, look.roofs.size)] or 0xFF000000.toInt())
                p.color = roof; c.drawPath(path, p)
                p.color = -1; p.shader = roofShader; p.alpha = 60; c.drawPath(path, p); p.shader = null; p.alpha = 255
                // parapet
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.28f; p.color = Sprites.lighten(roof, 0.25f); c.drawPath(path, p)
                p.style = Paint.Style.FILL
                // roof units (HVAC, skylights)
                val tcx = (top[0] + top[2] + top[4] + top[6]) / 4; val tcy = (top[1] + top[3] + top[5] + top[7]) / 4
                c.save(); c.translate(tcx.toFloat(), tcy.toFloat()); c.rotate(Math.toDegrees(b.a).toFloat())
                val rnd = (o.style and 0xffff)
                val hl = b.hl.toFloat(); val hw = b.hw.toFloat()
                // solar array
                if (rnd % 3 == 0 && hl > 2.5f && hw > 1.5f) {
                    val cols = (hl * 0.9f).toInt().coerceAtMost(8); val rows = (hw * 0.7f).toInt().coerceIn(1, 4)
                    for (i in 0 until cols) for (j in 0 until rows) {
                        val x = -hl * 0.8f + i * 0.95f; val y = -hw * 0.6f + j * 0.85f
                        if (x > hl * 0.4f) continue
                        p.color = 0x55000000; c.drawRect(x + 0.12f, y + 0.12f, x + 0.85f, y + 0.75f, p)
                        p.color = -1; p.shader = LinearGradient(x, y, x + 0.8f, y + 0.7f, intArrayOf(0xFF2B4C8C.toInt(), 0xFF16284F.toInt()), null, Shader.TileMode.CLAMP)
                        c.drawRect(x, y, x + 0.8f, y + 0.7f, p); p.shader = null
                        p.color = 0x3399C8FF; c.drawLine(x + 0.4f, y, x + 0.4f, y + 0.7f, p); c.drawLine(x, y + 0.35f, x + 0.8f, y + 0.35f, p)
                    }
                }
                // water tank
                if (rnd % 4 == 1) {
                    val tx = hl * 0.45f; val ty = -hw * 0.35f
                    p.color = 0x55000000; c.drawCircle(tx + 0.3f, ty + 0.3f, 0.9f, p)
                    p.color = -1; p.shader = RadialGradient(tx - 0.3f, ty - 0.3f, 1.1f, intArrayOf(0xFFE6E8EB.toInt(), 0xFF8E949B.toInt()), null, Shader.TileMode.CLAMP)
                    c.drawCircle(tx, ty, 0.9f, p); p.shader = null
                    p.style = Paint.Style.STROKE; p.strokeWidth = 0.06f; p.color = 0x55000000; c.drawCircle(tx, ty, 0.6f, p); p.style = Paint.Style.FILL
                }
                // HVAC units with fans
                val n = 1 + rnd % 3
                for (q in 0 until n) {
                    val ux = ((rnd shr (q * 3)) % 7 - 3) * 0.12f * hl
                    val uy = ((rnd shr (q * 3 + 2)) % 5 - 2) * 0.15f * hw
                    p.color = 0x55000000; c.drawRect(ux - 0.6f + 0.25f, uy - 0.5f + 0.25f, ux + 0.6f + 0.25f, uy + 0.5f + 0.25f, p)
                    p.color = -1; p.shader = LinearGradient(ux - 0.6f, uy - 0.5f, ux + 0.6f, uy + 0.5f, intArrayOf(0xFFE1E5EA.toInt(), 0xFFA9B0B8.toInt()), null, Shader.TileMode.CLAMP)
                    c.drawRect(ux - 0.6f, uy - 0.5f, ux + 0.6f, uy + 0.5f, p); p.shader = null
                    p.color = 0xFF5A6068.toInt(); c.drawCircle(ux, uy, 0.34f, p)
                    p.color = 0xFF8E949B.toInt(); p.strokeWidth = 0.05f
                    for (k in 0 until 4) { c.save(); c.rotate(k * 45f + time * 120f, ux, uy); c.drawLine(ux - 0.3f, uy, ux + 0.3f, uy, p); c.restore() }
                }
                if (hl > 3 && rnd % 2 == 0) {
                    p.color = 0xFF3A4A66.toInt(); c.drawRect(-hl * 0.5f, -0.4f, -hl * 0.2f, 0.4f, p)
                    p.color = 0x55FFFFFF; c.drawRect(-hl * 0.5f, -0.4f, -hl * 0.42f, 0.4f, p)
                }
                // rooftop beacon at night
                if (level?.lighting == Lighting.NIGHT || level?.lighting == Lighting.DUSK) {
                    val on = ((time * 1.2f + rnd * 0.13f) % 1f) < 0.5f
                    p.color = if (on) 0xFFFF3040.toInt() else 0xFF5A1018.toInt(); c.drawCircle(-hl * 0.85f, -hw * 0.8f, 0.18f, p)
                    if (on) { p.color = 0x55FF3040; c.drawCircle(-hl * 0.85f, -hw * 0.8f, 0.55f, p) }
                }
                c.restore()
            }
            ObKind.WALL -> {
                p.color = skinColor(look.wallTop or 0xFF000000.toInt()); c.drawPath(path, p)
            }
            ObKind.BARRIER -> {
                val plastic = level?.district == 15
                val tcx = (top[0] + top[4]) / 2; val tcy = (top[1] + top[5]) / 2
                c.save(); c.clipPath(path)
                c.translate(tcx.toFloat(), tcy.toFloat()); c.rotate(Math.toDegrees(b.a).toFloat())
                val hl = b.hl.toFloat(); val hw = b.hw.toFloat()
                if (plastic) {
                    p.color = if (Math.floorMod(o.style, 2) == 0) 0xFFFF7A1A.toInt() else 0xFFF4F6FA.toInt()
                    c.drawRect(-hl, -hw, hl, hw, p)
                    p.color = 0x33000000; c.drawRect(-hl, -hw * 0.15f, hl, hw * 0.15f, p)
                } else {
                    p.color = -1; p.shader = LinearGradient(0f, -hw, 0f, hw, intArrayOf(0xFFB9BDC3.toInt(), 0xFFE6E8EB.toInt(), 0xFFD3D6DA.toInt(), 0xFF9A9FA6.toInt()), floatArrayOf(0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP)
                    c.drawRect(-hl, -hw, hl, hw, p); p.shader = null
                    p.color = 0x22000000; c.drawRect(hl - 0.04f, -hw, hl, hw, p); c.drawRect(-hl, -hw, -hl + 0.04f, hw, p)
                    // reflectors
                    p.color = if (level?.lighting == Lighting.NIGHT) 0xFFFFE27A.toInt() else 0xFFE8B64A.toInt()
                    var x = -hl + 0.5f
                    while (x < hl - 0.3f) { c.drawRect(x, -0.06f, x + 0.18f, 0.06f, p); x += 1.0f }
                }
                c.restore()
            }
            ObKind.CONTAINER -> {
                val col = look.container[Math.floorMod(o.style, look.container.size)] or 0xFF000000.toInt()
                p.color = Sprites.lighten(col, 0.1f); c.drawPath(path, p)
                c.save(); c.clipPath(path)
                val tcx = (top[0] + top[4]) / 2; val tcy = (top[1] + top[5]) / 2
                c.translate(tcx.toFloat(), tcy.toFloat()); c.rotate(Math.toDegrees(b.a).toFloat())
                p.color = 0x26000000; p.strokeWidth = 0.08f
                var x = -b.hl.toFloat()
                while (x < b.hl) { c.drawLine(x, -b.hw.toFloat(), x, b.hw.toFloat(), p); x += 0.3f }
                c.restore()
            }
            ObKind.HEDGE -> {
                val hc = look.hedge or 0xFF000000.toInt()
                p.color = Sprites.darken(hc, 0.15f); c.drawPath(path, p)
                p.color = -1; p.shader = hedgeShader; p.alpha = 200; c.drawPath(path, p); p.shader = null; p.alpha = 255
                // leafy clumps along the hedge
                c.save(); c.clipPath(path)
                val tcx = (top[0] + top[4]) / 2; val tcy = (top[1] + top[5]) / 2
                c.translate(tcx.toFloat(), tcy.toFloat()); c.rotate(Math.toDegrees(b.a).toFloat())
                var x = -b.hl.toFloat()
                var k = o.style
                while (x < b.hl) {
                    k = k * 1103515245 + 12345
                    val r = b.hw.toFloat() * (0.7f + ((k ushr 16) and 0xff) / 255f * 0.5f)
                    p.color = -1; p.shader = RadialGradient(x - r * 0.3f, -r * 0.3f, r * 1.2f, intArrayOf(Sprites.lighten(hc, 0.25f), hc, Sprites.darken(hc, 0.3f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                    c.drawCircle(x, 0f, r, p)
                    x += r * 1.1f
                }
                p.shader = null
                c.restore()
            }
            ObKind.PILLAR -> {
                p.color = 0xFFC9CED4.toInt(); c.drawPath(path, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.08f; p.color = 0xFFFFC21A.toInt(); c.drawPath(path, p); p.style = Paint.Style.FILL
            }
            ObKind.FENCE -> {
                p.color = 0xFF9AA1AA.toInt(); c.drawPath(path, p)
            }
            else -> { p.color = 0xFF999999.toInt(); c.drawPath(path, p) }
        }
    }

    private fun extrudeDisc(c: Canvas, o: Obstacle, d: Disc, k: Double) {
        val h = o.kind.height
        val f = k * h
        val tx = d.cx + (d.cx - camX) * f; val ty = d.cy + (d.cy - camY) * f
        p.color = sideColor(o)
        // side as a capsule between base and top circle
        val ang = atan2(ty - d.cy, tx - d.cx)
        val nx = -sin(ang) * d.r; val ny = cos(ang) * d.r
        path.reset()
        path.moveTo((d.cx + nx).toFloat(), (d.cy + ny).toFloat()); path.lineTo((tx + nx).toFloat(), (ty + ny).toFloat())
        path.lineTo((tx - nx).toFloat(), (ty - ny).toFloat()); path.lineTo((d.cx - nx).toFloat(), (d.cy - ny).toFloat()); path.close()
        c.drawPath(path, p)
        c.drawCircle(d.cx.toFloat(), d.cy.toFloat(), d.r.toFloat(), p)
        p.color = 0xFFC9CED4.toInt(); c.drawCircle(tx.toFloat(), ty.toFloat(), d.r.toFloat(), p)
    }

    private fun flatTop(c: Canvas, o: Obstacle) {
        when (val sh = o.shape) {
            is Box -> { boxPath(sh, 0.0); for (i in 0 until 8) top[i] = corners[i]; topFace(c, o, sh, o.kind.height) }
            is Disc -> { p.color = 0xFFC9CED4.toInt(); c.drawCircle(sh.cx.toFloat(), sh.cy.toFloat(), sh.r.toFloat(), p) }
        }
    }

    // ======================================================== props
    private fun drawProp(c: Canvas, pr: com.ngstudio.inchperfect.game.level.Prop) {
        when (pr.kind) {
            PropKind.TREE -> {
                val r = (1.6 + (pr.variant % 5) * 0.25) * pr.size
                val tree = Sprites.tree(quality.ppm / 2, r.toFloat(), if (level?.weather == Weather.SNOW) 0x3E6B55 else look.hedge, pr.variant)
                if (quality.shadows) { p.color = 0x44000000; c.drawCircle((pr.x + sunX * 1.8).toFloat(), (pr.y + sunY * 1.8).toFloat(), r.toFloat() * 0.95f, p) }
                val ox = (pr.x - camX) * 0.06; val oy = (pr.y - camY) * 0.06
                drawBitmapWorld(c, tree, pr.x + ox, pr.y + oy, pr.a, 2f, 1f)
                if (level?.weather == Weather.SNOW) { p.color = 0x99FFFFFF.toInt(); c.drawCircle((pr.x + ox - r * 0.2).toFloat(), (pr.y + oy - r * 0.2).toFloat(), r.toFloat() * 0.5f, p) }
            }
            PropKind.BUSH -> {
                val tree = Sprites.tree(quality.ppm / 2, (0.8 * pr.size).toFloat(), look.hedge, pr.variant + 2)
                drawBitmapWorld(c, tree, pr.x, pr.y, pr.a, 2f, 1f)
            }
            PropKind.LAMP -> {
                val ox = (pr.x - camX) * 0.09; val oy = (pr.y - camY) * 0.09
                p.color = 0x55000000; c.drawCircle(pr.x.toFloat(), pr.y.toFloat(), 0.22f, p)
                p.color = 0xFF3A3E45.toInt(); p.strokeWidth = 0.12f
                c.drawLine(pr.x.toFloat(), pr.y.toFloat(), (pr.x + ox).toFloat(), (pr.y + oy).toFloat(), p)
                p.color = 0xFF50555C.toInt(); c.drawCircle((pr.x + ox).toFloat(), (pr.y + oy).toFloat(), 0.32f, p)
                p.color = 0xFFFFE6B0.toInt(); c.drawCircle((pr.x + ox).toFloat(), (pr.y + oy).toFloat(), 0.18f, p)
            }
            PropKind.SNOWPILE -> {
                p.color = -1; p.shader = RadialGradient(pr.x.toFloat() - 0.3f, pr.y.toFloat() - 0.3f, 1.4f * pr.size.toFloat(), intArrayOf(0xFFFFFFFF.toInt(), 0xFFDCE8F2.toInt(), 0xFFB8CCDD.toInt()), null, Shader.TileMode.CLAMP)
                c.drawCircle(pr.x.toFloat(), pr.y.toFloat(), 1.1f * pr.size.toFloat(), p); p.shader = null
            }
            else -> {}
        }
    }

    // ====================================================== sensors
    private fun drawSensors(c: Canvas, s: GameSession) {
        if (s.state != SessionState.PLAYING) return
        val w = s.world; val b = w.body()
        fun arcs(dist: Double, front: Boolean) {
            if (dist > 1.3) return
            val lvl = when { dist < 0.3 -> 3; dist < 0.7 -> 2; else -> 1 }
            c.save(); c.translate(b.cx.toFloat(), b.cy.toFloat()); c.rotate(Math.toDegrees(b.a).toFloat() + if (front) 0f else 180f)
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            for (k in 0 until 3) {
                val on = k < lvl
                p.color = intArrayOf(0xFF9BFF3C.toInt(), 0xFFFFC93C.toInt(), 0xFFFF4040.toInt())[k]
                p.alpha = if (on) 230 else 50
                p.strokeWidth = 0.1f
                val r = b.hl.toFloat() * 0.62f + 0.32f + k * 0.24f
                rect.set(b.hl.toFloat() * 0.38f - r, -r, b.hl.toFloat() * 0.38f + r, r)
                c.drawArc(rect, -26f, 52f, false, p)
            }
            p.style = Paint.Style.FILL; p.strokeCap = Paint.Cap.BUTT; p.alpha = 255
            c.restore()
        }
        arcs(w.sensorFront, true)
        arcs(w.sensorRear, false)
    }

    // ===================================================== lighting
    private fun drawLighting(c: Canvas, s: GameSession, l: Level) {
        val dark = when (l.lighting) { Lighting.NIGHT -> 205; Lighting.DUSK -> 110; Lighting.GOLDEN -> 0; Lighting.DAY -> 0 }
        val w = s.world; val b = w.body()
        if (l.lighting == Lighting.GOLDEN || skin == WorldSkin.GOLDEN) {
            p.color = 0x2EFF9A3C; c.drawRect(vis[0].toFloat(), vis[1].toFloat(), vis[2].toFloat(), vis[3].toFloat(), p)
        }
        if (dark == 0) return
        c.saveLayer(vis[0].toFloat(), vis[1].toFloat(), vis[2].toFloat(), vis[3].toFloat(), null)
        p.color = if (l.lighting == Lighting.NIGHT) 0xFF050A1E.toInt() else 0xFF1A1432.toInt()
        p.alpha = dark
        c.drawRect(vis[0].toFloat(), vis[1].toFloat(), vis[2].toFloat(), vis[3].toFloat(), p)
        p.alpha = 255
        // headlights
        fun headlights(cx: Double, cy: Double, a: Double, hl: Double, strength: Int) {
            c.save(); c.translate(cx.toFloat(), cy.toFloat()); c.rotate(Math.toDegrees(a).toFloat())
            val x0 = hl.toFloat()
            clearPaint.shader = RadialGradient(x0, 0f, 11f, intArrayOf((strength shl 24) or 0xFFFFFF, 0x00FFFFFF), floatArrayOf(0.15f, 1f), Shader.TileMode.CLAMP)
            path.reset(); path.moveTo(x0, -0.7f); path.lineTo(x0 + 11f, -4.6f); path.lineTo(x0 + 11f, 4.6f); path.lineTo(x0, 0.7f); path.close()
            c.drawPath(path, clearPaint)
            clearPaint.shader = RadialGradient(0f, 0f, 3.6f, intArrayOf(0xB0FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawCircle(0f, 0f, 3.6f, clearPaint)
            clearPaint.shader = null
            c.restore()
        }
        headlights(b.cx, b.cy, w.car.a, b.hl, 0xF0)
        if (w.car.gear < 0) {
            c.save(); c.translate(b.cx.toFloat(), b.cy.toFloat()); c.rotate(Math.toDegrees(w.car.a).toFloat() + 180f)
            clearPaint.shader = RadialGradient(b.hl.toFloat(), 0f, 4.5f, intArrayOf(0xA0FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawCircle(b.hl.toFloat() + 1f, 0f, 4.5f, clearPaint); clearPaint.shader = null
            c.restore()
        }
        for (lane in l.traffic) lane.carsAt(w.time) { _, x, y -> if (onScreen(x, y, 14.0)) headlights(x, y, lane.angle, lane.carLen / 2, 0xC0) }
        for (pr in l.props) if (pr.kind == PropKind.LAMP && onScreen(pr.x, pr.y, 8.0)) {
            clearPaint.shader = RadialGradient(pr.x.toFloat(), pr.y.toFloat(), 6.5f, intArrayOf(0xE6FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0.1f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(pr.x.toFloat(), pr.y.toFloat(), 6.5f, clearPaint)
        }
        // the bay glows a little
        clearPaint.shader = RadialGradient(l.bay.cx.toFloat(), l.bay.cy.toFloat(), 4f, intArrayOf(0x90FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(l.bay.cx.toFloat(), l.bay.cy.toFloat(), 4f, clearPaint)
        clearPaint.shader = null
        c.restore()
        // additive warm glows
        for (pr in l.props) if (pr.kind == PropKind.LAMP && onScreen(pr.x, pr.y, 8.0)) {
            addPaint.shader = RadialGradient(pr.x.toFloat(), pr.y.toFloat(), 5f, intArrayOf(0x66FFC46B, 0x00FFC46B), null, Shader.TileMode.CLAMP)
            c.drawCircle(pr.x.toFloat(), pr.y.toFloat(), 5f, addPaint)
        }
        c.save(); c.translate(b.cx.toFloat(), b.cy.toFloat()); c.rotate(Math.toDegrees(w.car.a).toFloat())
        addPaint.shader = LinearGradient(b.hl.toFloat(), 0f, b.hl.toFloat() + 9f, 0f, 0x40FFF4D8, 0x00FFF4D8, Shader.TileMode.CLAMP)
        path.reset(); path.moveTo(b.hl.toFloat(), -0.7f); path.lineTo(b.hl.toFloat() + 9f, -3.8f); path.lineTo(b.hl.toFloat() + 9f, 3.8f); path.lineTo(b.hl.toFloat(), 0.7f); path.close()
        c.drawPath(path, addPaint)
        addPaint.shader = null
        c.restore()
    }

    // ==================================================== blueprint skin
    private fun drawBlueprint(c: Canvas, s: GameSession, l: Level) {
        c.drawColor(0xFF0E3A6E.toInt())
        p.color = 0x2299C8FF; p.strokeWidth = 0.03f
        var gx = kotlin.math.floor(vis[0]); while (gx < vis[2]) { c.drawLine(gx.toFloat(), vis[1].toFloat(), gx.toFloat(), vis[3].toFloat(), p); gx += 1.0 }
        var gy = kotlin.math.floor(vis[1]); while (gy < vis[3]) { c.drawLine(vis[0].toFloat(), gy.toFloat(), vis[2].toFloat(), gy.toFloat(), p); gy += 1.0 }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.08f; p.color = 0xEEFFFFFF.toInt()
        for (o in l.obstacles) {
            if (!onScreen(o.shape.cx, o.shape.cy, o.shape.radius)) continue
            when (val sh = o.shape) { is Box -> { boxPath(sh, 0.0); c.drawPath(path, p) }; is Disc -> c.drawCircle(sh.cx.toFloat(), sh.cy.toFloat(), sh.r.toFloat(), p) }
        }
        for (m in l.marks) { boxPath(m.box, 0.0); p.color = 0x88FFFFFF.toInt(); c.drawPath(path, p) }
        p.color = 0xFFFFE27A.toInt(); boxPath(l.bay, 0.0); c.drawPath(path, p)
        for (cs in s.world.cones) c.drawCircle(cs.x.toFloat(), cs.y.toFloat(), 0.24f, p)
        for (lane in l.traffic) lane.carsAt(s.world.time) { _, x, y -> boxPath(lane.carBox(x, y), 0.0); c.drawPath(path, p) }
        p.style = Paint.Style.FILL
        drawCar(c, s.world, s.spec, s.look, 1f, s.bodyShift)
        if (quality.particles) s.particles.draw(c)
        drawSensors(c, s)
    }

    // ================================================ screen overlays
    private fun overlays(c: Canvas, s: GameSession, l: Level) {
        val cam = s.camera
        // weather (screen space)
        if (rainDrops.isNotEmpty()) {
            p.color = 0x66C8DCF0; p.strokeWidth = 2f
            for (i in 0 until rainDrops.size / 3) {
                val sp = 0.9f + rainDrops[i * 3 + 2]
                val yy = ((rainDrops[i * 3 + 1] + time * sp * 1.4f) % 1f) * (cam.viewH + 60) - 30
                val xx = ((rainDrops[i * 3] + time * 0.12f) % 1f) * cam.viewW
                c.drawLine(xx, yy, xx - 8f, yy + 26f * sp, p)
            }
        }
        if (snowFlakes.isNotEmpty()) {
            p.color = 0xDDFFFFFF.toInt()
            for (i in 0 until snowFlakes.size / 3) {
                val sp = 0.3f + snowFlakes[i * 3 + 2] * 0.5f
                val yy = ((snowFlakes[i * 3 + 1] + time * sp * 0.25f) % 1f) * (cam.viewH + 20) - 10
                val xx = ((snowFlakes[i * 3] + 0.03f * sin(time + i)) % 1f) * cam.viewW
                c.drawCircle(xx, yy, 1.5f + snowFlakes[i * 3 + 2] * 2.5f, p)
            }
        }
        // cinematic sunlight grade (warm key light top-left, cool shade bottom-right)
        if (skin != WorldSkin.BLUEPRINT && l.lighting != Lighting.NIGHT) {
            p.color = -1; p.shader = LinearGradient(0f, 0f, cam.viewW, cam.viewH, intArrayOf(0x1CFFD9A0, 0x00000000, 0x1A1A3A80), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, cam.viewW, cam.viewH, p); p.shader = null
        }
        // vignette
        p.color = -1; p.shader = RadialGradient(cam.viewW / 2, cam.viewH / 2, cam.viewW * 0.75f, intArrayOf(0x00000000, 0x00000000, 0x66000000), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, cam.viewW, cam.viewH, p); p.shader = null

        // "P" plate above the bay (screen space so text stays crisp)
        val bx = cam.sx(l.bay.cx); val by = cam.sy(l.bay.cy)
        val inside = bx > cam.safe.left && bx < cam.safe.right && by > cam.safe.top && by < cam.safe.bottom
        val carB = s.world.body()
        val nearBay = hypot(carB.cx - l.bay.cx, carB.cy - l.bay.cy)
        if (s.state == SessionState.PLAYING || s.state == SessionState.INTRO) {
            if (inside && nearBay > 3.2) {
                val r = (cam.zoom * 0.42).toFloat().coerceIn(12f, 30f)
                p.color = 0xE60B1026.toInt(); c.drawRoundRect(bx - r, by - r, bx + r, by + r, r * 0.35f, r * 0.35f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0xFF19E3FF.toInt()
                c.drawRoundRect(bx - r, by - r, bx + r, by + r, r * 0.35f, r * 0.35f, p); p.style = Paint.Style.FILL
                textP.color = 0xFF19E3FF.toInt(); textP.textSize = r * 1.3f
                c.drawText("P", bx, by + r * 0.46f, textP)
            } else if (!inside) {
                // off-screen pointer at the edge of the safe area
                val sx = cam.safe.centerX(); val sy = cam.safe.centerY()
                val ang = atan2((by - sy).toDouble(), (bx - sx).toDouble())
                val rx = cam.safe.width() / 2 - 50; val ry = cam.safe.height() / 2 - 40
                val t = minOf(abs(rx / cos(ang)), abs(ry / sin(ang))).toFloat()
                val ax = sx + cos(ang).toFloat() * t; val ay = sy + sin(ang).toFloat() * t
                c.save(); c.translate(ax, ay)
                p.color = 0xE60B1026.toInt(); c.drawCircle(0f, 0f, 30f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0xFF19E3FF.toInt(); c.drawCircle(0f, 0f, 30f, p); p.style = Paint.Style.FILL
                c.rotate(Math.toDegrees(ang).toFloat())
                path.reset(); path.moveTo(18f, 0f); path.lineTo(-8f, -13f); path.lineTo(-3f, 0f); path.lineTo(-8f, 13f); path.close()
                p.color = 0xFF19E3FF.toInt(); c.drawPath(path, p)
                c.restore()
                val dist = hypot(l.bay.cx - s.world.body().cx, l.bay.cy - s.world.body().cy)
                textP.color = 0xFFFFFFFF.toInt(); textP.textSize = 20f
                c.drawText("${dist.toInt()} m", ax, ay + 54f, textP)
            }
        }
        // rewind flash
        val rf = s.rewindFlash().toFloat()
        if (rf > 0f) { p.color = 0xFF19E3FF.toInt(); p.alpha = (rf * 140).toInt(); c.drawRect(0f, 0f, cam.viewW, cam.viewH, p); p.alpha = 255 }
        if (s.state == SessionState.CRASHED) { p.color = 0x30FF0000; c.drawRect(0f, 0f, cam.viewW, cam.viewH, p) }
        if (s.slowMoLeft > 0) { p.color = 0x2219E3FF; c.drawRect(0f, 0f, cam.viewW, cam.viewH, p) }
    }

    private object Styles {
        val TRAFFIC_COLORS = listOf(0xE9EDF2, 0x2B2F36, 0xB33A3A, 0x3C7DC4, 0xFFC21A, 0x5E7D3A, 0x9AA3AD, 0x7A1F4B)
        val TRUCK_CABS = intArrayOf(0xFF2F5D8A.toInt(), 0xFFB33A3A.toInt(), 0xFF2E7D5B.toInt(), 0xFFE8E8E8.toInt(), 0xFFFF7A1A.toInt(), 0xFF3A3E45.toInt())
        val TRUCK_STRIPES = intArrayOf(0xFF19E3FF.toInt(), 0xFFFF7A1A.toInt(), 0xFFB33A3A.toInt(), 0xFF2E7D5B.toInt())
        val BOAT_DECKS = intArrayOf(0xFFC9A27A.toInt(), 0xFF2F5D8A.toInt(), 0xFFE9EDF2.toInt())
    }
}
