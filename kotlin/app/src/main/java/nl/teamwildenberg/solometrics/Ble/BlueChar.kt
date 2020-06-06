package nl.teamwildenberg.solometrics.Ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
public class BlueChar  (
    var name: String,
    var value: ByteArray
 //   var service : BluetoothGattService
): Parcelable {

}