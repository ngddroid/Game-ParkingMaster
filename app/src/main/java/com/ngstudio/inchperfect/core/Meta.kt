package com.ngstudio.inchperfect.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class MissionKind(val text: String) {
    PARK("Park %d times"), THREE_STARS("Earn 3 stars on %d levels"), PERFECT("Score %d Inch Perfect parks"),
    REVERSE_M("Drive %d m in reverse"), CLEAN("Finish %d levels without touching a cone"),
}

data class Mission(val kind: MissionKind, val target: Int, val reward: Int, val progress: Int = 0, val claimed: Boolean = false) {
    val done get() = progress >= target
    val title get() = kind.text.format(target)
}

object Missions {
    fun roll(day: Long): List<Mission> {
        val r = java.util.Random(day * 31 + 7)
        val kinds = MissionKind.entries.shuffled(r).take(3)
        return kinds.map { k ->
            when (k) {
                MissionKind.PARK -> Mission(k, 5 + r.nextInt(6), 150)
                MissionKind.THREE_STARS -> Mission(k, 2 + r.nextInt(3), 200)
                MissionKind.PERFECT -> Mission(k, 1 + r.nextInt(2), 250)
                MissionKind.REVERSE_M -> Mission(k, 40 + r.nextInt(5) * 20, 150)
                MissionKind.CLEAN -> Mission(k, 3 + r.nextInt(3), 180)
            }
        }
    }

    fun load(prefs: SharedPreferences, day: Long, force: Boolean = false): List<Mission> {
        if (force || prefs.getLong("m_day", -1) != day) {
            val list = roll(day)
            prefs.edit().putLong("m_day", day).apply()
            save(prefs, list)
            return list
        }
        val raw = prefs.getString("m_list", null) ?: return roll(day)
        return raw.split(";").mapNotNull { e ->
            val f = e.split(":")
            if (f.size < 5) null else Mission(MissionKind.valueOf(f[0]), f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4] == "1")
        }
    }

    fun save(prefs: SharedPreferences, list: List<Mission>) {
        prefs.edit().putString("m_list", list.joinToString(";") { "${it.kind.name}:${it.target}:${it.reward}:${it.progress}:${if (it.claimed) 1 else 0}" }).apply()
    }

    fun progress(repo: GameRepo, kind: MissionKind, n: Int) {
        if (n <= 0) return
        repo.setMissions(repo.missions.value.map { if (it.kind == kind && !it.claimed) it.copy(progress = minOf(it.target, it.progress + n)) else it })
    }

    fun claim(repo: GameRepo, idx: Int): Int {
        val list = repo.missions.value
        val m = list.getOrNull(idx) ?: return 0
        if (!m.done || m.claimed) return 0
        repo.setMissions(list.mapIndexed { i, x -> if (i == idx) x.copy(claimed = true) else x })
        repo.addCoins(m.reward)
        return m.reward
    }
}

data class Achievement(val id: String, val title: String, val desc: String, val reward: Int, val test: (GameRepo) -> Boolean)

object Achievements {
    val ALL: List<Achievement> = buildList {
        for ((n, t) in listOf(1 to "First Park", 10 to "Rookie Graduate", 50 to "Lot Regular", 100 to "Century", 250 to "Quarter Master",
            500 to "District Hero", 1000 to "Thousandaire", 2500 to "Unstoppable", 5000 to "Halfway Legend", 10000 to "Inch Perfect Legend")) {
            add(Achievement("clear_$n", t, "Clear $n levels", 50 + n / 5) { it.cleared >= n })
        }
        for ((n, t) in listOf(30 to "Star Collector", 150 to "Star Hoarder", 600 to "Constellation", 3000 to "Galaxy", 15000 to "Supernova")) {
            add(Achievement("stars_$n", t, "Collect $n stars", 100 + n / 10) { it.totalStars >= n })
        }
        for ((n, t) in listOf(1 to "Inch Perfect!", 25 to "Laser Guided", 100 to "Surgeon", 500 to "Robot Precision")) {
            add(Achievement("perfect_$n", t, "Score $n Inch Perfect parks", 80 + n) { it.stats.value.perfects >= n })
        }
        for ((n, t) in listOf(100 to "Backseat Driver", 1000 to "Reverse Pro", 10000 to "Moonwalker")) {
            add(Achievement("rev_$n", t, "Reverse $n metres", 60 + n / 20) { it.stats.value.reverse >= n })
        }
        for ((n, t) in listOf(2 to "Second Ride", 5 to "Collector", 12 to "Full Garage")) {
            add(Achievement("cars_$n", t, "Own $n cars", 150 * n) { it.profile.value.ownedCars.size >= n })
        }
        add(Achievement("rich_10k", "Deep Pockets", "Hold 10,000 coins", 300) { it.profile.value.coins >= 10_000 })
        add(Achievement("skin_1", "New Look", "Buy a World Skin", 200) { it.profile.value.ownedSkins.size >= 2 })
        add(Achievement("crash_1", "Oops", "Crash once (it happens!)", 20) { it.stats.value.crashes >= 1 })
        add(Achievement("dist_10k", "Road Trip", "Drive 10 km in total", 400) { it.stats.value.distance >= 10_000 })
        add(Achievement("rewind_10", "Time Bender", "Use Rewind 10 times", 150) { it.stats.value.rewinds >= 10 })
    }

    fun check(repo: GameRepo): List<Achievement> {
        val got = repo.achievements.value
        val new = ALL.filter { it.id !in got && it.test(repo) }
        for (a in new) { repo.unlockAchievement(a.id); repo.addCoins(a.reward) }
        return new
    }
}

/** Short vibration cues. Respects the Haptics setting. */
object Haptics {
    private var vib: Vibrator? = null
    fun init(ctx: Context) {
        vib = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun tick(ms: Long, amp: Int) {
        if (!GameRepo.settings.value.haptics) return
        val v = vib ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, amp.coerceIn(1, 255)))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        } catch (_: Exception) {}
    }

    fun light() = tick(10, 60)
    fun medium() = tick(22, 140)
    fun heavy() = tick(70, 255)
}
