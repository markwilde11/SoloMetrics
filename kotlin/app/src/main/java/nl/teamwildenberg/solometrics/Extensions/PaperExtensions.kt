package nl.teamwildenberg.solometrics.Extensions

import nl.teamwildenberg.SoloMetrics.Service.WindMeasurement
import nl.teamwildenberg.solometrics.Service.PaperMeasurement
import java.time.Instant

public fun WindMeasurement.toPaper(counter: Int):PaperMeasurement{
    return PaperMeasurement(counter, Instant.now().epochSecond, this.WindDirection, this.WindSpeed, this.BoatDirection, this.BatteryPercentage )
}
