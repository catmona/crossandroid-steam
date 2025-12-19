package de.radicalfishgames.crosscode

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Applies CCLoader Patch Steps to JSON data.
 * Supports the core operations: ENTER, EXIT, SET_KEY, INIT_KEY, ADD_ARRAY_ELEMENT, REMOVE_ARRAY_ELEMENT
 *
 * @see https://github.com/CCDirectLink/CLS/blob/master/standards/patch-steps.md
 */
class PatchStepsApplier {

    companion object {
        private const val TAG = "PatchStepsApplier"

        /**
         * Apply a patch to JSON data.
         * @param originalJson The original JSON string
         * @param patchJson The patch steps JSON array string
         * @return The patched JSON string, or null if patching failed
         */
        fun apply(originalJson: String, patchJson: String): String? {
            return try {
                val root = JSONObject(originalJson)
                val steps = JSONArray(patchJson)

                applySteps(root, steps)

                root.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply patch", e)
                null
            }
        }

        private fun applySteps(root: JSONObject, steps: JSONArray) {
            // Stack of parent values for ENTER/EXIT navigation
            val stack = mutableListOf<Any>()
            var current: Any = root

            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val type = step.getString("type")

                when (type) {
                    "ENTER" -> {
                        val index = step.get("index")
                        stack.add(current)
                        current = navigate(current, index)
                    }
                    "EXIT" -> {
                        if (stack.isNotEmpty()) {
                            current = stack.removeAt(stack.lastIndex)
                        }
                    }
                    "SET_KEY" -> {
                        val index = step.get("index")
                        val content = step.opt("content")
                        setKey(current, index, content)
                    }
                    "INIT_KEY" -> {
                        val index = step.get("index")
                        val content = step.opt("content")
                        initKey(current, index, content)
                    }
                    "ADD_ARRAY_ELEMENT" -> {
                        val index = step.optInt("index", -1)
                        val content = step.get("content")
                        addArrayElement(current, index, content)
                    }
                    "REMOVE_ARRAY_ELEMENT" -> {
                        val index = step.getInt("index")
                        removeArrayElement(current, index)
                    }
                    "COMMENT", "DEBUG" -> {
                        // Ignore these steps
                    }
                    else -> {
                        Log.w(TAG, "Unsupported patch step type: $type")
                    }
                }
            }
        }

        /**
         * Navigate into a nested structure.
         * Index can be a single key/index or an array of keys/indices for deep navigation.
         */
        private fun navigate(current: Any, index: Any): Any {
            return when (index) {
                is JSONArray -> {
                    var result = current
                    for (i in 0 until index.length()) {
                        result = navigateSingle(result, index.get(i))
                    }
                    result
                }
                else -> navigateSingle(current, index)
            }
        }

        private fun navigateSingle(current: Any, key: Any): Any {
            return when (current) {
                is JSONObject -> current.get(key.toString())
                is JSONArray -> current.get(key as Int)
                else -> throw IllegalArgumentException("Cannot navigate into $current with key $key")
            }
        }

        /**
         * Set a key/index to a value.
         */
        private fun setKey(current: Any, index: Any, content: Any?) {
            when (current) {
                is JSONObject -> {
                    if (content == null || content == JSONObject.NULL) {
                        current.remove(index.toString())
                    } else {
                        current.put(index.toString(), content)
                    }
                }
                is JSONArray -> {
                    val idx = when (index) {
                        is Int -> index
                        is Number -> index.toInt()
                        else -> index.toString().toInt()
                    }
                    current.put(idx, content ?: JSONObject.NULL)
                }
                else -> Log.w(TAG, "Cannot SET_KEY on $current")
            }
        }

        /**
         * Set a key only if it doesn't exist.
         */
        private fun initKey(current: Any, index: Any, content: Any?) {
            when (current) {
                is JSONObject -> {
                    val key = index.toString()
                    if (!current.has(key)) {
                        current.put(key, content ?: JSONObject.NULL)
                    }
                }
                is JSONArray -> {
                    val idx = when (index) {
                        is Int -> index
                        is Number -> index.toInt()
                        else -> index.toString().toInt()
                    }
                    if (idx >= current.length() || current.isNull(idx)) {
                        current.put(idx, content ?: JSONObject.NULL)
                    }
                }
                else -> Log.w(TAG, "Cannot INIT_KEY on $current")
            }
        }

        /**
         * Add an element to an array.
         */
        private fun addArrayElement(current: Any, index: Int, content: Any) {
            if (current !is JSONArray) {
                Log.w(TAG, "Cannot ADD_ARRAY_ELEMENT on non-array")
                return
            }

            if (index < 0 || index >= current.length()) {
                // Append to end
                current.put(content)
            } else {
                // Insert at index - need to shift elements
                val newArray = JSONArray()
                for (i in 0 until current.length()) {
                    if (i == index) {
                        newArray.put(content)
                    }
                    newArray.put(current.get(i))
                }
                // Replace contents
                while (current.length() > 0) {
                    current.remove(0)
                }
                for (i in 0 until newArray.length()) {
                    current.put(newArray.get(i))
                }
            }
        }

        /**
         * Remove an element from an array.
         */
        private fun removeArrayElement(current: Any, index: Int) {
            if (current !is JSONArray) {
                Log.w(TAG, "Cannot REMOVE_ARRAY_ELEMENT on non-array")
                return
            }

            if (index >= 0 && index < current.length()) {
                current.remove(index)
            }
        }
    }
}
