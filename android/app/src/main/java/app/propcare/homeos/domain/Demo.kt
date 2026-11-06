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

data class DemoTechnician(val id: String, val name: String, val trade: Trade, val specialties: String) {
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
    )
    fun matching(trade: Trade): List<DemoTechnician> = technicians.filter { it.trade == trade }
    fun find(id: String?): DemoTechnician? = technicians.firstOrNull { it.id == id }
}
