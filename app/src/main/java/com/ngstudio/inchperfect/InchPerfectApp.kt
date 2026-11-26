package com.ngstudio.inchperfect

import android.app.Application
import com.ngstudio.inchperfect.audio.Audio
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.core.Haptics

class InchPerfectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GameRepo.init(this)
        Haptics.init(this)
        val s = GameRepo.settings.value
        Audio.sfxVolume = s.sfx; Audio.musicVolume = s.music; Audio.engineVolume = s.engine
        Audio.init(this)
        com.ngstudio.inchperfect.game.render.Textures.diskDir = java.io.File(cacheDir, "tex")
    }
}
