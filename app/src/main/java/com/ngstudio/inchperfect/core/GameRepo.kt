package com.ngstudio.inchperfect.core

import android.content.Context
import android.content.SharedPreferences
import com.ngstudio.inchperfect.game.input.ControlsConfig
import com.ngstudio.inchperfect.game.input.Scheme
import com.ngstudio.inchperfect.game.level.LEVEL_COUNT
import com.ngstudio.inchperfect.game.physics.Cars
import com.ngstudio.inchperfect.game.render.CarLook
import com.ngstudio.inchperfect.game.render.Decal
import com.ngstudio.inchperfect.game.render.Finish
import com.ngstudio.inchperfect.game.render.Quality
import com.ngstudio.inchperfect.game.render.WorldSkin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.time.LocalDate
import java.util.BitSet

data class Settings(
    val music: Float = 0.55f,
    val sfx: Float = 0.9f,
    val engine: Float = 0.8f,
    val haptics: Boolean = true,
    val scheme: Scheme = Scheme.WHEEL,
    val leftHanded: Boolean = false,
    val ctrlSize: Float = 1f,
    val ctrlOpacity: Float = 0.92f,
    val sensitivity: Float = 1f,
    val autoCenter: Boolean = true,
    val quality: String = "auto",
    val fps: Int = 60,
    val sensors: Boolean = true,
    val showFps: Boolean = false,
) {
    fun controls(wheelId: String = GameRepo.profile.value.selectedWheel) =
        ControlsConfig(scheme, leftHanded, ctrlSize, ctrlOpacity, sensitivity, autoCenter, com.ngstudio.inchperfect.game.input.Wheels.byId(wheelId))
}

data class CarCustom(val color: Int, val finish: Finish = Finish.SOLID, val decal: Decal = Decal.NONE, val accent: Int = 0xFFFFFF, val glow: Int = 0)

data class Profile(
    val coins: Int = 250,
    val starsSpent: Int = 0,
    val selectedCar: String = "rookie",
    val ownedCars: Set<String> = setOf("rookie"),
    val custom: Map<String, CarCustom> = emptyMap(),
    val ownedPaints: Set<Int> = setOf(0, 1, 2, 3),
    val ownedFinishes: Set<String> = setOf("solid"),
    val ownedDecals: Set<String> = setOf("none"),
    val ownedGlows: Set<Int> = setOf(0),
    val skin: String = "classic",
    val ownedSkins: Set<String> = setOf("classic"),
    val uiTheme: String = "midnight",
    val ownedThemes: Set<String> = setOf("midnight"),
    val rewind: Int = 2,
    val ghost: Int = 1,
    val slowmo: Int = 1,
    val onboardingDone: Boolean = false,
    val loginDay: Long = 0,
    val loginStreak: Int = 0,
    val loginClaimedDay: Long = 0,
    val spinDay: Long = 0,
    val challengeDay: Long = 0,
    val chestStars: Int = 0,
    val selectedWheel: String = "classic",
    val ownedWheels: Set<String> = setOf("classic"),
) {
    fun customFor(id: String) = custom[id] ?: CarCustom(Cars.byId(id).defaultColor)
    fun look(): CarLook = lookFor(selectedCar)
    fun lookFor(id: String): CarLook {
        val spec = Cars.byId(id)
        val c = customFor(id)
        return CarLook(spec.design, c.color, c.finish, c.decal, c.accent, axleR = spec.axleRear, axleF = spec.axleFront)
    }
}

data class Stats(
    val parks: Int = 0, val perfects: Int = 0, val crashes: Int = 0, val cones: Int = 0, val distance: Double = 0.0,
    val reverse: Double = 0.0, val coinsEarned: Int = 0, val threeStars: Int = 0, val rewinds: Int = 0, val playSeconds: Double = 0.0,
)

/** Single source of truth for everything saved on the device (offline, no network). */
object GameRepo {
    private lateinit var prefs: SharedPreferences
    private lateinit var progressFile: File
    val stars = ByteArray(LEVEL_COUNT + 1)
    val best = FloatArray(LEVEL_COUNT + 1)
    private val adUnlocked = BitSet(LEVEL_COUNT + 1)

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings
    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile
    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats
    /** bumps whenever level progress changes (so Compose re-reads the arrays) */
    private val _progressVersion = MutableStateFlow(0)
    val progressVersion: StateFlow<Int> = _progressVersion
    private val _missions = MutableStateFlow<List<Mission>>(emptyList())
    val missions: StateFlow<List<Mission>> = _missions
    private val _achievements = MutableStateFlow<Set<String>>(emptySet())
    val achievements: StateFlow<Set<String>> = _achievements

    var totalStars = 0
        private set
    var cleared = 0
        private set

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("inch_perfect", Context.MODE_PRIVATE)
        progressFile = File(ctx.filesDir, "progress.bin")
        loadProgress()
        _settings.value = readSettings()
        _profile.value = sanitize(readProfile())
        writeProfile(_profile.value)
        _stats.value = readStats()
        _achievements.value = prefs.getStringSet("ach", emptySet())!!.toSet()
        _missions.value = Missions.load(prefs, today())
    }

    fun today(): Long = LocalDate.now().toEpochDay()

    /** Drops items that no longer exist (e.g. retired skins/wheels) and repairs selections. */
    private fun sanitize(p: Profile): Profile {
        val skins = p.ownedSkins.filter { id -> WorldSkin.entries.any { it.id == id } }.toSet() + "classic"
        val wheels = p.ownedWheels.filter { id -> com.ngstudio.inchperfect.game.input.Wheels.ALL.any { it.id == id } }.toSet() + "classic"
        val cars = p.ownedCars.filter { id -> Cars.ALL.any { it.id == id } }.toSet() + "rookie"
        return p.copy(
            ownedSkins = skins, skin = if (p.skin in skins) p.skin else "classic",
            ownedWheels = wheels, selectedWheel = if (p.selectedWheel in wheels) p.selectedWheel else "classic",
            ownedCars = cars, selectedCar = if (p.selectedCar in cars) p.selectedCar else "rookie",
        )
    }

    // ------------------------------------------------------------ progress
    private fun loadProgress() {
        if (!progressFile.exists()) return
        try {
            DataInputStream(progressFile.inputStream().buffered()).use { inp ->
                val ver = inp.readInt()
                val n = inp.readInt()
                inp.readFully(stars, 0, minOf(n, stars.size))
                for (i in 0 until n) { val f = inp.readFloat(); if (i < best.size) best[i] = f }
                if (ver >= 2) { val len = inp.readInt(); val bytes = ByteArray(len); inp.readFully(bytes); adUnlocked.or(BitSet.valueOf(bytes)) }
            }
        } catch (_: Exception) {}
        recount()
    }

    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** Snapshot on the caller's thread, write on a background thread (atomic rename). */
    private fun saveProgress() {
        val starsCopy = stars.copyOf(); val bestCopy = best.copyOf(); val adCopy = adUnlocked.toByteArray()
        io.execute {
            try {
                val tmp = File(progressFile.parentFile, "progress.tmp")
                DataOutputStream(tmp.outputStream().buffered()).use { out ->
                    out.writeInt(2); out.writeInt(starsCopy.size); out.write(starsCopy)
                    for (f in bestCopy) out.writeFloat(f)
                    out.writeInt(adCopy.size); out.write(adCopy)
                }
                tmp.renameTo(progressFile)
            } catch (_: Exception) {}
        }
    }

    private fun recount() {
        var s = 0; var c = 0
        for (i in 1..LEVEL_COUNT) { s += stars[i]; if (stars[i] > 0) c++ }
        totalStars = s; cleared = c
    }

    val availableStars get() = totalStars - _profile.value.starsSpent

    fun isUnlocked(n: Int): Boolean = n == 1 || (n in 2..LEVEL_COUNT && (stars[n - 1] > 0 || stars[n] > 0 || adUnlocked[n]))

    fun currentLevel(): Int {
        for (i in 1..LEVEL_COUNT) if (stars[i].toInt() == 0 && isUnlocked(i)) return i
        return LEVEL_COUNT
    }

    fun highestUnlocked(): Int { var h = 1; for (i in 1..LEVEL_COUNT) if (isUnlocked(i)) h = i; return h }

    fun unlockByAd(n: Int) { adUnlocked.set(n); saveProgress(); _progressVersion.value++ }

    data class Outcome(val reward: Economy.Reward, val firstClear: Boolean, val newBest: Boolean, val chestReady: Boolean, val newAchievements: List<Achievement>)

    fun recordWin(level: Int, s: Int, time: Double, perfect: Boolean, pickups: Int, distance: Double, reverse: Double, cones: Int): Outcome {
        val prev = stars[level].toInt()
        val first = prev == 0
        val improved = s > prev
        val newBest = best[level] == 0f || time < best[level]
        if (s > prev) stars[level] = s.toByte()
        if (newBest) best[level] = time.toFloat()
        recount(); saveProgress(); _progressVersion.value++
        val reward = Economy.levelReward(level, s, perfect, pickups, first, improved)
        addCoins(reward.coins)
        val st = _stats.value
        _stats.value = st.copy(parks = st.parks + 1, perfects = st.perfects + if (perfect) 1 else 0, threeStars = st.threeStars + if (s == 3 && first) 1 else 0,
            distance = st.distance + distance, reverse = st.reverse + reverse, coinsEarned = st.coinsEarned + reward.coins, cones = st.cones + cones)
        saveStats()
        Missions.progress(this, MissionKind.PARK, 1)
        if (s == 3) Missions.progress(this, MissionKind.THREE_STARS, 1)
        if (perfect) Missions.progress(this, MissionKind.PERFECT, 1)
        Missions.progress(this, MissionKind.REVERSE_M, reverse.toInt())
        if (cones == 0) Missions.progress(this, MissionKind.CLEAN, 1)
        val p = _profile.value
        val chestStars = p.chestStars + (s - prev).coerceAtLeast(0)
        update { it.copy(chestStars = chestStars) }
        return Outcome(reward, first, newBest, chestStars >= 50, Achievements.check(this))
    }

    fun recordCrash(distance: Double, reverse: Double) {
        val st = _stats.value
        _stats.value = st.copy(crashes = st.crashes + 1, distance = st.distance + distance, reverse = st.reverse + reverse); saveStats()
    }

    fun addPlayTime(sec: Double) { val st = _stats.value; _stats.value = st.copy(playSeconds = st.playSeconds + sec); saveStats() }

    // ------------------------------------------------------------- wallet
    fun addCoins(n: Int) = update { it.copy(coins = it.coins + n) }

    fun spend(n: Int): Boolean {
        if (_profile.value.coins < n) return false
        update { it.copy(coins = it.coins - n) }
        return true
    }

    fun spendStars(n: Int): Boolean {
        if (availableStars < n) return false
        update { it.copy(starsSpent = it.starsSpent + n) }
        return true
    }

    fun useBooster(kind: String): Boolean {
        val p = _profile.value
        val have = when (kind) { "rewind" -> p.rewind; "ghost" -> p.ghost; else -> p.slowmo }
        if (have <= 0) return false
        update { when (kind) { "rewind" -> it.copy(rewind = it.rewind - 1); "ghost" -> it.copy(ghost = it.ghost - 1); else -> it.copy(slowmo = it.slowmo - 1) } }
        return true
    }

    fun addBooster(kind: String, n: Int) = update {
        when (kind) { "rewind" -> it.copy(rewind = it.rewind + n); "ghost" -> it.copy(ghost = it.ghost + n); else -> it.copy(slowmo = it.slowmo + n) }
    }

    fun update(f: (Profile) -> Profile) { _profile.value = f(_profile.value); writeProfile(_profile.value) }

    fun updateSettings(f: (Settings) -> Settings) { _settings.value = f(_settings.value); writeSettings(_settings.value) }

    fun unlockAchievement(id: String) {
        if (id in _achievements.value) return
        _achievements.value = _achievements.value + id
        prefs.edit().putStringSet("ach", _achievements.value).apply()
    }

    fun setMissions(list: List<Mission>) { _missions.value = list; Missions.save(prefs, list) }

    fun resetAll() {
        stars.fill(0); best.fill(0f); adUnlocked.clear(); saveProgress(); recount(); _progressVersion.value++
        val keepOnboarding = _profile.value.onboardingDone
        _profile.value = Profile(onboardingDone = keepOnboarding); writeProfile(_profile.value)
        _stats.value = Stats(); saveStats()
        _achievements.value = emptySet(); prefs.edit().remove("ach").apply()
        _missions.value = Missions.load(prefs, today(), force = true)
    }

    // ---------------------------------------------------- serialization
    private fun readSettings() = Settings(
        prefs.getFloat("s_music", 0.55f), prefs.getFloat("s_sfx", 0.9f), prefs.getFloat("s_engine", 0.8f), prefs.getBoolean("s_hap", true),
        Scheme.of(prefs.getString("s_scheme", "wheel")!!), prefs.getBoolean("s_left", false), prefs.getFloat("s_size", 1f), prefs.getFloat("s_op", 0.92f),
        prefs.getFloat("s_sens", 1f), prefs.getBoolean("s_center", true), prefs.getString("s_quality", "auto")!!, prefs.getInt("s_fps", 60),
        prefs.getBoolean("s_sensors", true), prefs.getBoolean("s_showfps", false),
    )

    private fun writeSettings(s: Settings) = prefs.edit()
        .putFloat("s_music", s.music).putFloat("s_sfx", s.sfx).putFloat("s_engine", s.engine).putBoolean("s_hap", s.haptics)
        .putString("s_scheme", s.scheme.id).putBoolean("s_left", s.leftHanded).putFloat("s_size", s.ctrlSize).putFloat("s_op", s.ctrlOpacity)
        .putFloat("s_sens", s.sensitivity).putBoolean("s_center", s.autoCenter).putString("s_quality", s.quality).putInt("s_fps", s.fps)
        .putBoolean("s_sensors", s.sensors).putBoolean("s_showfps", s.showFps).apply()

    private fun ints(s: String?): Set<Int> = s?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    private fun strs(s: String?): Set<String> = s?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    private fun readProfile(): Profile {
        if (!prefs.contains("p_coins")) return Profile()
        val custom = HashMap<String, CarCustom>()
        for (c in Cars.ALL) {
            if (!prefs.contains("c_${c.id}_color")) continue
            custom[c.id] = CarCustom(prefs.getInt("c_${c.id}_color", c.defaultColor), Finish.of(prefs.getString("c_${c.id}_fin", "solid")!!),
                Decal.of(prefs.getString("c_${c.id}_dec", "none")!!), prefs.getInt("c_${c.id}_acc", 0xFFFFFF), prefs.getInt("c_${c.id}_glow", 0))
        }
        return Profile(
            prefs.getInt("p_coins", 250), prefs.getInt("p_sspent", 0), prefs.getString("p_car", "rookie")!!, strs(prefs.getString("p_cars", "rookie")),
            custom, ints(prefs.getString("p_paints", "0,1,2,3")), strs(prefs.getString("p_fin", "solid")), strs(prefs.getString("p_dec", "none")),
            ints(prefs.getString("p_glows", "0")), prefs.getString("p_skin", "classic")!!, strs(prefs.getString("p_skins", "classic")),
            prefs.getString("p_theme", "midnight")!!, strs(prefs.getString("p_themes", "midnight")),
            prefs.getInt("p_rewind", 2), prefs.getInt("p_ghost", 1), prefs.getInt("p_slow", 1), prefs.getBoolean("p_onb", false),
            prefs.getLong("p_lday", 0), prefs.getInt("p_lstreak", 0), prefs.getLong("p_lclaim", 0), prefs.getLong("p_spin", 0),
            prefs.getLong("p_chal", 0), prefs.getInt("p_chest", 0),
            prefs.getString("p_wheel", "classic")!!, strs(prefs.getString("p_wheels", "classic")),
        )
    }

    private fun writeProfile(p: Profile) {
        val e = prefs.edit()
            .putInt("p_coins", p.coins).putInt("p_sspent", p.starsSpent).putString("p_car", p.selectedCar).putString("p_cars", p.ownedCars.joinToString(","))
            .putString("p_paints", p.ownedPaints.joinToString(",")).putString("p_fin", p.ownedFinishes.joinToString(",")).putString("p_dec", p.ownedDecals.joinToString(","))
            .putString("p_glows", p.ownedGlows.joinToString(",")).putString("p_skin", p.skin).putString("p_skins", p.ownedSkins.joinToString(","))
            .putString("p_theme", p.uiTheme).putString("p_themes", p.ownedThemes.joinToString(","))
            .putInt("p_rewind", p.rewind).putInt("p_ghost", p.ghost).putInt("p_slow", p.slowmo).putBoolean("p_onb", p.onboardingDone)
            .putLong("p_lday", p.loginDay).putInt("p_lstreak", p.loginStreak).putLong("p_lclaim", p.loginClaimedDay).putLong("p_spin", p.spinDay)
            .putLong("p_chal", p.challengeDay).putInt("p_chest", p.chestStars)
            .putString("p_wheel", p.selectedWheel).putString("p_wheels", p.ownedWheels.joinToString(","))
        for ((id, c) in p.custom) e.putInt("c_${id}_color", c.color).putString("c_${id}_fin", c.finish.id).putString("c_${id}_dec", c.decal.id).putInt("c_${id}_acc", c.accent).putInt("c_${id}_glow", c.glow)
        e.apply()
    }

    private fun readStats() = Stats(
        prefs.getInt("st_parks", 0), prefs.getInt("st_perf", 0), prefs.getInt("st_crash", 0), prefs.getInt("st_cones", 0),
        prefs.getFloat("st_dist", 0f).toDouble(), prefs.getFloat("st_rev", 0f).toDouble(), prefs.getInt("st_coins", 0), prefs.getInt("st_3s", 0),
        prefs.getInt("st_rew", 0), prefs.getFloat("st_play", 0f).toDouble(),
    )

    private fun saveStats() {
        val s = _stats.value
        prefs.edit().putInt("st_parks", s.parks).putInt("st_perf", s.perfects).putInt("st_crash", s.crashes).putInt("st_cones", s.cones)
            .putFloat("st_dist", s.distance.toFloat()).putFloat("st_rev", s.reverse.toFloat()).putInt("st_coins", s.coinsEarned).putInt("st_3s", s.threeStars)
            .putInt("st_rew", s.rewinds).putFloat("st_play", s.playSeconds.toFloat()).apply()
    }

    fun noteRewind() { val s = _stats.value; _stats.value = s.copy(rewinds = s.rewinds + 1); saveStats() }

    fun effectiveQuality(): Quality = when (_settings.value.quality) {
        "low" -> Quality.LOW; "medium" -> Quality.MEDIUM; "high" -> Quality.HIGH; "ultra" -> Quality.ULTRA; else -> Quality.HIGH
    }

    fun skin() = WorldSkin.of(_profile.value.skin)
}
