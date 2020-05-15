package nl.teamwildenberg.SoloMetrics.Ble

public class BlueDeviceProfile(
    var name: String,
    var servicesFilter: String,
    var charToConnect: String,
    var doNotify : Boolean = false){
}