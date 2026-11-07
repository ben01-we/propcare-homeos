package app.propcare.homeos.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A calendar-day warranty: expiry is exclusive. No per-second countdown or reset on reopening. */
data class RepairCoverage(val completedAt: Long, val days: Int) {
    init { require(days in 1..730) }
    fun start(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate()
    fun expires(zone: ZoneId = ZoneId.systemDefault()): LocalDate = start(zone).plusDays(days.toLong())
    fun remaining(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long =
        ChronoUnit.DAYS.between(today, expires(zone)).coerceIn(0, days.toLong())
    fun active(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()) = !today.isBefore(start(zone)) && today.isBefore(expires(zone))
}

data class DemoTechnician(val id: String, val name: String, val trade: Trade, val specialties: String,
    val sampleRating: String = "4.8", val sampleHomes: Int = 126) {
    val initials get() = name.split(' ').mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
}

/** Illustrative directory only. Matching never asserts availability, certification or assignment. */
object DemoDirectory {
    val technicians = listOf(
        DemoTechnician("jamie", "Jamie Taylor", Trade.plumber, "Leaks. Taps. Pipework."),
        DemoTechnician("alex", "Alex Morgan", Trade.plumber, "Kitchen and bathroom repairs."),
        DemoTechnician("sam", "Sam Khan", Trade.electrician, "Sockets, lighting and electrical checks."),
        DemoTechnician("gas", "Casey Wilson", Trade.gas_engineer, "Gas appliance enquiries. Credentials require confirmation."),
        DemoTechnician("roof", "Robin Lewis", Trade.roofer, "Roof leaks and gutters."),
        DemoTechnician("damp", "Jordan Reed", Trade.damp, "Damp and mould enquiries."),
        DemoTechnician("general", "Taylor James", Trade.general, "General property maintenance."),
        DemoTechnician("morgan", "Morgan Ellis", Trade.general, "Small repairs and property upkeep.", "4.7", 98),
        DemoTechnician("heating", "Riley Owen", Trade.heating, "Heating and hot-water enquiries.", "4.9", 184),
        DemoTechnician("carpenter", "Charlie Green", Trade.carpenter, "Doors, frames and woodwork.", "4.8", 112),
        DemoTechnician("decorator", "Avery Brooks", Trade.decorator, "Interior finishes and wall repairs.", "4.9", 157),
        DemoTechnician("plasterer", "Drew Parker", Trade.plasterer, "Plaster repairs and surface preparation.", "4.8", 143),
        DemoTechnician("flooring", "Cameron Lee", Trade.flooring, "Floor finishes and damaged tiles.", "4.7", 119),
        DemoTechnician("locksmith", "Harper Lane", Trade.locksmith, "Door, window and lock enquiries.", "4.9", 208),
    )
    fun matching(trade: Trade): List<DemoTechnician> = technicians.filter { it.trade == trade }
    fun find(id: String?): DemoTechnician? = technicians.firstOrNull { it.id == id }
}
