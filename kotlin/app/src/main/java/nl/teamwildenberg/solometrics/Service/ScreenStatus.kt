package nl.teamwildenberg.solometrics.Service

public class ScreenStatus (
    var screenConnected: Boolean,
    var ultraSonicConnected: Boolean,
    var windMeasurement: WindMeasurement?
){
}