package com.findora.app

import java.util.Locale

/** Only sources with an identifiable free/open license are eligible for Internet Archive downloads. */
internal object MediaLicensePolicy {
    fun isRecognizedOpenLicense(value: String): Boolean {
        val lower = value.lowercase(Locale.ROOT)
        return lower.contains("creativecommons.org/licenses/") ||
            lower.contains("creativecommons.org/publicdomain/") ||
            lower.contains("creativecommons.org/cc0/")
    }
}
