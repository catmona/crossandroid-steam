package de.radicalfishgames.crosscode.preferences

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import de.radicalfishgames.crosscode.R

/**
 * Drop-in replacement for [androidx.preference.SeekBarPreference] rendered with a Material 3
 * [Slider]. Persists an Int under the same key, so existing stored values keep working.
 */
class SliderPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs) {
        private val max: Int
        private val showValue: Boolean
        private var value = 0

        init {
            layoutResource = R.layout.pref_slider
            val a = context.obtainStyledAttributes(attrs, R.styleable.SliderPreference)
            try {
                max = a.getInt(R.styleable.SliderPreference_android_max, 100)
                showValue = a.getBoolean(R.styleable.SliderPreference_showSliderValue, false)
            } finally {
                a.recycle()
            }
        }

        override fun onGetDefaultValue(
            a: TypedArray,
            index: Int,
        ): Any = a.getInt(index, 0)

        override fun onSetInitialValue(defaultValue: Any?) {
            value = getPersistedInt(defaultValue as? Int ?: 0).coerceIn(0, max)
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            val slider = holder.findViewById(R.id.slider) as Slider
            val valueText = holder.findViewById(R.id.slider_value) as TextView

            valueText.visibility = if (showValue) android.view.View.VISIBLE else android.view.View.GONE
            valueText.text = value.toString()

            slider.clearOnChangeListeners()
            slider.valueFrom = 0f
            slider.valueTo = max.toFloat()
            slider.stepSize = 1f
            slider.value = value.toFloat()
            slider.addOnChangeListener { _, newValue, fromUser ->
                if (!fromUser) return@addOnChangeListener
                val newInt = newValue.toInt()
                if (newInt != value && callChangeListener(newInt)) {
                    value = newInt
                    persistInt(newInt)
                    valueText.text = newInt.toString()
                }
            }
        }
    }
