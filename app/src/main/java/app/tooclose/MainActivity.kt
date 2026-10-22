package app.tooclose

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

/** Settings screen. When a parent PIN is set, everything except the live status is behind the PIN. */
class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var liveCard: LinearLayout
    private lateinit var tvLive: TextView
    private lateinit var tvLiveSub: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTh: TextView
    private lateinit var panel: LinearLayout
    private lateinit var pinGate: LinearLayout
    private var unlocked = false

    private val ticker = object : Runnable {
        override fun run() { refreshLive(); h.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C_INDIGO
        setContentView(buildUi())
        if (intent?.getBooleanExtra(EXTRA_START, false) == true && ready()) GuardService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START, false) && ready()) GuardService.start(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        h.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(ticker)
    }

    override fun onStop() {
        super.onStop()
        unlocked = false   // ask for the PIN again next time
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(28)) }

        // Header
        root.addView(TextView(this).apply {
            text = "👀 " + getString(R.string.app_name)
            textSize = 30f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.tagline); textSize = 15f; setTextColor(Color.argb(230, 255, 255, 255))
            setPadding(0, dp(2), 0, dp(14))
        })

        // Live distance card (colour follows the distance)
        liveCard = card(C_GREY).apply { gravity = Gravity.CENTER_HORIZONTAL }
        tvLive = TextView(this).apply { textSize = 34f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        tvLiveSub = TextView(this).apply { textSize = 14f; setTextColor(Color.argb(235, 255, 255, 255)); gravity = Gravity.CENTER }
        liveCard.addView(tvLive); liveCard.addView(tvLiveSub)
        root.addView(liveCard)

        // Status card
        val st = card(Color.WHITE)
        tvStatus = TextView(this).apply { textSize = 15f; setTextColor(C_TEXT); setLineSpacing(0f, 1.25f) }
        st.addView(tvStatus)
        root.addView(st)

        // PIN gate
        pinGate = card(Color.WHITE)
        val etPin = EditText(this).apply { hint = getString(R.string.pin_hint); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        pinGate.addView(section("🔒 " + getString(R.string.pin_hint)))
        pinGate.addView(etPin)
        pinGate.addView(btn(getString(R.string.pin_unlock), C_INDIGO) {
            if (etPin.text.toString() == Prefs.pin(this)) { unlocked = true; etPin.setText(""); refresh() }
            else etPin.error = getString(R.string.pin_wrong)
        })
        root.addView(pinGate)

        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Setup + start/stop
        val setup = card(Color.WHITE)
        setup.addView(section("🚀 " + getString(R.string.sec_setup)))
        setup.addView(btn(getString(R.string.perm_camera), C_BLUE) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS), 1)
            else requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        })
        setup.addView(btn(getString(R.string.perm_overlay), C_TEAL) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        val ss = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ss.addView(btn(getString(R.string.start), C_GREEN, weight = 1f) {
            if (ready()) { Prefs.setWanted(this, true); GuardService.start(this) }
            refresh()
        })
        ss.addView(btn(getString(R.string.stop), C_RED, weight = 1f) {
            Prefs.setWanted(this, false); GuardService.stop(this); refresh()
        })
        setup.addView(ss)
        panel.addView(setup)

        // Threshold + age
        val dist = card(Color.WHITE)
        dist.addView(section("📏 " + getString(R.string.sec_distance)))
        tvTh = TextView(this).apply { textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(C_ORANGE_D); setPadding(0, dp(4), 0, dp(4)) }
        dist.addView(tvTh)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(btn("− 5 cm", C_AMBER, weight = 1f) { Prefs.setThresholdCm(this, Prefs.thresholdCm(this) - 5); refresh() })
        row.addView(btn("+ 5 cm", C_AMBER, weight = 1f) { Prefs.setThresholdCm(this, Prefs.thresholdCm(this) + 5); refresh() })
        dist.addView(row)
        dist.addView(TextView(this).apply { text = getString(R.string.age_title); textSize = 16f; setTextColor(C_TEXT); setPadding(0, dp(12), 0, 0) })
        val rg = RadioGroup(this)
        val ages = arrayOf(R.string.age_0, R.string.age_1, R.string.age_2)
        val ageColors = intArrayOf(C_PINK, C_PURPLE, C_BLUE)
        ages.forEachIndexed { i, s ->
            rg.addView(RadioButton(this).apply {
                id = 100 + i; text = getString(s); textSize = 16f; setTextColor(C_TEXT)
                buttonTintList = ColorStateList.valueOf(ageColors[i])
            })
        }
        rg.check(100 + Prefs.ageGroup(this))
        rg.setOnCheckedChangeListener { _, id -> Prefs.setAgeGroup(this, id - 100); Prefs.setCalib(this, 1f); refresh() }
        dist.addView(rg)
        panel.addView(dist)

        // Calibration
        val cal = card(Color.WHITE)
        cal.addView(section("🎯 " + getString(R.string.sec_calib)))
        cal.addView(TextView(this).apply { text = getString(R.string.cal_help); textSize = 14f; setTextColor(C_TEXT_2) })
        cal.addView(btn(getString(R.string.cal_button), C_PURPLE) {
            if (GuardService.instance == null) tvStatus.text = getString(R.string.cal_need_running)
            else { GuardService.calibrate(30f); tvStatus.text = getString(R.string.cal_started) }
        })
        cal.addView(btn(getString(R.string.cal_reset), C_GREY) { Prefs.setCalib(this, 1f); refresh() })
        panel.addView(cal)

        // Options
        val opt = card(Color.WHITE)
        opt.addView(section("🔔 " + getString(R.string.sec_options)))
        opt.addView(CheckBox(this).apply {
            text = getString(R.string.opt_voice); textSize = 16f; setTextColor(C_TEXT); isChecked = Prefs.voice(this@MainActivity)
            buttonTintList = ColorStateList.valueOf(C_GREEN)
            setOnCheckedChangeListener { _, v -> Prefs.setVoice(this@MainActivity, v) }
        })
        opt.addView(CheckBox(this).apply {
            text = getString(R.string.opt_pause); textSize = 16f; setTextColor(C_TEXT); isChecked = Prefs.pauseMedia(this@MainActivity)
            buttonTintList = ColorStateList.valueOf(C_GREEN)
            setOnCheckedChangeListener { _, v -> Prefs.setPauseMedia(this@MainActivity, v) }
        })
        panel.addView(opt)

        // Parent PIN
        val pin = card(Color.WHITE)
        pin.addView(section("🔐 " + getString(R.string.sec_pin)))
        pin.addView(TextView(this).apply { text = getString(R.string.pin_title); textSize = 14f; setTextColor(C_TEXT_2) })
        val etNew = EditText(this).apply { hint = getString(R.string.pin_new_hint); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        pin.addView(etNew)
        pin.addView(btn(getString(R.string.pin_save), C_INDIGO) {
            val p = etNew.text.toString()
            if (p.isEmpty() || p.length in 4..8) { Prefs.setPin(this, p); etNew.setText(""); refresh() } else etNew.error = getString(R.string.pin_len)
        })
        panel.addView(pin)

        // Privacy
        val pv = card(C_MINT)
        pv.addView(TextView(this).apply { text = "🛡️ " + getString(R.string.privacy_note); textSize = 13f; setTextColor(C_TEXT) })
        panel.addView(pv)

        root.addView(panel)
        return ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(C_INDIGO, C_SKY, C_BG))
            addView(root)
        }
    }

    private fun ready(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && Settings.canDrawOverlays(this)

    private fun refresh() {
        val locked = Prefs.pin(this).isNotEmpty() && !unlocked
        pinGate.visibility = if (locked) View.VISIBLE else View.GONE
        panel.visibility = if (locked) View.GONE else View.VISIBLE
        val cam = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val ov = Settings.canDrawOverlays(this)
        tvStatus.text = buildString {
            append(if (cam) getString(R.string.st_cam_ok) else getString(R.string.st_cam_no)).append('\n')
            append(if (ov) getString(R.string.st_ov_ok) else getString(R.string.st_ov_no)).append('\n')
            append("🎯 ").append(getString(R.string.st_calib, String.format("%.2f", Prefs.calib(this@MainActivity))))
        }
        tvTh.text = getString(R.string.th_title, Prefs.thresholdCm(this))
        refreshLive()
    }

    private fun refreshLive() {
        val cm = GuardService.lastCm
        val th = Prefs.thresholdCm(this)
        val (text, color) = when (GuardService.state) {
            GuardService.State.OFF, GuardService.State.SCREEN_OFF -> Pair("😴 " + getString(R.string.live_off), C_GREY)
            GuardService.State.NO_CAMERA -> Pair("📷 " + getString(R.string.live_nocam), C_GREY)
            GuardService.State.COVERED -> Pair("🙈 " + getString(R.string.live_covered), C_RED)
            GuardService.State.WATCHING -> when {
                cm == null -> Pair("🔍 " + getString(R.string.live_noface), C_BLUE)
                cm < th -> Pair("😮 " + getString(R.string.live_cm, cm.toInt()), C_RED)
                cm < th + 10 -> Pair("🙂 " + getString(R.string.live_cm, cm.toInt()), C_ORANGE)
                else -> Pair("😄 " + getString(R.string.live_cm, cm.toInt()), C_GREEN)
            }
        }
        tvLive.text = text
        tvLiveSub.text = getString(R.string.live_sub, th)
        (liveCard.background as GradientDrawable).setColor(color)
    }

    private fun card(bg: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(bg) }
        elevation = dp(3).toFloat()
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
    }

    private fun section(t: String): TextView = TextView(this).apply {
        text = t; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(C_INDIGO); setPadding(0, 0, 0, dp(8))
    }

    private fun btn(label: String, color: Int, weight: Float = 0f, onClick: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE)
        background = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(color) }
        stateListAnimator = null
        setPadding(dp(14), dp(10), dp(14), dp(10))
        layoutParams = LinearLayout.LayoutParams(if (weight > 0f) 0 else LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
            setMargins(dp(4), dp(6), dp(4), dp(6))
        }
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_START = "start"
        val C_INDIGO = Color.rgb(63, 81, 181)
        val C_SKY = Color.rgb(129, 212, 250)
        val C_BG = Color.rgb(255, 248, 225)
        val C_BLUE = Color.rgb(30, 136, 229)
        val C_TEAL = Color.rgb(0, 150, 136)
        val C_GREEN = Color.rgb(67, 160, 71)
        val C_RED = Color.rgb(229, 57, 53)
        val C_ORANGE = Color.rgb(251, 140, 0)
        val C_ORANGE_D = Color.rgb(230, 81, 0)
        val C_AMBER = Color.rgb(255, 160, 0)
        val C_PURPLE = Color.rgb(142, 36, 170)
        val C_PINK = Color.rgb(236, 64, 122)
        val C_GREY = Color.rgb(120, 144, 156)
        val C_MINT = Color.rgb(224, 247, 233)
        val C_TEXT = Color.rgb(38, 50, 56)
        val C_TEXT_2 = Color.rgb(84, 110, 122)
    }
}
