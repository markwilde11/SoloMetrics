package nl.teamwildenberg.SoloMetrics.Ble

import android.content.Context
import java.lang.Exception
import io.reactivex.Observable

public class BleService (private val context: Context){
    private val adapterWrapper :BleServiceAdapterWrapper = BleServiceAdapterWrapper(context)

    public suspend fun GetDeviceList( type: DeviceTypeEnum, timeOutObservable:  Observable<Int>):Observable<BlueDevice>
    {
        var profile = GetProfile(type);
        var result = adapterWrapper.startScan(profile.name, type, profile.servicesFilter)
        return result
            .takeUntil(timeOutObservable);
    }

    public suspend fun Connect(theBlueDevice: BlueDevice):Observable<BlueChar>{
        val deviceWrapper = BleServiceDeviceWrapper(context)
        var profile = GetProfile(theBlueDevice.type)
        var result = deviceWrapper.connect(theBlueDevice, profile.servicesFilter, profile.charToConnect)
        return result.share()
    }

    public fun setChar(theDevice: BlueDevice, value:Int){
        val deviceWrapper = BleServiceDeviceWrapper(context)
        var profile = GetProfile(theDevice.type)
        deviceWrapper.setCharValue(theDevice, profile.servicesFilter, profile.charToConnect, value.toShort())
    }

    public fun setChar(theDevice: BlueDevice, value:ByteArray){
        val deviceWrapper = BleServiceDeviceWrapper(context)
        var profile = GetProfile(theDevice.type)
        deviceWrapper.setCharValue(theDevice, profile.servicesFilter, profile.charToConnect, value)
    }

    public fun setChar(theDevice: BlueDevice, charUuid: String, isOn : Boolean){
        val deviceWrapper = BleServiceDeviceWrapper(context)
        var profile = GetProfile(theDevice.type)
        var value = 0.toByte()
        if (isOn)
            value = 1.toByte()
        deviceWrapper.setCharValue(theDevice, profile.servicesFilter, charUuid, value)
    }
    public fun setChar(theDevice: BlueDevice, charUuid: String, value : Int){
        val deviceWrapper = BleServiceDeviceWrapper(context)
        var profile = GetProfile(theDevice.type)
        deviceWrapper.setCharValue(theDevice, profile.servicesFilter, charUuid, value.toShort())
    }
    private fun GetProfile(type: DeviceTypeEnum): BlueDeviceProfile {
        when(type){
            DeviceTypeEnum.Ultrasonic ->{
                return BlueDeviceProfile( "ULTRASONIC", "0000180D-0000-1000-8000-00805F9B34FB", "00002a39-0000-1000-8000-00805f9b34fb", true )
            }

            DeviceTypeEnum.SoloScreenDuino -> {
                return BlueDeviceProfile("SoloScreenDuino", "7A2C5500-C492-4B71-BA1B-000000000001", "7A2C5500-C492-4B71-BA1B-000000000002", true)
            }

            DeviceTypeEnum.Empty ->{
                return BlueDeviceProfile("","", "", false)
            }
        }
    }
}