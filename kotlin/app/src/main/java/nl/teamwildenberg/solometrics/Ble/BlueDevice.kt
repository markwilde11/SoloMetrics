package nl.teamwildenberg.solometrics.Ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
public class BlueDevice  (
    var name: String,
//    var id: String,
    var address: String,
    var type: DeviceTypeEnum,
//    var addressULong : Int,
    var isConnectable: Boolean,
    var device : BluetoothDevice? = null

): Parcelable {
    var gatt: BluetoothGatt? = null
}