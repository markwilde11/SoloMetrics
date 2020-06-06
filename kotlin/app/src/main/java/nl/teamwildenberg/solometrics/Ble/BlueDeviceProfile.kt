package nl.teamwildenberg.solometrics.Ble

public class BlueDeviceProfile(
    var name: String,
    var servicesFilter: String,
    var charToConnect: String,
    var doNotify : Boolean = false){
}