package nl.teamwildenberg.solometrics.Service

import android.Manifest
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject


class GpsService: Service() {
    val myBinder = LocalBinder()
    private var locationManager : LocationManager? = null

    override fun onCreate() {
        super.onCreate()

        val bindServiceIntent = Intent(this, StorageService::class.java)
        this.bindService(bindServiceIntent, storageServiceConnection, Context.BIND_NOT_FOREGROUND)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent != null) {
            // hydrate UltraSonic bluetooth device
            Log.d("StorageService", "starting command - determine if start")
            val actionString = intent.getStringExtra("action")
            if (actionString == "start") {
                startGps()
            }
            if (actionString == "stop") {
                stopGps()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }


    override fun onBind(intent: Intent?): IBinder? {
        return myBinder
    }

    private fun startGps()
    {
        myBinder.gpsStatusChannel.onNext(DeviceStatusEnum.Connecting)
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (    (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            && (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        ) {
            stopGps()
        }
        else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,1.0f, locationListener)
            myBinder.gpsStatusChannel.onNext(DeviceStatusEnum.Connected)
        }
    }

    private fun stopGps(){
        locationManager = null;
        myBinder.gpsStatusChannel.onNext(DeviceStatusEnum.Disconnected)

    }

    inner class LocalBinder(
        public val gpsStatusChannel: Subject<DeviceStatusEnum> = PublishSubject.create<DeviceStatusEnum>(),
        val gpsMeasureMentChannel: Subject<GpsMeasurement> = PublishSubject.create<GpsMeasurement>(),
        public var gpsStatus: DeviceStatusEnum = DeviceStatusEnum.Disconnected
    ) : Binder() {
        init {
            gpsStatusChannel.subscribe {
                gpsStatus = it
            }
        }

        fun getService(): GpsService {
            return this@GpsService
        }
    }

    //define the listener
    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            myBinder.gpsMeasureMentChannel.onNext(GpsMeasurement(location.time / 1000, location.longitude, location.longitude))
        }
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            Log.d("GpsService", "locationListener.onStatusChanged - status ${status}")
        }
        override fun onProviderEnabled(provider: String) {
            myBinder.gpsStatusChannel.onNext(DeviceStatusEnum.Connected)
        }
        override fun onProviderDisabled(provider: String) {
            myBinder.gpsStatusChannel.onNext(DeviceStatusEnum.Connecting)
        }
    }

    private val storageServiceConnection = object : ServiceConnection {
        private var storageBinding: StorageService.LocalBinder? = null

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            storageBinding = service as StorageService.LocalBinder
            var storageService = storageBinding!!.getService()
            storageService.bindGpsMeasurementObserver(myBinder.gpsMeasureMentChannel)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            var storageService = storageBinding!!.getService()
//            storageService.unbindMeasurementObserver()
            storageBinding = null;
        }

    }
}