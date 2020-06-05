package nl.teamwildenberg.solometrics.Ble

import android.bluetooth.BluetoothDevice
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.internal.operators.flowable.FlowableReplay.observeOn
import java.util.concurrent.TimeUnit

class BleServiceDummy: IBleService {
    override suspend fun GetDeviceList(
        type: DeviceTypeEnum,
        timeOutObservable: Observable<Int>
    ): Observable<BlueDevice> {

        var result = Observable
            .interval(0,100, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .map{
                BlueDevice("Ultrasonic","#######", type, false, null)
            }
        result.publish()

        return result
            .takeUntil(timeOutObservable)

    }

    override suspend fun Connect(theBlueDevice: BlueDevice): Observable<BlueChar> {
        var obs  = Observable
            .interval(0,100, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .map{
                BlueChar("wind thingy", byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 6.toByte(),120.toByte(), 49.toByte(), 82.toByte(), 46.toByte(), 0.toByte()))
            }
        obs.publish()
        return obs
    }

    override fun setChar(theDevice: BlueDevice, value: Int) {

    }

    override fun setChar(theDevice: BlueDevice, value: ByteArray) {

    }

    override fun setChar(theDevice: BlueDevice, charUuid: String, isOn: Boolean) {

    }

    override fun setChar(theDevice: BlueDevice, charUuid: String, value: Int) {

    }
}