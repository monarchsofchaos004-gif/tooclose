package app.tooclose

import android.content.Context
import android.content.SharedPreferences

/** Settings (local only). */
object Prefs {
    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences("tooclose", Context.MODE_PRIVATE)

    /** Average distance between the eyes, cm (used to turn pixels into centimetres). */
    val AGE_IPD = floatArrayOf(4.7f, 5.4f, 6.3f)   // 3–5 years, 6–12 years, teen/adult

    fun thresholdCm(c: Context) = sp(c).getInt("th", 30)
    fun setThresholdCm(c: Context, v: Int) = sp(c).edit().putInt("th", v.coerceIn(15, 60)).apply()

    fun ageGroup(c: Context) = sp(c).getInt("age", 0).coerceIn(0, 2)
    fun setAgeGroup(c: Context, v: Int) = sp(c).edit().putInt("age", v.coerceIn(0, 2)).apply()
    fun ipdCm(c: Context) = AGE_IPD[ageGroup(c)]

    /** Calibration multiplier (actual ÷ estimated), 1 = not calibrated. */
    fun calib(c: Context) = sp(c).getFloat("cal", 1f)
    fun setCalib(c: Context, v: Float) = sp(c).edit().putFloat("cal", v.coerceIn(0.5f, 2f)).apply()

    fun voice(c: Context) = sp(c).getBoolean("voice", true)
    fun setVoice(c: Context, v: Boolean) = sp(c).edit().putBoolean("voice", v).apply()

    fun pauseMedia(c: Context) = sp(c).getBoolean("pause", true)
    fun setPauseMedia(c: Context, v: Boolean) = sp(c).edit().putBoolean("pause", v).apply()

    /** Parent PIN; empty = no PIN. Needed to stop the guard or change settings. */
    fun pin(c: Context) = sp(c).getString("pin", "") ?: ""
    fun setPin(c: Context, v: String) = sp(c).edit().putString("pin", v).apply()

    /** The user wants the guard on (used to remind after a reboot). */
    fun wanted(c: Context) = sp(c).getBoolean("on", false)
    fun setWanted(c: Context, v: Boolean) = sp(c).edit().putBoolean("on", v).apply()
}
