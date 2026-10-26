package config

import kotlin.test.*

class ShippingProviderConfigTest {
    private val settings = mapOf(
        "FEDEX_MODE" to "PRODUCTION",
        "FEDEX_PRODUCTION_CLIENT_ID" to "pt-client",
        "FEDEX_PRODUCTION_CLIENT_SECRET" to "pt-secret",
        "FEDEX_PRODUCTION_ACCOUNT_NUMBER" to "pt-account",
        "FEDEX_PRODUCTION_UK_CLIENT_ID" to "uk-client",
        "FEDEX_PRODUCTION_UK_CLIENT_SECRET" to "uk-secret",
        "FEDEX_PRODUCTION_UK_ACCOUNT_NUMBER" to "uk-account"
    )
    @Test fun ptIsDefaultAndUkHasItsOwnCredentials() {
        val pt = ShippingProviderConfig.load(read = settings::get)
        assertEquals("PT", pt.account?.countryCode)
        assertEquals("pt-account", pt.account?.accountNumber)
        val uk = ShippingProviderConfig.load("GB", settings::get)
        assertEquals("GB", uk.account?.countryCode)
        assertEquals("uk-account", uk.account?.accountNumber)
    }
    @Test fun missingUkCredentialsNeverFallBackToPt() {
        val onlyPt = settings.filterKeys { !it.contains("_UK_") }
        assertFailsWith<IllegalStateException> { ShippingProviderConfig.load("GB", onlyPt::get) }
    }
    @Test fun sandboxDoesNotReuseProductionCredentials() {
        val sandbox = settings + ("FEDEX_MODE" to "SANDBOX")
        assertFailsWith<IllegalStateException> { ShippingProviderConfig.load(read = sandbox::get) }
    }
    @Test fun explicitPtCredentialsOverrideLegacyValues() {
        val values = settings + ("FEDEX_PRODUCTION_PT_ACCOUNT_NUMBER" to "explicit-pt")
        assertEquals("explicit-pt", ShippingProviderConfig.load(read = values::get).account?.accountNumber)
    }
}
