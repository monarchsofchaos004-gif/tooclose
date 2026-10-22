package app.tooclose

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
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
    private lateinit var tvStatus: TextView
    private lateinit var tvLive: TextView
    private lateinit var tvTh: TextView
    private lateinit var panel: LinearLayout
    private lateinit var pinGate: LinearLayout
    private var unlocked = false

    private val ticker = object : Runnable {
        override fun run() { refreshLive(); h.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 64, 48, 64) }
        tvLive = TextView(this).apply { textSize = 30f }
        root.addView(tvLive)
        tvStatus = TextView(this).apply { textSize = 15f; setPadding(0, 16, 0, 16) }
        root.addView(tvStatus)

        // PIN gate
        pinGate = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val etPin = EditText(this).apply { hint = getString(R.string.pin_hint); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        pinGate.addView(etPin)
        pinGate.addView(btn(getString(R.string.pin_unlock)) {
            if (etPin.text.toString() == Prefs.pin(this)) { unlocked = true; etPin.setText(""); refresh() }
            else etPin.error = getString(R.string.pin_wrong)
        })
        root.addView(pinGate)

        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(btn(getString(R.string.perm_camera)) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS), 1)
            else requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        })
        panel.addView(btn(getString(R.string.perm_overlay)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        panel.addView(btn(getString(R.string.start)) {
            if (ready()) { Prefs.setWanted(this, true); GuardService.start(this) }
            refresh()
        })
        panel.addView(btn(getString(R.string.stop)) {
            Prefs.setWanted(this, false); GuardService.stop(this); refresh()
        })
        panel.addView(space())

        tvTh = TextView(this).apply { textSize = 16f }
        panel.addView(tvTh)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(btn("−5") { Prefs.setThresholdCm(this, Prefs.thresholdCm(this) - 5); refresh() })
        row.addView(btn("+5") { Prefs.setThresholdCm(this, Prefs.thresholdCm(this) + 5); refresh() })
        panel.addView(row)

        panel.addView(TextView(this).apply { text = getString(R.string.age_title); textSize = 16f; setPadding(0, 24, 0, 0) })
        val rg = RadioGroup(this)
        val ages = arrayOf(R.string.age_0, R.string.age_1, R.string.age_2)
        ages.forEachIndexed { i, s -> rg.addView(RadioButton(this).apply { id = 100 + i; text = getString(s) }) }
        rg.check(100 + Prefs.ageGroup(this))
        rg.setOnCheckedChangeListener { _, id -> Prefs.setAgeGroup(this, id - 100); Prefs.setCalib(this, 1f); refresh() }
        panel.addView(rg)

        panel.addView(TextView(this).apply { text = getString(R.string.cal_help); textSize = 14f; setPadding(0, 24, 0, 0) })
        panel.addView(btn(getString(R.string.cal_button)) {
            if (GuardService.instance == null) tvStatus.text = getString(R.string.cal_need_running)
            else { GuardService.calibrate(30f); tvStatus.text = getString(R.string.cal_started) }
        })
        panel.addView(btn(getString(R.string.cal_reset)) { Prefs.setCalib(this, 1f); refresh() })

        panel.addView(CheckBox(this).apply {
            text = getString(R.string.opt_voice); isChecked = Prefs.voice(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setVoice(this@MainActivity, v) }
        })
        panel.addView(CheckBox(this).apply {
            text = getString(R.string.opt_pause); isChecked = Prefs.pauseMedia(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setPauseMedia(this@MainActivity, v) }
        })

        panel.addView(TextView(this).apply { text = getString(R.string.pin_title); textSize = 16f; setPadding(0, 24, 0, 0) })
        val etNew = EditText(this).apply { hint = getString(R.string.pin_new_hint); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        panel.addView(etNew)
        panel.addView(btn(getString(R.string.pin_save)) {
            val p = etNew.text.toString()
            if (p.isEmpty() || p.length in 4..8) { Prefs.setPin(this, p); etNew.setText(""); refresh() } else etNew.error = getString(R.string.pin_len)
        })
        panel.addView(TextView(this).apply { text = getString(R.string.privacy_note); textSize = 13f; setPadding(0, 32, 0, 0) })
        root.addView(panel)
        return ScrollView(this).apply { addView(root) }
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
            append(getString(R.string.st_calib, String.format("%.2f", Prefs.calib(this@MainActivity))))
        }
        tvTh.text = getString(R.string.th_title, Prefs.thresholdCm(this))
        refreshLive()
    }

    private fun refreshLive() {
        val cm = GuardService.lastCm
        tvLive.text = when (GuardService.state) {
            GuardService.State.OFF -> getString(R.string.live_off)
            GuardService.State.NO_CAMERA -> getString(R.string.live_nocam)
            GuardService.State.SCREEN_OFF -> getString(R.string.live_off)
            GuardService.State.COVERED -> getString(R.string.live_covered)
            GuardService.State.WATCHING -> if (cm == null) getString(R.string.live_noface) else getString(R.string.live_cm, cm.toInt())
        }
    }

    private fun btn(label: String, onClick: () -> Unit): Button = Button(this).apply { text = label; setOnClickListener { onClick() } }
    private fun space(): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24) }

    companion object { const val EXTRA_START = "start" }
}
