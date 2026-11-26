package com.ngstudio.inchperfect.game.render

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Procedural, seamlessly tiling textures. Generated once per kind/colour and cached.
 * All noise is periodic over the tile so there are no seams.
 */
object Textures {
    const val SIZE = 512
    /** metres covered by one tile of ground texture */
    const val TILE_M = 8.0

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    /** set by the Application: generated textures are cached here as PNG */
    @Volatile var diskDir: java.io.File? = null
    private const val DISK_VERSION = 1
    private val pool = java.util.concurrent.Executors.newFixedThreadPool(maxOf(2, Runtime.getRuntime().availableProcessors() - 1))

    private fun keyOf(kind: GroundKind, base: Int, seed: Int) = "$kind-$base-$seed"

    fun get(kind: GroundKind, base: Int, seed: Int = 7): Bitmap {
        val key = keyOf(kind, base, seed)
        cache[key]?.let { if (!it.isRecycled) return it }
        val bmp = loadOrMake(kind, base, seed, key)
        cache[key] = bmp
        return bmp
    }

    private fun loadOrMake(kind: GroundKind, base: Int, seed: Int, key: String): Bitmap {
        val f = diskDir?.let { java.io.File(it, "${key}_v$DISK_VERSION.png") }
        if (f != null && f.exists()) {
            try { android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.let { return it } } catch (_: Throwable) {}
        }
        val bmp = make(kind, base, seed)
        if (f != null) try {
            f.parentFile?.mkdirs()
            val tmp = java.io.File(f.parentFile, f.name + ".tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            tmp.renameTo(f)
        } catch (_: Throwable) {}
        return bmp
    }

    /** Generates (or loads) several textures in parallel; blocks until done. */
    fun warm(list: List<Triple<GroundKind, Int, Int>>) {
        val futures = list.distinct().filter { cache[keyOf(it.first, it.second, it.third)] == null }.map { (k, c, sd) -> pool.submit<Bitmap> { get(k, c, sd) } }
        futures.forEach { try { it.get() } catch (_: Throwable) {} }
        macro()
    }

    fun macro(): Bitmap = cache.getOrPut("macro") { makeMacro() }

    fun clear() { cache.values.forEach { it.recycle() }; cache.clear() }

    // ------------------------------------------------------------- noise
    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xffff) / 65535f
    }

    /** periodic value noise, period = cells */
    private fun vnoise(x: Float, y: Float, cells: Int, seed: Int): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val xf = x - xi; val yf = y - yi
        val x0 = Math.floorMod(xi, cells); val y0 = Math.floorMod(yi, cells)
        val x1 = (x0 + 1) % cells; val y1 = (y0 + 1) % cells
        val u = xf * xf * (3 - 2 * xf); val v = yf * yf * (3 - 2 * yf)
        val a = hash(x0, y0, seed); val b = hash(x1, y0, seed); val c = hash(x0, y1, seed); val d = hash(x1, y1, seed)
        return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v
    }

    /** fractal noise in 0..1, tileable over SIZE pixels */
    private fun fbm(px: Int, py: Int, baseCells: Int, octaves: Int, seed: Int): Float {
        var amp = 0.5f; var sum = 0f; var norm = 0f; var cells = baseCells
        for (o in 0 until octaves) {
            val x = px.toFloat() / SIZE * cells; val y = py.toFloat() / SIZE * cells
            sum += vnoise(x, y, cells, seed + o * 31) * amp
            norm += amp; amp *= 0.5f; cells *= 2
        }
        return sum / norm
    }

    private fun r(c: Int) = (c shr 16) and 0xff
    private fun g(c: Int) = (c shr 8) and 0xff
    private fun b(c: Int) = c and 0xff
    private fun rgb(r: Float, g: Float, b: Float): Int =
        (0xff shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)

    private fun shade(c: Int, f: Float) = rgb(r(c) * f, g(c) * f, b(c) * f)
    private fun mix(c1: Int, c2: Int, t: Float) = rgb(r(c1) + (r(c2) - r(c1)) * t, g(c1) + (g(c2) - g(c1)) * t, b(c1) + (b(c2) - b(c1)) * t)

    // ------------------------------------------------------------ makers
    private fun make(kind: GroundKind, base: Int, seed: Int): Bitmap {
        val px = IntArray(SIZE * SIZE)
        when (kind) {
            GroundKind.ASPHALT -> asphalt(px, base, seed)
            GroundKind.CONCRETE -> concrete(px, base, seed, 128)
            GroundKind.EPOXY -> epoxy(px, base, seed)
            GroundKind.COBBLE -> cobble(px, base, seed)
            GroundKind.GRASS -> grass(px, base, seed)
            GroundKind.SAND -> sand(px, base, seed)
            GroundKind.SNOW -> snow(px, base, seed)
            GroundKind.WOOD -> wood(px, base, seed)
            GroundKind.PLATE -> plate(px, base, seed)
            GroundKind.PAVERS -> pavers(px, base, seed)
            GroundKind.WATER -> water(px, base, seed)
        }
        return Bitmap.createBitmap(px, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    private fun asphalt(px: IntArray, base: Int, seed: Int) {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val n = fbm(x, y, 8, 5, seed)
            val fine = hash(x, y, seed + 99)
            var f = 0.86f + n * 0.26f
            // aggregate: bright and dark specks
            if (fine > 0.965f) f += 0.18f * ((fine - 0.965f) / 0.035f)
            else if (fine < 0.05f) f -= 0.14f
            val grain = (hash(x / 2, y / 2, seed + 5) - 0.5f) * 0.05f
            px[y * SIZE + x] = shade(base, f + grain)
        }
        // a few larger pebbles
        val rnd = java.util.Random(seed.toLong())
        repeat(260) {
            val cx = rnd.nextInt(SIZE); val cy = rnd.nextInt(SIZE); val rr = 1 + rnd.nextInt(2)
            val f = if (rnd.nextBoolean()) 1.25f else 0.8f
            for (dy in -rr..rr) for (dx in -rr..rr) {
                if (dx * dx + dy * dy > rr * rr) continue
                val i = Math.floorMod(cy + dy, SIZE) * SIZE + Math.floorMod(cx + dx, SIZE)
                px[i] = shade(px[i], f)
            }
        }
    }

    private fun concrete(px: IntArray, base: Int, seed: Int, panel: Int) {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val n = fbm(x, y, 4, 5, seed)
            val fine = hash(x, y, seed + 3)
            var f = 0.9f + n * 0.18f + (fine - 0.5f) * 0.05f
            val sx = x % panel; val sy = y % panel
            if (sx < 2 || sy < 2) f *= 0.78f
            else if (sx < 4 || sy < 4) f *= 1.06f
            px[y * SIZE + x] = shade(base, f)
        }
    }

    private fun epoxy(px: IntArray, base: Int, seed: Int) {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val n = fbm(x, y, 2, 3, seed)
            val fine = hash(x, y, seed + 7)
            var f = 0.95f + n * 0.1f
            if (fine > 0.985f) f += 0.25f
            px[y * SIZE + x] = shade(base, f)
        }
    }

    private fun cobble(px: IntArray, base: Int, seed: Int) {
        val cells = 16
        val pts = Array(cells * cells) { i -> floatArrayOf((i % cells + 0.2f + hash(i, 1, seed) * 0.6f), (i / cells + 0.2f + hash(i, 2, seed) * 0.6f)) }
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val fx = x.toFloat() / SIZE * cells; val fy = y.toFloat() / SIZE * cells
            val cx = floor(fx).toInt(); val cy = floor(fy).toInt()
            var d1 = 9f; var d2 = 9f; var id = 0
            for (oy in -1..1) for (ox in -1..1) {
                val gx = cx + ox; val gy = cy + oy
                val p = pts[Math.floorMod(gy, cells) * cells + Math.floorMod(gx, cells)]
                val px0 = gx + (p[0] - floor(p[0])); val py0 = gy + (p[1] - floor(p[1]))
                val dx = fx - px0; val dy = fy - py0
                val d = sqrt(dx * dx + dy * dy)
                if (d < d1) { d2 = d1; d1 = d; id = Math.floorMod(gy, cells) * cells + Math.floorMod(gx, cells) } else if (d < d2) d2 = d
            }
            val edge = d2 - d1
            val stone = 0.85f + hash(id, 9, seed) * 0.3f
            val n = fbm(x, y, 16, 3, seed)
            var f = stone * (0.92f + n * 0.16f) * (1.08f - d1 * 0.35f)
            if (edge < 0.07f) f *= 0.45f + edge * 6f
            px[y * SIZE + x] = shade(base, f)
        }
    }

    private fun grass(px: IntArray, base: Int, seed: Int) {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val n = fbm(x, y, 6, 5, seed)
            val fine = hash(x, y, seed + 4)
            val f = 0.78f + n * 0.4f + (fine - 0.5f) * 0.12f
            px[y * SIZE + x] = mix(shade(base, f), 0x9BC45A, (fine * 0.15f).coerceIn(0f, 0.12f))
        }
        val rnd = java.util.Random(seed.toLong())
        repeat(5200) {
            val x0 = rnd.nextInt(SIZE); val y0 = rnd.nextInt(SIZE)
            val len = 3 + rnd.nextInt(5)
            val dx = rnd.nextFloat() - 0.5f
            val col = if (rnd.nextBoolean()) shade(base, 1.25f) else shade(base, 0.7f)
            for (k in 0 until len) {
                val xx = Math.floorMod((x0 + dx * k).toInt(), SIZE); val yy = Math.floorMod(y0 - k, SIZE)
                px[yy * SIZE + xx] = mix(px[yy * SIZE + xx], col, 0.6f)
            }
        }
    }

    private fun sand(px: IntArray, base: Int, seed: Int) {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val n = fbm(x, y, 4, 4, seed)
            val w = fbm(x, y, 2, 2, seed + 50)
            val ripple = kotlin.math.sin((y / SIZE.toFloat() * 24 + w * 6) * 2 * Math.PI.toFloat()) * 0.04f
            val fine = hash(x, y, seed + 1)
            px[y * SIZE + x] = shade(base, 0.9f + n * 0.18f + ripple + (fine - 0.5f) * 0.08f)
        }
    }

    private fun snow(px: IntArray, base: Int, seed: Int) {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val n = fbm(x, y, 4, 5, seed)
            val fine = hash(x, y, seed + 2)
            var c = shade(base, 0.92f + n * 0.1f)
            c = mix(c, 0xB8D4EC, (0.5f - n).coerceIn(0f, 0.5f) * 0.5f)
            if (fine > 0.992f) c = 0xFFFFFFFF.toInt()
            px[y * SIZE + x] = c
        }
    }

    private fun wood(px: IntArray, base: Int, seed: Int) {
        val plank = 32
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val row = y / plank
            val off = (hash(row, 0, seed) * SIZE).toInt()
            val grain = vnoise((x + off).toFloat() / SIZE * 4, y.toFloat() / SIZE * 64, 64, seed) * 0.5f +
                vnoise((x + off).toFloat() / SIZE * 16, y.toFloat() / SIZE * 128, 128, seed + 3) * 0.5f
            var f = 0.82f + grain * 0.3f + (hash(row, 1, seed) - 0.5f) * 0.12f
            if (y % plank < 2) f *= 0.55f
            if ((x + off) % 256 < 2) f *= 0.6f
            px[y * SIZE + x] = shade(base, f)
        }
    }

    private fun plate(px: IntArray, base: Int, seed: Int) {
        val cell = 32
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val n = fbm(x, y, 4, 3, seed)
            val lx = (x % cell) - cell / 2f; val ly = (y % cell) - cell / 2f
            // two diagonal raised bars per cell (diamond plate)
            val d1 = kotlin.math.abs(lx - ly * 0.0f + (if ((x / cell + y / cell) % 2 == 0) ly else -ly))
            var f = 0.9f + n * 0.12f
            if (d1 < 2.5f && kotlin.math.abs(lx) < 9) f += 0.22f - d1 * 0.04f
            px[y * SIZE + x] = shade(base, f)
        }
    }

    private fun pavers(px: IntArray, base: Int, seed: Int) {
        val bw = 64; val bh = 32
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val row = y / bh
            val xx = x + if (row % 2 == 0) 0 else bw / 2
            val bx = Math.floorMod(xx, SIZE) / bw
            val n = fbm(x, y, 8, 3, seed)
            var f = 0.88f + hash(bx, row, seed) * 0.18f + n * 0.08f
            val ex = xx % bw; val ey = y % bh
            if (ex < 2 || ey < 2) f *= 0.6f else if (ex < 3 || ey < 3) f *= 1.08f
            px[y * SIZE + x] = shade(base, f)
        }
    }

    private fun water(px: IntArray, base: Int, seed: Int) {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val n = fbm(x, y, 4, 5, seed)
            val m = fbm(x, y, 16, 2, seed + 9)
            px[y * SIZE + x] = mix(shade(base, 0.85f + n * 0.3f), 0x7FD8F0, (m - 0.6f).coerceIn(0f, 0.4f) * 0.6f)
        }
    }

    /** large-scale brightness variation (alpha only) to break up tiling */
    private fun makeMacro(): Bitmap {
        val s = 128
        val px = IntArray(s * s)
        for (y in 0 until s) for (x in 0 until s) {
            var amp = 0.5f; var sum = 0f; var norm = 0f; var cells = 3
            for (o in 0 until 4) {
                sum += vnoise(x.toFloat() / s * cells, y.toFloat() / s * cells, cells, 77 + o) * amp
                norm += amp; amp *= 0.5f; cells *= 2
            }
            val v = sum / norm
            val dark = ((0.55f - v).coerceAtLeast(0f) * 110).toInt().coerceIn(0, 60)
            px[y * s + x] = (dark shl 24)
        }
        return Bitmap.createBitmap(px, s, s, Bitmap.Config.ARGB_8888)
    }
}
