package com.ngstudio.inchperfect

import com.ngstudio.inchperfect.game.level.Level
import com.ngstudio.inchperfect.game.level.LevelGenerator
import com.ngstudio.inchperfect.game.level.ObKind
import com.ngstudio.inchperfect.game.math.Box
import com.ngstudio.inchperfect.game.math.Disc
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Debug tool: renders top-down PNG previews. ./gradlew testDebugUnitTest --tests '*LevelPreviewTest*' -Ppreview=1,2,3 */
class LevelPreviewTest {
    @Test
    fun renderPreviews() {
        val prop = System.getProperty("preview").orEmpty().trim()
        if (prop.isEmpty()) return
        val ids = prop.split(",").flatMap { part ->
            if ("-" in part) { val (a, b) = part.split("-").map { it.toInt() }; (a..b).toList() } else listOf(part.toInt())
        }
        val dir = File("build/previews").apply { mkdirs() }
        for (n in ids) {
            val l = LevelGenerator.generate(n)
            ImageIO.write(render(l), "png", File(dir, "L%05d.png".format(n)))
            println("preview L$n -> ${File(dir, "L%05d.png".format(n)).absolutePath}  obstacles=${l.obstacles.size} size=%.0fx%.0f t3=%.1f D=%.2f mech=${l.mechanics} attempts=${l.attempts}".format(l.width, l.height, l.t3, l.tightness))
        }
    }

    private fun boxPath(b: Box, s: Double, ox: Double, oy: Double): Path2D.Double {
        val c = b.corners()
        val p = Path2D.Double()
        p.moveTo((c[0] - ox) * s, (c[1] - oy) * s)
        for (k in 1 until 4) p.lineTo((c[k * 2] - ox) * s, (c[k * 2 + 1] - oy) * s)
        p.closePath()
        return p
    }

    fun render(l: Level): BufferedImage {
        val maxW = 1400.0
        val s = minOf(maxW / l.width, 900.0 / l.height)
        val w = (l.width * s).toInt() + 1; val h = (l.height * s).toInt() + 40
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x2a2d33); g.fillRect(0, 0, w, h)
        val ox = l.minX; val oy = l.minY
        for (z in l.zones) { g.color = Color(0x3a3f47); g.fill(boxPath(z.box, s, ox, oy)) }
        g.color = Color(0xbbbbbb)
        for (m in l.marks) g.fill(boxPath(Box(m.box.cx, m.box.cy, maxOf(m.box.hl, 0.06), maxOf(m.box.hw, 0.06), m.box.a), s, ox, oy))
        for (o in l.obstacles) {
            g.color = when (o.kind) {
                ObKind.CAR -> Color(if (o.color != 0) o.color else 0x8899aa)
                ObKind.CONE -> Color(0xff7a1a)
                ObKind.BUILDING -> Color(0x6b5b95)
                ObKind.HEDGE -> Color(0x3d8b40)
                ObKind.BARRIER -> Color(0xc0c0c0)
                ObKind.SIDEWALK -> Color(0x8d8d8d)
                ObKind.TRUCK, ObKind.CONTAINER -> Color(0x2f7f9f)
                ObKind.PILLAR -> Color(0xdddd55)
                ObKind.PLANTER -> Color(0x5aa05a)
                else -> Color(0xa07050)
            }
            when (val sh = o.shape) {
                is Box -> g.fill(boxPath(sh, s, ox, oy))
                is Disc -> g.fill(Ellipse2D.Double((sh.cx - sh.r - ox) * s, (sh.cy - sh.r - oy) * s, sh.r * 2 * s, sh.r * 2 * s))
            }
        }
        for (lane in l.traffic) {
            g.color = Color(0xff4444); g.stroke = BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(6f, 6f), 0f)
            g.drawLine(((lane.x0 - ox) * s).toInt(), ((lane.y0 - oy) * s).toInt(), ((lane.x1 - ox) * s).toInt(), ((lane.y1 - oy) * s).toInt())
        }
        g.stroke = BasicStroke(1f)
        for (gt in l.gates) for (d in gt.bollards) { g.color = Color(0xcc44ff); g.fill(Ellipse2D.Double((d.cx - d.r - ox) * s, (d.cy - d.r - oy) * s, d.r * 2 * s, d.r * 2 * s)) }
        for (t in l.turntables) { g.color = Color(0x44ccff); g.draw(Ellipse2D.Double((t.cx - t.r - ox) * s, (t.cy - t.r - oy) * s, t.r * 2 * s, t.r * 2 * s)) }
        // reference path
        g.color = Color(0x19e3ff); g.stroke = BasicStroke(2f)
        val rp = l.refPath
        for (i in 0 until rp.size / 2 - 1) g.drawLine(((rp[i * 2] - ox) * s).toInt(), ((rp[i * 2 + 1] - oy) * s).toInt(), ((rp[i * 2 + 2] - ox) * s).toInt(), ((rp[i * 2 + 3] - oy) * s).toInt())
        // bay
        g.color = Color(0xffd21a); g.stroke = BasicStroke(2f)
        g.draw(boxPath(l.bay, s, ox, oy))
        val ax = l.bay.cx + l.bay.c * l.bay.hl * 0.8; val ay = l.bay.cy + l.bay.s * l.bay.hl * 0.8
        g.drawLine(((l.bay.cx - ox) * s).toInt(), ((l.bay.cy - oy) * s).toInt(), ((ax - ox) * s).toInt(), ((ay - oy) * s).toInt())
        // start car
        val st = l.start
        val car = Box(st.x + Math.cos(st.a) * 1.35, st.y + Math.sin(st.a) * 1.35, 2.25, 0.95, st.a)
        g.color = Color(0x9bff3c); g.fill(boxPath(car, s, ox, oy))
        for (c in l.coins) { g.color = Color(0xffcc00); g.fill(Ellipse2D.Double((c[0] - 0.4 - ox) * s, (c[1] - 0.4 - oy) * s, 0.8 * s, 0.8 * s)) }
        // simulate autopilot and overlay the driven trajectory
        val wsim = com.ngstudio.inchperfect.game.sim.World(l, com.ngstudio.inchperfect.game.physics.Cars.REF)
        val ap = com.ngstudio.inchperfect.game.sim.Autopilot(wsim)
        var t = 0.0; var px0 = Double.NaN; var py0 = 0.0; var info = ""
        g.color = Color(0xff3355); g.stroke = BasicStroke(1.2f)
        while (t < 300 && wsim.phase == com.ngstudio.inchperfect.game.sim.Phase.PLAYING) {
            ap.update(1.0 / 120); wsim.step(1.0 / 120); t += 1.0 / 120
            val b = wsim.body()
            if (!px0.isNaN()) g.drawLine(((px0 - ox) * s).toInt(), ((py0 - oy) * s).toInt(), ((b.cx - ox) * s).toInt(), ((b.cy - oy) * s).toInt())
            px0 = b.cx; py0 = b.cy
            if (ap.done && Math.abs(wsim.car.v) < 1e-4) break
        }
        if (wsim.phase == com.ngstudio.inchperfect.game.sim.Phase.CRASHED) {
            g.color = Color.RED; g.draw(boxPath(wsim.body(), s, ox, oy))
            g.fill(Ellipse2D.Double((wsim.crashX - 0.5 - ox) * s, (wsim.crashY - 0.5 - oy) * s, s, s))
            info = " CRASH ${wsim.crashedInto} step ${ap.idx} t=%.1f".format(t)
            println("L${l.number} steps: " + l.steps.mapIndexed { i, st -> "$i:${st.type}/g${st.gear}/s%.2f/d%.2f".format(st.steer, st.dist) }.joinToString(" "))
        } else info = " ${wsim.phase} t=%.1f".format(wsim.runTime)
        g.color = Color.WHITE; g.font = Font("SansSerif", Font.BOLD, 14)
        g.drawString(info, 8, 18)
        g.drawString("L${l.number} ${l.name}  D=%.2f c=%.2f  ${l.lighting} ${l.weather} ${l.mechanics}".format(l.tightness, l.clearance), 8, h - 14)
        g.dispose()
        return img
    }
}
