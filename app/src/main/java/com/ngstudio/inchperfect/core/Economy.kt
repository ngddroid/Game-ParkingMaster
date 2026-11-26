package com.ngstudio.inchperfect.core

import com.ngstudio.inchperfect.game.render.Decal
import com.ngstudio.inchperfect.game.render.Finish

/** All prices and rewards live here so the balance is easy to tune (and unit-test). */
object Economy {
    fun baseReward(level: Int): Int = (20 + level / 50).coerceAtMost(120)

    data class Reward(val coins: Int, val breakdown: List<Pair<String, Int>>)

    fun levelReward(level: Int, stars: Int, perfect: Boolean, coinPickups: Int, firstClear: Boolean, improved: Boolean): Reward {
        val base = baseReward(level)
        val starMul = when (stars) { 3 -> 2.0; 2 -> 1.5; else -> 1.0 }
        var coins = base * starMul
        val parts = ArrayList<Pair<String, Int>>()
        parts.add("Parked" to base)
        if (stars > 1) parts.add("$stars stars" to (base * starMul - base).toInt())
        if (perfect) { parts.add("Inch Perfect" to (coins * 0.25).toInt()); coins *= 1.25 }
        val bossMul = if (level % 100 == 0) 3.0 else if (level % 10 == 0) 2.0 else 1.0
        if (bossMul > 1) { parts.add((if (bossMul > 2) "Master level x3" else "Boss level x2") to (coins * (bossMul - 1)).toInt()); coins *= bossMul }
        if (!firstClear && !improved) { coins *= 0.2; parts.clear(); parts.add("Replay" to coins.toInt()) }
        val pick = coinPickups * 5
        if (pick > 0) parts.add("Pickups" to pick)
        return Reward(coins.toInt() + pick, parts)
    }

    val PAINTS: List<Int> = listOf(
        0xFF7A1A, 0x19E3FF, 0x9BFF3C, 0xFF3DA5, 0xF2F5FF, 0x1B1F3B, 0xD7263D, 0xFFC21A, 0x2E7D5B, 0x7B61FF, 0x5A6B7D, 0xE8C15A,
        0x00B3A4, 0xFF5E5B, 0x3A86FF, 0x8338EC, 0xFB5607, 0x06D6A0,
    )
    fun paintPrice(i: Int) = if (i < 4) 0 else 300 + (i - 4) * 90

    fun finishStars(f: Finish) = when (f) { Finish.SOLID -> 0; Finish.METALLIC -> 40; Finish.MATTE -> 80; Finish.PEARL -> 150; Finish.CHROME -> 300 }
    fun decalPrice(d: Decal) = when (d) { Decal.NONE -> 0; Decal.STRIPES -> 800; Decal.TWO_TONE -> 1200; Decal.CHECKER -> 1500; Decal.BOLT -> 1800; Decal.NUMBER -> 1000; Decal.CARBON -> 2500 }

    val UNDERGLOWS: List<Int> = listOf(0, 0x19E3FF, 0xFF3DA5, 0x9BFF3C, 0xFF7A1A, 0x7B61FF, 0xFFFFFF)
    fun underglowPrice(i: Int) = if (i == 0) 0 else 1500 + i * 250

    const val REWIND_PRICE = 150
    const val GHOST_PRICE = 250
    const val SLOWMO_PRICE = 100
    const val CHEST_PRICE = 2_000

    val LOGIN_REWARDS = intArrayOf(100, 150, 200, 300, 400, 500, 1000)

    /** Lucky spin wheel: label to (coins, booster) */
    data class SpinPrize(val label: String, val coins: Int, val booster: String? = null, val weight: Double)
    val SPIN = listOf(
        SpinPrize("50", 50, null, 3.0), SpinPrize("100", 100, null, 2.5), SpinPrize("Rewind", 0, "rewind", 1.5), SpinPrize("250", 250, null, 1.2),
        SpinPrize("Ghost", 0, "ghost", 1.0), SpinPrize("500", 500, null, 0.6), SpinPrize("Slow-mo", 0, "slowmo", 1.2), SpinPrize("1000", 1000, null, 0.25),
    )

    fun rankTitle(cleared: Int) = when {
        cleared >= 5000 -> "Legend"; cleared >= 2000 -> "Grand Master"; cleared >= 1000 -> "Master Valet"
        cleared >= 500 -> "Ace Driver"; cleared >= 200 -> "Pro Valet"; cleared >= 50 -> "Valet"; else -> "Rookie Valet"
    }
}

/** Accent palettes for the whole UI (Store > UI Themes). */
data class UiTheme(val id: String, val title: String, val price: Int, val primary: Long, val secondary: Long, val bgTop: Long, val bgBottom: Long)

object UiThemes {
    val ALL = listOf(
        UiTheme("midnight", "Midnight", 0, 0xFF19E3FF, 0xFFFF7A1A, 0xFF141B3D, 0xFF070A1A),
        UiTheme("sunset", "Sunset Drive", 3_000, 0xFFFF7A1A, 0xFFFF3DA5, 0xFF3A1540, 0xFF120818),
        UiTheme("mint", "Mint Fresh", 3_000, 0xFF2EF2B0, 0xFF19E3FF, 0xFF0D2E33, 0xFF061416),
        UiTheme("royal", "Royal Velvet", 3_000, 0xFFB08CFF, 0xFFE8C15A, 0xFF22163F, 0xFF0D0820),
        UiTheme("carbon", "Carbon Gold", 3_000, 0xFFE8C15A, 0xFFFF7A1A, 0xFF22262E, 0xFF0B0C10),
    )
    fun of(id: String) = ALL.firstOrNull { it.id == id } ?: ALL[0]
}
