package nl.teamwildenberg.solometrics.Service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.util.Log
import io.paperdb.Paper
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import nl.teamwildenberg.solometrics.Extensions.toStringKey
import nl.teamwildenberg.solometrics.Extensions.toPaper
import java.lang.Exception
import java.time.Instant

class GpsService: Service() {
    val myBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()

        val bindServiceIntent = Intent(this, StorageService::class.java)
        this.bindService(bindServiceIntent, storageServiceConnection, Context.BIND_NOT_FOREGROUND)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }


    override fun onBind(intent: Intent?): IBinder? {
        return myBinder
    }


    inner class LocalBinder(
        public val gpsStatusChannel: Subject<DeviceStatusEnum> = PublishSubject.create<DeviceStatusEnum>(),
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

    private val storageServiceConnection = object : ServiceConnection {
        private var storageBinding: StorageService.LocalBinder? = null

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            storageBinding = service as StorageService.LocalBinder
            var storageService = storageBinding!!.getService()
//            storageService.bindMeasurementObserver(localBinder.windMeasurementChannel)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            var storageService = storageBinding!!.getService()
//            storageService.unbindMeasurementObserver()
            storageBinding = null;
        }

    }
}