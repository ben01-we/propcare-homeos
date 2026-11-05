package app.propcare.homeos.domain

/**
 * White-label settings: everything company-specific lives here.
 * Defaults describe Prop Care (NW) LTD as a concept. Prices are example values that
 * the company replaces with its own price list.
 */
object Company {
    const val name = "Prop Care (NW) LTD"
    const val shortName = "PropCare"
    const val conceptLabel = "Concept app built for Prop Care (NW) LTD by A Benjamin Gabrial."
    const val phone = "+447938849865"
    const val phoneDisplay = "07938 849865"
    const val whatsapp = "447938849865"
    const val email = "support@propcarenw.co.uk"
    const val hours = "Mon to Sat, 7am to 7pm. Emergency repairs 24/7."
    const val quoteResponse = "We reply to quote requests within 24 hours."
    const val gasEmergency = "0800 111 999"
    val areas = listOf("Manchester", "Liverpool", "Preston", "Bolton", "Blackburn", "Burnley", "Wigan", "Warrington", "Stockport", "Oldham", "Rochdale", "Salford")

    data class Service(val name: String, val detail: String)
    val services = listOf(
        Service("Property maintenance", "Plumbing, electrical, heating servicing and general repairs."),
        Service("Renovation", "Kitchens, bathrooms, full refurbishments and extensions."),
        Service("Painting and decorating", "Interior and exterior painting, wallpapering, colour advice."),
        Service("Flooring and tiling", "Hardwood, laminate, vinyl, ceramic and stone."),
        Service("Partitions and plasterboard", "Stud walls, fire-rated and acoustic partitions."),
        Service("Plastering and skimming", "Skimming and re-skimming walls and ceilings."),
        Service("Home improvement", "Loft conversions, extensions and structural work."),
    )

    /** Example price bands in pence (low, high) per trade. Replace with the company's real list. */
    val priceGuide: Map<Trade, Pair<Int, Int>> = mapOf(
        Trade.plumber to (8000 to 18000),
        Trade.electrician to (9000 to 20000),
        Trade.gas_engineer to (7000 to 15000),
        Trade.heating to (9000 to 25000),
        Trade.roofer to (15000 to 45000),
        Trade.carpenter to (8000 to 22000),
        Trade.decorator to (15000 to 60000),
        Trade.plasterer to (12000 to 40000),
        Trade.flooring to (20000 to 80000),
        Trade.locksmith to (7000 to 14000),
        Trade.damp to (15000 to 50000),
        Trade.general to (6000 to 15000),
    )
    const val priceGuideNote = "Example price band. PropCare confirms the price after seeing the job."

    /** Fixed prices for safety checks (examples). */
    val checkPrices: Map<String, Int> = mapOf("gas_safety" to 6500, "eicr" to 18000, "epc" to 8500, "alarms" to 4500)
}
