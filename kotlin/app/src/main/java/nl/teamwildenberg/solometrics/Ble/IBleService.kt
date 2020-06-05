package nl.teamwildenberg.solometrics.Ble

import io.reactivex.Observable

interface IBleService {
    suspend fun GetDeviceList(type: DeviceTypeEnum, timeOutObservable: Observable<Int>): Observable<BlueDevice>

    suspend fun Connect(theBlueDevice: BlueDevice): Observable<BlueChar>
    fun setChar(theDevice: BlueDevice, value: Int)
    fun setChar(theDevice: BlueDevice, value: ByteArray)
    fun setChar(theDevice: BlueDevice, charUuid: String, isOn : Boolean)
    fun setChar(theDevice: BlueDevice, charUuid: String, value : Int)
}