package de.radicalfishgames.crosscode.preferences

import androidx.annotation.StringRes
import de.radicalfishgames.crosscode.R

sealed class SaveValidationResult {
    data object Valid : SaveValidationResult()

    data class Invalid(
        @param:StringRes val reasonResId: Int,
    ) : SaveValidationResult()
}

object SaveValidator {
    private const val PREFIX = "[-!_0_!-]"

    fun validate(content: String): SaveValidationResult {
        val trimmed = content.trim()

        if (trimmed.isEmpty()) {
            return SaveValidationResult.Invalid(R.string.error_save_empty)
        }

        if (!trimmed.startsWith(PREFIX)) {
            return SaveValidationResult.Invalid(R.string.error_save_invalid_header)
        }

        // Use SaveParser for decryption to avoid code duplication
        return if (SaveParser.decrypt(trimmed) != null) {
            SaveValidationResult.Valid
        } else {
            SaveValidationResult.Invalid(R.string.error_save_decryption_failed)
        }
    }
}
