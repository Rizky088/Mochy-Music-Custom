package com.mochyy.mochymusic.launcher

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.mochyy.mochymusic.MainActivity

abstract class BaseIconSplashLauncher(private val bgColorInt: Int) : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawable(ColorDrawable(bgColorInt))
        super.onCreate(savedInstanceState)
        val forward = Intent(this, MainActivity::class.java).apply {
            action = intent?.action
            data = intent?.data
            intent?.extras?.let { putExtras(it) }
            putExtra(EXTRA_SPLASH_BG_COLOR, bgColorInt)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(forward)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        finish()
    }

    companion object {
        const val EXTRA_SPLASH_BG_COLOR = "mochy_ext_splash_bg_color"
    }
}

class SplashLauncher_IconDefault : BaseIconSplashLauncher(0xFF1A1A1A.toInt())
class SplashLauncher_IconMarkWhite : BaseIconSplashLauncher(0xFFD9D9D9.toInt())
class SplashLauncher_IconMarkBlack : BaseIconSplashLauncher(0xFF000000.toInt())
class SplashLauncher_IconMarkMaterial : BaseIconSplashLauncher(0xFF121212.toInt())
class SplashLauncher_IconMarkTransparent : BaseIconSplashLauncher(0xFF121212.toInt())
class SplashLauncher_IconOutline : BaseIconSplashLauncher(0xFF1A1A1A.toInt())
class SplashLauncher_IconOutlineWhite : BaseIconSplashLauncher(0xFFD9D9D9.toInt())
class SplashLauncher_IconOutlineBlack : BaseIconSplashLauncher(0xFF000000.toInt())
class SplashLauncher_IconOutlineMaterial : BaseIconSplashLauncher(0xFF121212.toInt())
class SplashLauncher_IconOutlineTransparent : BaseIconSplashLauncher(0xFF121212.toInt())
class SplashLauncher_IconAnimeWhite : BaseIconSplashLauncher(0xFFD9D9D9.toInt())
class SplashLauncher_IconAnimeDark : BaseIconSplashLauncher(0xFF000000.toInt())
class SplashLauncher_IconAnimeMaterial : BaseIconSplashLauncher(0xFF121212.toInt())
class SplashLauncher_IconAnimeTransparent : BaseIconSplashLauncher(0xFF121212.toInt())
class SplashLauncher_IconMarkRed : BaseIconSplashLauncher(0xFFE5484D.toInt())
class SplashLauncher_IconMarkOrange : BaseIconSplashLauncher(0xFFF76B15.toInt())
class SplashLauncher_IconMarkYellow : BaseIconSplashLauncher(0xFFFFC53D.toInt())
class SplashLauncher_IconMarkGreen : BaseIconSplashLauncher(0xFF46A758.toInt())
class SplashLauncher_IconMarkTeal : BaseIconSplashLauncher(0xFF12A594.toInt())
class SplashLauncher_IconMarkSky : BaseIconSplashLauncher(0xFF68DDFD.toInt())
class SplashLauncher_IconMarkBlue : BaseIconSplashLauncher(0xFF0090FF.toInt())
class SplashLauncher_IconMarkIndigo : BaseIconSplashLauncher(0xFF3E63DD.toInt())
class SplashLauncher_IconMarkPurple : BaseIconSplashLauncher(0xFF8E4EC6.toInt())
class SplashLauncher_IconMarkPink : BaseIconSplashLauncher(0xFFE93D82.toInt())
class SplashLauncher_IconMarkGraphite : BaseIconSplashLauncher(0xFF2E3135.toInt())
class SplashLauncher_IconMarkCream : BaseIconSplashLauncher(0xFFF1E7D0.toInt())
class SplashLauncher_IconOutlineRed : BaseIconSplashLauncher(0xFFE5484D.toInt())
class SplashLauncher_IconOutlineOrange : BaseIconSplashLauncher(0xFFF76B15.toInt())
class SplashLauncher_IconOutlineYellow : BaseIconSplashLauncher(0xFFFFC53D.toInt())
class SplashLauncher_IconOutlineGreen : BaseIconSplashLauncher(0xFF46A758.toInt())
class SplashLauncher_IconOutlineTeal : BaseIconSplashLauncher(0xFF12A594.toInt())
class SplashLauncher_IconOutlineSky : BaseIconSplashLauncher(0xFF68DDFD.toInt())
class SplashLauncher_IconOutlineBlue : BaseIconSplashLauncher(0xFF0090FF.toInt())
class SplashLauncher_IconOutlineIndigo : BaseIconSplashLauncher(0xFF3E63DD.toInt())
class SplashLauncher_IconOutlinePurple : BaseIconSplashLauncher(0xFF8E4EC6.toInt())
class SplashLauncher_IconOutlinePink : BaseIconSplashLauncher(0xFFE93D82.toInt())
class SplashLauncher_IconOutlineGraphite : BaseIconSplashLauncher(0xFF2E3135.toInt())
class SplashLauncher_IconOutlineCream : BaseIconSplashLauncher(0xFFF1E7D0.toInt())
class SplashLauncher_IconAnimeRed : BaseIconSplashLauncher(0xFFE5484D.toInt())
class SplashLauncher_IconAnimeOrange : BaseIconSplashLauncher(0xFFF76B15.toInt())
class SplashLauncher_IconAnimeYellow : BaseIconSplashLauncher(0xFFFFC53D.toInt())
class SplashLauncher_IconAnimeGreen : BaseIconSplashLauncher(0xFF46A758.toInt())
class SplashLauncher_IconAnimeTeal : BaseIconSplashLauncher(0xFF12A594.toInt())
class SplashLauncher_IconAnimeSky : BaseIconSplashLauncher(0xFF68DDFD.toInt())
class SplashLauncher_IconAnimeBlue : BaseIconSplashLauncher(0xFF0090FF.toInt())
class SplashLauncher_IconAnimeIndigo : BaseIconSplashLauncher(0xFF3E63DD.toInt())
class SplashLauncher_IconAnimePurple : BaseIconSplashLauncher(0xFF8E4EC6.toInt())
class SplashLauncher_IconAnimePink : BaseIconSplashLauncher(0xFFE93D82.toInt())
class SplashLauncher_IconAnimeGraphite : BaseIconSplashLauncher(0xFF2E3135.toInt())
class SplashLauncher_IconAnimeCream : BaseIconSplashLauncher(0xFFF1E7D0.toInt())
