package com.ngstudio.inchperfect.ads

import android.os.Handler
import android.os.Looper
import com.ngstudio.inchperfect.BuildConfig

enum class Placement { UNLOCK_LEVEL, DOUBLE_COINS, FREE_SPIN, FREE_BOOSTER, FREE_COINS, REWIND }

/**
 * Rewarded-ad gateway. Real ads are not integrated yet:
 *  - debug builds use [SimulatedRewardedAds] (shows a short "loading ad" and always rewards),
 *  - release builds use [DisabledRewardedAds] (every ad button is hidden).
 * Plugging AdMob in later means adding a third implementation.
 */
interface RewardedAds {
    val available: Boolean
    fun show(placement: Placement, onReward: () -> Unit, onFail: () -> Unit = {})
}

class SimulatedRewardedAds : RewardedAds {
    override val available = true
    private val h = Handler(Looper.getMainLooper())
    override fun show(placement: Placement, onReward: () -> Unit, onFail: () -> Unit) { h.postDelayed(onReward, 1500) }
}

class DisabledRewardedAds : RewardedAds {
    override val available = false
    override fun show(placement: Placement, onReward: () -> Unit, onFail: () -> Unit) = onFail()
}

object Ads {
    val rewarded: RewardedAds by lazy { if (BuildConfig.ADS_SIMULATED) SimulatedRewardedAds() else DisabledRewardedAds() }
}
