package nl.teamwildenberg.solometrics.Adapter

import nl.teamwildenberg.solometrics.Service.PaperTrace
import nl.teamwildenberg.solometrics.Service.WindMeasurement

public class PaperTraceItem(
    val Trace: PaperTrace,
    var PartionList: List<List<WindMeasurement>>? = null
) {
}