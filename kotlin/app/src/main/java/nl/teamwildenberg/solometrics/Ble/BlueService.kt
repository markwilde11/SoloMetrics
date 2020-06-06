package nl.teamwildenberg.solometrics.Ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
public class BlueService  (
    var name: String,

    var device : BluetoothGattService
): Parcelable {

}