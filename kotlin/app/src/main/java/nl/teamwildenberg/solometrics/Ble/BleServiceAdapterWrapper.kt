package nl.teamwildenberg.solometrics.Ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import io.reactivex.Observable
import nl.teamwildenberg.solometrics.MainApplication


class BleServiceAdapterWrapper (){
    private lateinit var context: Context
    private lateinit var mBluetoothManager: BluetoothManager
    private lateinit var adapter: BluetoothAdapter

    init{
        context = MainApplication.applicationContext()
        mBluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = mBluetoothManager.adapter
    }

    fun startScan(name: String, type: DeviceTypeEnum, serviceFilterString: String): Observable<BlueDevice>{
        lateinit var bleScannerCallback: ScanCallback
        var obs: Observable<BlueDevice> =  Observable.create {
            // WRAP CALLBACK
            emitter->
                if (adapter.bluetoothLeScanner == null) {
                    emitter.onError(Error("No bluetooth scanner available. Check your bluetooth or flightmodus."));
                } else {
                    bleScannerCallback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult?) {
                            super.onScanResult(callbackType, result)
                            var theDevice = result?.device;
                            if (theDevice != null) {
                                var blueDevice = BlueDevice(theDevice.name, theDevice.address, type, false, theDevice )
                                emitter.onNext(blueDevice)
                            }
                            Log.d("DeviceListActivity","onScanResult: ${result?.device?.address} - ${result?.device?.name}")
                        }

                        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                            super.onBatchScanResults(results)
                            Log.d("DeviceListActivity","onBatchScanResults:${results.toString()}")
                            emitter.onComplete()
                        }

                        override fun onScanFailed(errorCode: Int) {
                            super.onScanFailed(errorCode)
                            Log.d("DeviceListActivity", "onScanFailed: $errorCode")
                            emitter.onError(Error("Scan failed with errorCode: $errorCode"));
                        }
                    }

                 //DO WORK

                    var ssb = ScanSettings.Builder();
                    ssb.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
                    ssb.setReportDelay(0);

    //            if ((Int)Build.VERSION.SdkInt >= 23)
    //            ssb.setCallbackType(ScanCallbackType.AllMatches);
    //
    //            if ((Int)Build.VERSION.SdkInt >= 26)
    //            ssb.SetLegacy(true);

                    var filterList: MutableList<ScanFilter> = mutableListOf()

                    if (serviceFilterString.isNotEmpty()) {
                        var builder = ScanFilter.Builder()
                        var parcelUuid = ParcelUuid.fromString(serviceFilterString);
                        var parcelUuidMask =
                            ParcelUuid.fromString("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF");
                        builder.setServiceUuid(parcelUuid, parcelUuidMask);
                        var filter = builder.build();
                        filterList.add(filter);
                    }
                    this.adapter.bluetoothLeScanner.startScan(filterList, ssb.build(), bleScannerCallback);
                }
        }
        return obs.doFinally{
            if (adapter.bluetoothLeScanner != null) {
                this.adapter.bluetoothLeScanner.stopScan(bleScannerCallback)
            }
        }
    }
}