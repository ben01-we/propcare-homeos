package app.propcare.homeos.domain

/**
 * Safety rules. Words in the customer's own description always override any AI answer:
 * a gas smell, CO alarm, sparking, water near electrics or fire is an emergency.
 */
object Safety {
    private val gas = Regex("""\b(smell(s|ing)? (of )?gas|gas smell|gas leak|smells? like gas|rotten eggs?)\b""", RegexOption.IGNORE_CASE)
    private val co = Regex("""\b(carbon monoxide|co alarm|co detector)\b""", RegexOption.IGNORE_CASE)
    private val spark = Regex("""\b(spark(s|ing)?|burning smell|smoke from (the )?(socket|switch|plug)|scorch(ed)?|electric shock|melt(ed|ing) (socket|plug|wire))\b""", RegexOption.IGNORE_CASE)
    private val waterElec = Regex(
        """\b(water|leak(ing)?|drip(ping)?)\b[\s\S]{0,40}\b(socket|light|fuse ?box|consumer unit|wiring|switch)\b|\b(socket|light|fuse ?box|consumer unit|wiring|switch)\b[\s\S]{0,40}\b(water|leak(ing)?|drip(ping)?|wet)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val flood = Regex("""\b(flood(ing|ed)?|burst pipe|water pouring|gushing)\b""", RegexOption.IGNORE_CASE)
    private val structural = Regex("""\b(ceiling (is )?(collaps\w*|sagging|bulging)|wall (leaning|bowing|collaps\w*))\b""", RegexOption.IGNORE_CASE)
    private val fire = Regex("""\b(fire|flames?)\b""", RegexOption.IGNORE_CASE)

    val emergencyFlags = setOf("gas_smell", "carbon_monoxide", "electrical_hazard", "water_near_electrics", "structural", "fire_risk")

    fun flagsFromText(text: String): List<String> = buildList {
        if (gas.containsMatchIn(text)) add("gas_smell")
        if (co.containsMatchIn(text)) add("carbon_monoxide")
        if (spark.containsMatchIn(text)) add("electrical_hazard")
        if (waterElec.containsMatchIn(text)) add("water_near_electrics")
        if (structural.containsMatchIn(text)) add("structural")
        if (fire.containsMatchIn(text)) add("fire_risk")
    }

    /** Applies the safety overrides to any triage (AI or offline). */
    fun enforce(t: Triage, description: String): Triage {
        val flags = (t.safetyFlags.filter { it in emergencyFlags } + flagsFromText(description)).distinct()
        var urgency = Urgency.of(t.urgency)
        var trade = Trade.of(t.trade)
        if (flood.containsMatchIn(description) && urgency == Urgency.routine) urgency = Urgency.urgent
        if (flags.any { it in emergencyFlags }) urgency = Urgency.emergency
        if ("gas_smell" in flags || "carbon_monoxide" in flags) trade = Trade.gas_engineer
        else if ("electrical_hazard" in flags || "water_near_electrics" in flags) trade = Trade.electrician
        return t.copy(
            safetyFlags = flags,
            urgency = urgency.name,
            trade = trade.name,
            issueSummary = t.issueSummary.take(160),
            likelyCauses = t.likelyCauses.take(3),
            cannotTell = t.cannotTell.take(3),
            customerAdvice = t.customerAdvice.take(3),
        )
    }

    private data class Rule(val test: Regex, val trade: Trade, val urgency: Urgency, val summary: String, val causes: List<String>, val tell: List<String>, val advice: List<String>)

    private val rules = listOf(
        Rule(Regex("gas (boiler|fire|hob|cooker|meter|appliance|pipe)"), Trade.gas_engineer, Urgency.urgent, "Gas appliance problem", listOf("Faulty appliance or connection"), listOf("The inside of the appliance"), listOf("If you smell gas, leave and call the gas emergency line.")),
        Rule(Regex("spark|burning smell|socket|fuse|tripp|electric|light switch"), Trade.electrician, Urgency.urgent, "Electrical fault", listOf("Faulty socket or switch", "Overloaded circuit"), listOf("The wiring behind the wall"), listOf("Stop using the affected socket or switch.")),
        Rule(Regex("leak|drip|pipe|tap|toilet|sink|water|plumbing"), Trade.plumber, Urgency.urgent, "Water leak or plumbing problem", listOf("Loose fitting or worn washer", "Failed seal", "Damaged pipe"), listOf("Where the water comes from inside the wall or floor"), listOf("Put a container under the drip.", "Turn off the water at the stopcock if it gets worse.")),
        Rule(Regex("boiler|radiator|heating|hot water"), Trade.heating, Urgency.urgent, "Heating or hot water problem", listOf("Low boiler pressure", "Air in the radiators", "Boiler fault"), listOf("The boiler's fault code or internal parts"), listOf("Check the boiler pressure gauge and note any fault code.")),
        Rule(Regex("damp|mould|mold|condensation|black spots"), Trade.damp, Urgency.routine, "Damp or mould", listOf("Condensation", "Penetrating damp", "Rising damp"), listOf("How far the damp goes into the wall"), listOf("Ventilate the room and wipe mould with a mould cleaner.")),
        Rule(Regex("roof|gutter|slate|chimney"), Trade.roofer, Urgency.urgent, "Roof or gutter problem", listOf("Slipped or broken roof tiles", "Blocked gutter"), listOf("Damage only visible from the roof"), emptyList()),
        Rule(Regex("crack|plaster|hole in (the )?wall"), Trade.plasterer, Urgency.routine, "Cracked or damaged plaster", listOf("Normal settlement", "Old plaster losing grip"), listOf("Whether the crack is structural"), listOf("Mark the ends of the crack in pencil, with the date, to see if it grows.")),
        Rule(Regex("floor|tile|laminate|carpet"), Trade.flooring, Urgency.routine, "Damaged flooring", listOf("Wear", "Moisture underneath"), listOf("The condition of the subfloor"), emptyList()),
        Rule(Regex("door|window|lock|key"), Trade.locksmith, Urgency.urgent, "Door, window or lock problem", listOf("Worn lock", "Swollen or dropped door"), listOf("The inside of the lock"), emptyList()),
        Rule(Regex("paint|decorat|wallpaper|peeling"), Trade.decorator, Urgency.routine, "Decorating needed", listOf("Wear and tear"), listOf("What is under the surface"), emptyList()),
    )

    /** Offline triage from the description plus on-device photo labels. Always labelled as offline. */
    fun offlineTriage(description: String, labels: List<String>): Triage {
        val text = description.lowercase(java.util.Locale.ROOT)
        // Specific descriptions beat broad photo labels (e.g. a room's floor must not
        // override a reported boiler fault). Heating, roof and damp precede generic water.
        val priority = listOf(Trade.gas_engineer, Trade.electrician, Trade.heating, Trade.damp,
            Trade.roofer, Trade.plumber, Trade.plasterer, Trade.flooring, Trade.locksmith, Trade.decorator)
        val ordered = rules.sortedBy { priority.indexOf(it.trade) }
        val described = ordered.firstOrNull { it.test.containsMatchIn(text) }
        val objects = labels.map { it.lowercase(java.util.Locale.ROOT).trim() }.toSet()
        val photoTrade = when {
            objects.any { it in setOf("tap", "faucet", "sink", "plumbing fixture", "toilet", "pipe") } -> Trade.plumber
            objects.any { it in setOf("electrical wiring", "electrical connector", "socket", "light switch") } -> Trade.electrician
            objects.any { it in setOf("radiator", "boiler", "water heater") } -> Trade.heating
            objects.any { it in setOf("roof", "gutter", "roof tiles") } -> Trade.roofer
            objects.any { it in setOf("mold", "mould", "damp") } -> Trade.damp
            objects.any { it in setOf("flooring", "wood flooring", "tile", "laminate flooring") } -> Trade.flooring
            objects.any { it in setOf("door", "window", "lock") } -> Trade.locksmith
            else -> null
        }
        val r = described ?: rules.firstOrNull { it.trade == photoTrade }
        val base = if (r != null) Triage(
            issueSummary = if (described == null) "Possible ${r.trade.label.lowercase()} issue" else r.summary,
            likelyCauses = r.causes, trade = r.trade.name,
            urgency = if (described != null) r.urgency.name else Urgency.routine.name,
            cannotTell = (if (described == null) listOf("A photo of an object does not prove it is faulty") else emptyList()) + r.tell,
            customerAdvice = if (described == null) listOf("Add what is happening: leaking, loose, damaged or not working.") else r.advice,
            confidence = "low", source = "offline", labels = labels,
        ) else Triage(
            issueSummary = "More detail will help identify this issue",
            cannotTell = listOf("The image does not show a specific fault clearly enough"),
            customerAdvice = listOf("Take a close-up in good light and describe what changed.", "Mention the room and whether it leaks, cracks or stops working."),
            source = "offline", labels = labels,
        )
        return enforce(base, description)
    }

    /** Safety rules on text alone, for reports without a photo. */
    fun textOnly(description: String): Triage = offlineTriage(description, emptyList())
}
