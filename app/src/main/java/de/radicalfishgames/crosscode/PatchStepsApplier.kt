package de.radicalfishgames.crosscode

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** Returns the contents of an IMPORT/INCLUDE path (optionally "game:" or "mod:" prefixed), or null. */
typealias FileResolver = (path: String) -> String?

/**
 * Native implementation of CCLoader Patch Steps: ENTER, EXIT, SET_KEY, INIT_KEY,
 * ADD_ARRAY_ELEMENT, REMOVE_ARRAY_ELEMENT, COPY, PASTE, IMPORT, INCLUDE, FOR_IN, DEBUG, COMMENT.
 * IF is only partially supported: its JS condition can't be evaluated, so thenSteps always run.
 *
 * @see https://github.com/CCDirectLink/CLS/blob/master/standards/patch-steps.md
 */
class PatchStepsApplier {
    companion object {
        private const val TAG = "PatchStepsApplier"

        /**
         * Applies [patchJson] to [originalJson]: a JSON array is run as Patch Steps, an object is
         * deep-merged. Returns null if patching fails.
         */
        fun apply(
            originalJson: String,
            patchJson: String,
            fileResolver: FileResolver? = null,
        ): String? {
            return try {
                val root = JSONObject(originalJson)
                val cleanedPatch = stripTrailingCommas(patchJson)
                val trimmed = cleanedPatch.trim()

                if (trimmed.startsWith("[")) {
                    val steps = JSONArray(cleanedPatch)
                    val state = PatchState(fileResolver = fileResolver, current = root)
                    applySteps(root, steps, state)
                } else if (trimmed.startsWith("{")) {
                    val patch = JSONObject(cleanedPatch)
                    deepMerge(root, patch)
                } else {
                    Log.e(TAG, "Invalid patch format - must be JSON array or object")
                    return null
                }

                root.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply patch", e)
                null
            }
        }

        private class PatchState(
            val memory: MutableMap<String, Any> = mutableMapOf(),
            val fileResolver: FileResolver? = null,
            var debugEnabled: Boolean = false,
            val stack: MutableList<Any> = mutableListOf(),
            val pathStack: MutableList<String> = mutableListOf(),
            var current: Any,
        ) {
            fun currentPath(): String = pathStack.joinToString("/").ifEmpty { "<root>" }

            /** Create child state for INCLUDE (fresh stacks, same memory/resolver). */
            fun childState(): PatchState =
                PatchState(
                    memory = memory,
                    fileResolver = fileResolver,
                    debugEnabled = debugEnabled,
                    stack = mutableListOf(),
                    pathStack = mutableListOf(),
                    current = current,
                )
        }

        /**
         * Strips trailing commas, which hand-written mod JSON often has but org.json rejects.
         * Tracks string state so ",}" or ",]" inside strings is left intact.
         */
        private fun stripTrailingCommas(json: String): String {
            val result = StringBuilder(json.length)
            var inString = false
            var escape = false
            var pendingComma = -1

            for (ch in json) {
                if (escape) {
                    escape = false
                    result.append(ch)
                    continue
                }

                when {
                    ch == '\\' && inString -> {
                        escape = true
                        result.append(ch)
                    }

                    ch == '"' -> {
                        inString = !inString
                        result.append(ch)
                    }

                    !inString && ch == ',' -> {
                        pendingComma = result.length
                        result.append(ch)
                    }

                    !inString && (ch == '}' || ch == ']') -> {
                        if (pendingComma >= 0) {
                            result.delete(pendingComma, result.length)
                        }
                        result.append(ch)
                        pendingComma = -1
                    }

                    !inString && ch.isWhitespace() -> {
                        result.append(ch)
                    }

                    else -> {
                        pendingComma = -1
                        result.append(ch)
                    }
                }
            }

            return result.toString()
        }

        /** Objects are merged recursively; arrays and primitives are replaced. */
        private fun deepMerge(
            target: JSONObject,
            patch: JSONObject,
        ) {
            for (key in patch.keys()) {
                val patchValue = patch.opt(key) ?: continue

                if (patchValue is JSONObject && target.has(key)) {
                    val targetValue = target.opt(key)
                    if (targetValue is JSONObject) {
                        deepMerge(targetValue, patchValue)
                        continue
                    }
                }

                target.put(key, patchValue)
            }
        }

        /** Objects are deep-merged; array elements are appended. */
        private fun mergeAny(
            target: Any,
            source: Any,
        ) {
            when {
                target is JSONObject && source is JSONObject -> {
                    deepMerge(target, source)
                }

                target is JSONArray && source is JSONArray -> {
                    for (i in 0 until source.length()) {
                        target.put(source.get(i))
                    }
                }

                else -> {
                    throw IllegalArgumentException(
                        "Cannot merge ${source::class.simpleName} into ${target::class.simpleName}",
                    )
                }
            }
        }

        private fun deepCopy(value: Any): Any =
            when (value) {
                is JSONObject -> JSONObject(value.toString())
                is JSONArray -> JSONArray(value.toString())
                else -> value // Primitives are immutable
            }

        private fun deepCopyNullable(value: Any?): Any? = if (value == null) null else deepCopy(value)

        private fun applySteps(
            root: JSONObject,
            steps: JSONArray,
            state: PatchState,
        ) {
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val type = step.getString("type")
                try {
                    when (type) {
                        "ENTER" -> {
                            val index = step.get("index")
                            if (index is JSONArray) {
                                for (j in 0 until index.length()) {
                                    val idx = index.get(j)
                                    state.stack.add(state.current)
                                    state.pathStack.add(idx.toString())
                                    state.current = navigateSingle(state.current, idx)
                                }
                            } else {
                                state.stack.add(state.current)
                                state.pathStack.add(index.toString())
                                state.current = navigateSingle(state.current, index)
                            }
                        }

                        "EXIT" -> {
                            val count = step.optInt("count", 1)
                            repeat(count) {
                                if (state.stack.isEmpty() || state.pathStack.isEmpty()) {
                                    throw IllegalStateException("EXIT: stack underflow (count=$count) at path ${state.currentPath()}")
                                }
                                state.current = state.stack.removeAt(state.stack.lastIndex)
                                state.pathStack.removeAt(state.pathStack.lastIndex)
                            }
                        }

                        "SET_KEY" -> {
                            val index = step.get("index")
                            val content = step.opt("content")
                            setKey(state.current, index, deepCopyNullable(content))
                        }

                        "INIT_KEY" -> {
                            val index = step.get("index")
                            val content = step.opt("content")
                            initKey(state.current, index, deepCopyNullable(content))
                        }

                        "ADD_ARRAY_ELEMENT" -> {
                            val index = step.optInt("index", -1)
                            val content = step.get("content")
                            addArrayElement(state.current, index, deepCopy(content))
                        }

                        "REMOVE_ARRAY_ELEMENT" -> {
                            val index = step.getInt("index")
                            removeArrayElement(state.current, index)
                        }

                        "COPY" -> {
                            val alias = step.getString("alias")
                            state.memory[alias] = deepCopy(state.current)
                        }

                        "PASTE" -> {
                            val alias = step.getString("alias")
                            val stored =
                                state.memory[alias]
                                    ?: throw IllegalArgumentException("Unknown COPY alias: $alias")
                            val copied = deepCopy(stored)
                            when (state.current) {
                                is JSONArray -> {
                                    // Arrays: behaves like ADD_ARRAY_ELEMENT
                                    val index = step.optInt("index", -1)
                                    addArrayElement(state.current, index, copied)
                                }

                                is JSONObject -> {
                                    // Objects: behaves like SET_KEY (index required)
                                    val index = step.get("index")
                                    setKey(state.current, index, copied)
                                }

                                else -> {
                                    throw IllegalArgumentException(
                                        "PASTE not supported on ${state.current::class.simpleName}",
                                    )
                                }
                            }
                        }

                        "IMPORT" -> {
                            val src = step.getString("src")
                            val resolver =
                                state.fileResolver
                                    ?: throw IllegalStateException("IMPORT requires fileResolver")
                            // IMPORT defaults to game: when no protocol is given.
                            val resolvedSrc = if (!src.contains(':')) "game:$src" else src
                            val content =
                                resolver(resolvedSrc)
                                    ?: throw IllegalArgumentException("Failed to resolve IMPORT: $src")

                            val cleaned = stripTrailingCommas(content)
                            var imported: Any = JSONObject(cleaned)

                            val path = step.optJSONArray("path")
                            if (path != null) {
                                imported = navigate(imported, path)
                            }

                            // Without an index, the import is merged into the current value.
                            val index = step.opt("index")
                            if (index != null) {
                                setKey(state.current, index, imported)
                            } else {
                                mergeAny(state.current, imported)
                            }
                        }

                        "INCLUDE" -> {
                            val src = step.getString("src")
                            val resolver =
                                state.fileResolver
                                    ?: throw IllegalStateException("INCLUDE requires fileResolver")
                            // INCLUDE defaults to mod: when no protocol is given.
                            val resolvedSrc = if (!src.contains(':')) "mod:$src" else src
                            val patchContent =
                                resolver(resolvedSrc)
                                    ?: throw IllegalArgumentException("Failed to resolve INCLUDE: $src")

                            val cleaned = stripTrailingCommas(patchContent)
                            val includeSteps = JSONArray(cleaned)
                            // Fresh stack but same current position and COPY memory.
                            applySteps(root, includeSteps, state.childState())
                        }

                        "FOR_IN" -> {
                            val values = step.get("values")
                            val keyword = step.get("keyword")
                            val body = step.getJSONArray("body")

                            val valuesList =
                                when (values) {
                                    is JSONArray -> (0 until values.length()).map { values.get(it) }
                                    else -> throw IllegalArgumentException("FOR_IN values must be array")
                                }

                            // Iterations share state, so ENTER/EXIT in the body carry over between them.
                            for (value in valuesList) {
                                val substitutedBody = substituteKeyword(body, keyword, value)
                                applySteps(root, substitutedBody, state)
                            }
                        }

                        "DEBUG" -> {
                            state.debugEnabled = step.optBoolean("value", false)
                        }

                        "COMMENT" -> {
                            if (state.debugEnabled) {
                                val commentValue = step.opt("value")
                                Log.d(TAG, "COMMENT: $commentValue")
                            }
                        }

                        "IF" -> {
                            // Conditions are JS expressions, which can't be evaluated natively.
                            val condition = step.opt("condition")
                            val thenSteps = step.optJSONArray("thenSteps")
                            val elseSteps = step.optJSONArray("elseSteps")
                            Log.w(
                                TAG,
                                "IF condition not evaluated (JS not supported), executing thenSteps unconditionally. " +
                                    "Condition: $condition, elseSteps present: ${elseSteps != null}",
                            )
                            if (thenSteps != null) {
                                applySteps(root, thenSteps, state)
                            }
                        }

                        else -> {
                            Log.w(TAG, "Unsupported patch step type: $type")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply patch step $i ($type) at path: ${state.currentPath()}")
                    Log.e(TAG, "Step content: $step", e)
                    throw e
                }
            }
        }

        /**
         * Returns a copy of [body] with [keyword] replaced by [value]. Keywords are treated as
         * regexes to match CCLoader; an object keyword maps each key's pattern to value[key].
         */
        private fun substituteKeyword(
            body: JSONArray,
            keyword: Any,
            value: Any,
        ): JSONArray {
            val clonedBody = JSONArray(body.toString())
            when (keyword) {
                is String -> {
                    valueInsertion(clonedBody, keyword.toRegex(), value)
                }

                is JSONObject -> {
                    for (key in keyword.keys()) {
                        val pattern = keyword.getString(key)
                        val replacement =
                            if (value is JSONObject) {
                                value.opt(key) ?: JSONObject.NULL
                            } else {
                                value
                            }
                        valueInsertion(clonedBody, pattern.toRegex(), replacement)
                    }
                }
            }
            return clonedBody
        }

        /** Mirrors CCLoader's valueInsertion: regex-replaces within all nested string values. */
        private fun valueInsertion(
            obj: Any,
            pattern: Regex,
            replacement: Any,
        ) {
            val replacementStr =
                when (replacement) {
                    is String -> replacement
                    is Number, is Boolean -> replacement.toString()
                    JSONObject.NULL -> ""
                    else -> replacement.toString()
                }

            when (obj) {
                is JSONArray -> {
                    for (i in 0 until obj.length()) {
                        when (val child = obj.get(i)) {
                            is String -> {
                                obj.put(i, child.replace(pattern, replacementStr))
                            }

                            is JSONObject, is JSONArray -> {
                                valueInsertion(child, pattern, replacement)
                            }
                        }
                    }
                }

                is JSONObject -> {
                    for (key in obj.keys().asSequence().toList()) {
                        when (val child = obj.opt(key)) {
                            is String -> {
                                obj.put(key, child.replace(pattern, replacementStr))
                            }

                            is JSONObject, is JSONArray -> {
                                valueInsertion(child, pattern, replacement)
                            }
                        }
                    }
                }
            }
        }

        private fun navigate(
            current: Any,
            index: Any,
        ): Any =
            when (index) {
                is JSONArray -> {
                    var result = current
                    for (i in 0 until index.length()) {
                        result = navigateSingle(result, index.get(i))
                    }
                    result
                }

                else -> {
                    navigateSingle(current, index)
                }
            }

        private fun navigateSingle(
            current: Any,
            key: Any,
        ): Any =
            when (current) {
                is JSONObject -> current.get(key.toString())
                is JSONArray -> current.get(toArrayIndex(key))
                else -> throw IllegalArgumentException("Cannot navigate into $current with key $key")
            }

        private fun toArrayIndex(index: Any): Int =
            when (index) {
                is Int -> index
                is Number -> index.toInt()
                else -> index.toString().toInt()
            }

        private fun setKey(
            current: Any,
            index: Any,
            content: Any?,
        ) {
            when (current) {
                is JSONObject -> {
                    if (content == null || content == JSONObject.NULL) {
                        current.remove(index.toString())
                    } else {
                        current.put(index.toString(), content)
                    }
                }

                is JSONArray -> {
                    val arrayIndex = index.toString().toIntOrNull()
                    if (arrayIndex != null) {
                        current.put(arrayIndex, content ?: JSONObject.NULL)
                    } else {
                        Log.w(TAG, "Cannot set string key '$index' on JSONArray (not supported in JSON)")
                    }
                }

                else -> {
                    Log.w(TAG, "Cannot SET_KEY on $current")
                }
            }
        }

        private fun initKey(
            current: Any,
            index: Any,
            content: Any?,
        ) {
            when (current) {
                is JSONObject -> {
                    val key = index.toString()
                    if (!current.has(key)) {
                        current.put(key, content ?: JSONObject.NULL)
                    }
                }

                is JSONArray -> {
                    val idx = toArrayIndex(index)
                    if (idx >= current.length() || current.isNull(idx)) {
                        current.put(idx, content ?: JSONObject.NULL)
                    }
                }

                else -> {
                    Log.w(TAG, "Cannot INIT_KEY on $current")
                }
            }
        }

        private fun addArrayElement(
            current: Any,
            index: Int,
            content: Any,
        ) {
            if (current !is JSONArray) {
                Log.w(TAG, "Cannot ADD_ARRAY_ELEMENT on non-array")
                return
            }

            if (index < 0 || index >= current.length()) {
                current.put(content)
            } else {
                val newArray = JSONArray()
                for (i in 0 until current.length()) {
                    if (i == index) {
                        newArray.put(content)
                    }
                    newArray.put(current.get(i))
                }
                while (current.length() > 0) {
                    current.remove(0)
                }
                for (i in 0 until newArray.length()) {
                    current.put(newArray.get(i))
                }
            }
        }

        private fun removeArrayElement(
            current: Any,
            index: Int,
        ) {
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
