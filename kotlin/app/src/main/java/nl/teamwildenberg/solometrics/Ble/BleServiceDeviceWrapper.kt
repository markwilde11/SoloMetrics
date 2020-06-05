package nl.teamwildenberg.solometrics.Ble

import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.STATE_CONNECTED
import android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
import android.content.Context
import android.util.Log
import io.reactivex.Observable
import nl.teamwildenberg.solometrics.MainApplication
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*


class BleServiceDeviceWrapper (){
    private lateinit var context: Context

    init{
        context = MainApplication.applicationContext()
    }

    fun setCharValue(theBlueDevice: BlueDevice, servicesFilter: String, charToConnect: String, value:Short):Boolean {
        return setCharValue(theBlueDevice,servicesFilter,charToConnect,value.toByteArray())
    }

    fun setCharValue(theBlueDevice: BlueDevice, servicesFilter: String, charToConnect: String, value:ByteArray):Boolean {
        val TAG: String = "SETCHARVAL:"
        val gatt = theBlueDevice.gatt
        //check mBluetoothGatt is available
        if (gatt == null) {
            Log.e(TAG, "lost connection");
            return false;
        } else {

            val service = gatt.getService(UUID.fromString(servicesFilter));
            if (service == null) {
                Log.e(TAG, "service not found!");
                return false;
            } else {
                val char = service.getCharacteristic(UUID.fromString(charToConnect));
                if (char == null) {
                    Log.e(TAG, "char not found!");
                    return false;
                } else {
                    char.setValue(value);
                    val status = gatt.writeCharacteristic (char);
                    return status;
                }
            }
        }
    }

    fun setCharValue(theBlueDevice: BlueDevice, servicesFilter: String, charToConnect: String, value:Byte):Boolean {
        val theByte :ByteArray = ByteArray(1)
        theByte[0] = value
        return setCharValue(theBlueDevice, servicesFilter, charToConnect, theByte)
    }

    fun Short.toByteArray(): ByteArray {
        return byteArrayOf((this.toInt() and 0x00FF).toByte(), ((this.toInt() and 0xFF00) shr (8)).toByte())
    }

    /// KUDOS
    /// https://medium.com/@bananaumai/kotlin-convert-integers-into-bytearray-ca7a2bd9718a
        fun Int.toByteArray(isBigEndian: Boolean = true): ByteArray {
            var bytes = byteArrayOf()

            var n = this

            if (n == 0 || n == -1) {
                bytes += n.toByte()
            } else if (n > 0) {
                while (n != 0) {
                    val b = n.and(0xFF).toByte()

                    bytes += b

                    n = n.shr(Byte.SIZE_BITS)
                }
            } else {
                while (n != -1) {
                    val b = n.and(0xFF).toByte()

                    bytes += b

                    n = n.shr(Byte.SIZE_BITS)
                }
            }

            val padding = if (n < 0) { 0xFF.toByte() } else { 0x00.toByte() }
            var paddings = byteArrayOf()
            repeat(Int.SIZE_BYTES - bytes.count()) {
                paddings += padding
            }

            return if (isBigEndian) {
                paddings + bytes.reversedArray()
            } else {
                paddings + bytes
            }
        }

    fun connect(theBlueDevice: BlueDevice, servicesFilter: String, charToConnect: String):Observable<BlueChar>{
        lateinit var deviceGatt : BluetoothGatt

        var obs: Observable<BlueChar> = Observable.create{
            emitter->
                var bleConnectCallback = object: BluetoothGattCallback(){
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt?,
                        status: Int,
                        newState: Int
                    ) {
                        Log.d("BleServiceDeviceWrapper","onConnectionStateChange: ${newState}")
                        super.onConnectionStateChange(gatt, status, newState)
                        if (newState == STATE_CONNECTED )
                        {
                            theBlueDevice.gatt = gatt
                            gatt?.discoverServices()
                        }
                        else if (newState == STATE_DISCONNECTED)
                        {
                            emitter.onComplete()
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                        super.onServicesDiscovered(gatt, status)

                        if (status == GATT_SUCCESS){
                            gatt?.services?.forEach {
                                if (it.uuid.toString().toUpperCase() == servicesFilter.toUpperCase()) {
                                    Log.d("BleServiceDeviceWrapper","onServicesDiscovered: ${it.uuid}")
                                    var blueService = BlueService(it.uuid.toString(), it)
                                    connectToChar(it, charToConnect)
                                }
                            }
                        }
                    }

                    override fun onCharacteristicChanged(
                        gatt: BluetoothGatt?,
                        characteristic: BluetoothGattCharacteristic?
                    ) {
                        super.onCharacteristicChanged(gatt, characteristic)
                        var name = characteristic?.uuid.toString().toUpperCase()
                        var value = characteristic?.value
                        if (name != null && value != null) {
                            var char = BlueChar(name = name,  value=value)
                            emitter.onNext(char)
                        }
                    }

                    private fun connectToChar(gatt: BluetoothGattService, charFilter: String){
                        gatt.characteristics.forEach{
                            Log.d("BleServiceDeviceWrapper","connectToChar: ${it.uuid}")
                            var name = it.uuid.toString().toUpperCase()
                            if(name == charFilter.toUpperCase()){


                                deviceGatt.setCharacteristicNotification(it, true)
                                val descriptor: BluetoothGattDescriptor =
                                    it.getDescriptor(
                                        java.util.UUID.fromString(
                                            "00002902-0000-1000-8000-00805f9b34fb"
                                        )
                                    )
                                descriptor.setValue(ENABLE_NOTIFICATION_VALUE)
                                deviceGatt.writeDescriptor(descriptor)
                            }
                        }
                    }
                }
            deviceGatt = theBlueDevice.device.connectGatt(context, false, bleConnectCallback )
        }

        return obs.doFinally {
            deviceGatt.close()
        }
    }

}