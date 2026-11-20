package de.radicalfishgames.crosscode.steam

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.appPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Steam-style achievement pop-ups in the bottom-right corner of the game screen, one at a time:
 * the achievement's own art, "Achievement Unlocked!", its name and description. Rises out of the
 * bottom edge, holds, sinks back. A plain native view over the WebView, so it costs the game
 * nothing and never takes input. Off with the "Achievement pop-ups" setting.
 */
class AchievementPopups(
    private val activity: AppCompatActivity,
    parent: ViewGroup,
) {
    private val layer =
        FrameLayout(activity).apply {
            isClickable = false
            isFocusable = false
            clipChildren = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    private val queue = Channel<SteamBridge.Popup>(capacity = 32)

    init {
        parent.addView(layer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { SteamBridge.popups.collect { queue.trySend(it) } }
            }
        }
        activity.lifecycleScope.launch {
            for (popup in queue) {
                if (enabled(activity)) show(popup)
            }
        }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics).toInt()

    private suspend fun show(popup: SteamBridge.Popup) {
        val content =
            when (popup) {
                is SteamBridge.Popup.Unlocked -> {
                    // Like Steam: header, name, description. A status line only when it is NOT on Steam yet
                    val status =
                        when {
                            !popup.signedIn -> activity.getString(R.string.steam_popup_signed_out)
                            popup.onSteam == true -> null
                            popup.onSteam == false -> activity.getString(R.string.steam_popup_retrying)
                            else -> activity.getString(R.string.steam_popup_offline)
                        }
                    Content(activity.getString(R.string.steam_popup_unlocked), popup.title, popup.description, status, popup.iconUrl)
                }
                is SteamBridge.Popup.CaughtUp ->
                    Content(
                        activity.getString(R.string.steam_popup_synced),
                        activity.getString(R.string.steam_popup_caught_up, popup.count),
                        activity.getString(R.string.steam_popup_caught_up_desc),
                        null,
                        null,
                    )
            }

        val icon = content.iconUrl?.let { withTimeoutOrNull(3_000L) { loadIcon(it) } }
        val (card, iconFrame, shine) = buildCard(content, icon)
        layer.addView(
            card,
            FrameLayout.LayoutParams(dp(330), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(14), dp(14))
            },
        )

        // Rise out of the bottom edge (fast, then settling), like Steam's corner notification
        val travel = dp(140).toFloat()
        card.translationY = travel
        card.animate().translationY(0f).setInterpolator(DecelerateInterpolator(2f)).setDuration(450).start()
        delay(450)

        // A glint across the achievement art once it has landed
        shine.translationX = -dp(64).toFloat()
        shine.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(shine, View.TRANSLATION_X, -dp(64).toFloat(), iconFrame.width.toFloat() + dp(16)).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            start()
        }

        delay(5_200L)
        card.animate().translationY(travel).setInterpolator(AccelerateInterpolator(1.6f)).setDuration(380).start()
        delay(420)
        layer.removeView(card)
    }

    private data class Content(
        val header: String,
        val title: String,
        val description: String?,
        val status: String?,
        val iconUrl: String?,
    )

    private fun buildCard(
        c: Content,
        icon: Bitmap?,
    ): Triple<View, FrameLayout, View> {
        val card =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(14), dp(10))
                // Steam's notification panel: a dark slate gradient with a faint lighter rim
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(Color.parseColor("#3D4450"), Color.parseColor("#23262E")),
                    ).apply {
                        cornerRadius = dp(6).toFloat()
                        setStroke(dp(1), Color.parseColor("#4C5564"))
                    }
                elevation = dp(10).toFloat()
            }

        // The achievement's own art, square, with a glint that sweeps over it on arrival
        val iconFrame =
            FrameLayout(activity).apply {
                clipToOutline = true
                outlineProvider =
                    object : android.view.ViewOutlineProvider() {
                        override fun getOutline(
                            view: View,
                            outline: android.graphics.Outline,
                        ) = outline.setRoundRect(0, 0, view.width, view.height, dp(3).toFloat())
                    }
                setBackgroundColor(Color.parseColor("#16181D"))
            }
        val iconView =
            ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                if (icon != null) setImageBitmap(icon) else setImageResource(R.drawable.lea_sprite)
            }
        iconFrame.addView(iconView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val shine =
            View(activity).apply {
                visibility = View.INVISIBLE
                rotation = 20f
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(Color.TRANSPARENT, Color.argb(110, 255, 255, 255), Color.TRANSPARENT),
                    )
            }
        iconFrame.addView(shine, FrameLayout.LayoutParams(dp(28), dp(110), Gravity.CENTER_VERTICAL))
        card.addView(iconFrame, LinearLayout.LayoutParams(dp(64), dp(64)))

        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(c.header, 11f, "#B8BCBF", bold = false))
        texts.addView(label(c.title, 15f, "#FFFFFF", bold = true, lines = 2).apply { setPadding(0, dp(1), 0, 0) })
        if (!c.description.isNullOrBlank()) {
            texts.addView(label(c.description, 12f, "#969BA1", bold = false, lines = 2).apply { setPadding(0, dp(2), 0, 0) })
        }
        if (c.status != null) {
            texts.addView(label(c.status, 11f, "#FCCD9B", bold = false, lines = 2).apply { setPadding(0, dp(3), 0, 0) })
        }
        card.addView(
            texts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) },
        )
        return Triple(card, iconFrame, shine)
    }

    private fun label(
        text: String,
        sizeSp: Float,
        color: String,
        bold: Boolean,
        lines: Int = 1,
    ) = TextView(activity).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(Color.parseColor(color))
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    private suspend fun loadIcon(url: String): Bitmap? {
        icons.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 2_500; readTimeout = 2_500 }
                conn.inputStream.use { BitmapFactory.decodeStream(it) }?.also { icons.put(url, it) }
            } catch (e: Exception) {
                Log.w("CrossAndroidSteam", "Achievement icon failed: ${e.message}")
                null
            }
        }
    }

    companion object {
        const val PREF_KEY = "steam_achievement_popups"
        private val icons = LruCache<String, Bitmap>(24)

        /** The settings switch, read each time so turning it off applies at once. */
        fun enabled(context: Context): Boolean =
            appPreferences(context).getBoolean(PREF_KEY, true)
    }
}
