package app.tooclose

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen "too close" cover. It takes all touches while shown, so the app underneath can't be used. */
class Overlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private lateinit var title: TextView
    private lateinit var detail: TextView

    val showing get() = root != null

    fun show() {
        if (root != null || !Settings.canDrawOverlays(ctx)) return
        val v = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // Cheerful, not scary: warm gradient the child notices right away
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(255, 112, 67), Color.rgb(236, 64, 122), Color.rgb(126, 87, 194)))
            setPadding(48, 48, 48, 48)
        }
        val face = TextView(ctx).apply { text = "🙈"; textSize = 96f; gravity = Gravity.CENTER }
        title = TextView(ctx).apply {
            text = ctx.getString(R.string.cover_title)
            textSize = 40f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
        }
        detail = TextView(ctx).apply {
            text = ctx.getString(R.string.cover_hint)
            textSize = 22f; setTextColor(Color.rgb(255, 243, 224)); gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }
        v.addView(face); v.addView(title); v.addView(detail)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        try { wm.addView(v, lp); root = v } catch (e: Exception) { root = null }
    }

    /** Update the line under the title (distance, or "don't cover the camera"). */
    fun setDetail(text: String) { if (root != null) detail.text = text }

    fun hide() {
        val v = root ?: return
        root = null
        try { wm.removeView(v) } catch (e: Exception) {}
    }
}
