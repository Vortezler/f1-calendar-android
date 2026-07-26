package com.praval.f1calendar.core

/**
 * Maps the country names the Ergast/Jolpica API reports for a circuit's location onto a
 * regional-indicator flag emoji. The API uses informal names ("UK", "USA", "UAE") rather than ISO
 * codes, and it has never been consistent across eras, so the lookup is by name.
 */
object CountryFlags {

    private val isoByCountry: Map<String, String> = mapOf(
        "argentina" to "AR",
        "australia" to "AU",
        "austria" to "AT",
        "azerbaijan" to "AZ",
        "bahrain" to "BH",
        "belgium" to "BE",
        "brazil" to "BR",
        "canada" to "CA",
        "china" to "CN",
        "france" to "FR",
        "germany" to "DE",
        "hungary" to "HU",
        "india" to "IN",
        "italy" to "IT",
        "japan" to "JP",
        "korea" to "KR",
        "south korea" to "KR",
        "malaysia" to "MY",
        "mexico" to "MX",
        "monaco" to "MC",
        "morocco" to "MA",
        "netherlands" to "NL",
        "portugal" to "PT",
        "qatar" to "QA",
        "russia" to "RU",
        "saudi arabia" to "SA",
        "singapore" to "SG",
        "south africa" to "ZA",
        "spain" to "ES",
        "sweden" to "SE",
        "switzerland" to "CH",
        "turkey" to "TR",
        "uae" to "AE",
        "united arab emirates" to "AE",
        "uk" to "GB",
        "united kingdom" to "GB",
        "great britain" to "GB",
        "england" to "GB",
        "usa" to "US",
        "united states" to "US",
        "vietnam" to "VN",
    )

    /** Chequered flag stands in for anything unmapped, which keeps row layouts stable. */
    private const val FALLBACK = "🏁"

    private val cache = HashMap<String, String>()

    fun emojiFor(country: String?): String {
        val key = country?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return FALLBACK
        return cache.getOrPut(key) {
            val iso = isoByCountry[key] ?: return@getOrPut FALLBACK
            buildString { iso.forEach { appendCodePoint(0x1F1E6 + (it - 'A')) } }
        }
    }
}
