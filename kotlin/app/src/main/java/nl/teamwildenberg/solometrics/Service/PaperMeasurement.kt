package nl.teamwildenberg.solometrics.Service

class PaperMeasurement(
    var counter:Int = 0,
    val epoch: Long,
    val windDirection:Int,
    val windSpeed: Int,
    val boatDirection: Int,
    val batteryPercentage: Int,
    val lat: Double,
    val lon: Double,
    val gpsTime: Long
){

}