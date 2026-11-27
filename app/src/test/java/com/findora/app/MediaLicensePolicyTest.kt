package com.findora.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLicensePolicyTest {
    @Test
    fun acceptsCreativeCommonsAndPublicDomainLicenses() {
        assertTrue(MediaLicensePolicy.isRecognizedOpenLicense("https://creativecommons.org/licenses/by/4.0/"))
        assertTrue(MediaLicensePolicy.isRecognizedOpenLicense("https://creativecommons.org/publicdomain/zero/1.0/"))
        assertTrue(MediaLicensePolicy.isRecognizedOpenLicense("https://creativecommons.org/publicdomain/mark/1.0/"))
    }

    @Test
    fun rejectsMissingOrUnrecognizedLicenses() {
        assertFalse(MediaLicensePolicy.isRecognizedOpenLicense(""))
        assertFalse(MediaLicensePolicy.isRecognizedOpenLicense("https://example.com/copyright"))
    }
}
