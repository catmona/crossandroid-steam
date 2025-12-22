package de.radicalfishgames.crosscode.preferences

sealed class SaveValidationResult {
    data object Valid : SaveValidationResult()

    data class Invalid(
        val reason: String,
    ) : SaveValidationResult()
}

object SaveValidator {
    private const val PREFIX = "[-!_0_!-]"

    fun validate(content: String): SaveValidationResult {
        val trimmed = content.trim()

        if (trimmed.isEmpty()) {
            return SaveValidationResult.Invalid("Save string is empty")
        }

        if (!trimmed.startsWith(PREFIX)) {
            return SaveValidationResult.Invalid("Invalid save format: missing header")
        }

        // Use SaveParser for decryption to avoid code duplication
        return if (SaveParser.decrypt(trimmed) != null) {
            SaveValidationResult.Valid
        } else {
            SaveValidationResult.Invalid("Invalid save format: decryption failed")
        }
    }
}
