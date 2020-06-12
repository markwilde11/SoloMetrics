package nl.teamwildenberg.solometrics.Service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import io.paperdb.Paper
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import nl.teamwildenberg.solometrics.Extensions.toStringKey
import nl.teamwildenberg.solometrics.Extensions.toPaper
import java.lang.Exception
import java.time.Instant

class StorageService: Service() {
    val myBinder = LocalBinder()
    public var activeTrace: PaperTrace? = null;
    private val measurementDisposable: CompositeDisposable = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent != null) {
            // hydrate UltraSonic bluetooth device
            Log.d("StorageService", "starting command - determine if start")
            val actionString = intent.getStringExtra("action")
            if (actionString == "start") {
                StartNewTrace()
            }
            if (actionString == "stop") {
                StopTrace()
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


    inner class LocalBinder(
        public val storageActionChannel: Subject<StorageStatus> = PublishSubject.create<StorageStatus>(),
        public val storageStatusChannel: Subject<DeviceStatusEnum> = PublishSubject.create<DeviceStatusEnum>(),
        public var storageStatus: DeviceStatusEnum = DeviceStatusEnum.Disconnected
    ): Binder(){
        init{
            storageStatusChannel.subscribe{
                storageStatus = it
            }
        }
        fun getService(): StorageService{
            return this@StorageService
        }
    }

    private fun StartNewTrace() {
        if (activeTrace == null) {
            var traceKeys = Paper.book().allKeys
            traceKeys.sort()
            var newKey = 0
            if (traceKeys.size > 0) {
                var lastKey = traceKeys.last()
                var lastTrace = Paper.book().read<PaperTrace>(lastKey)
                newKey = lastTrace.key
            }
            activeTrace = PaperTrace(++newKey, Instant.now().epochSecond)
            Paper.book().write(newKey.toStringKey(), activeTrace)
            myBinder.storageStatusChannel.onNext(DeviceStatusEnum.Connecting)
        }
    }

    public fun StopTrace(){
        var trace = activeTrace
        activeTrace = null
        if (trace != null){
            Paper.book().write(trace.key.toStringKey(), trace)
            myBinder.storageStatusChannel.onNext(DeviceStatusEnum.Disconnected)
            myBinder.storageActionChannel.onNext(StorageStatus(StorageStatusEnum.Add, trace))
        }

    }

    public fun bindMeasurementObserver(obs: Observable<WindMeasurement>){
            obs
                .map{if (activeTrace == null){
                        -1
                    }
                    else{
                        activeTrace!!.key
                    }
                }
                .distinct()
                .filter{it != null}
                .subscribe { msmntList ->
                    myBinder.storageStatusChannel.onNext(DeviceStatusEnum.Connected)
                }

            measurementDisposable += obs
                .filter{activeTrace != null}
                .map { msmnt: WindMeasurement -> msmnt.toPaper(++activeTrace!!.count) }
                .buffer(60)
                .subscribe { msmntList ->
                    activeTrace?.let { AddMeasurements(it, msmntList) }
                }
    }

    public fun unbindMeasurementObserver(){
        measurementDisposable.clear()
    }

    public fun AddMeasurements(targetTrace:PaperTrace, measurementPartition: MutableList<PaperMeasurement>): String{
        var partitionKey: Int = 1

        if (checkTrace(targetTrace)) {
            val traceKey = targetTrace.key.toStringKey()
            var partitionKeyList = Paper.book(traceKey).allKeys
            if (partitionKeyList.size > 0) {
                partitionKeyList.sortBy { it }
                var lastPartitionKey = partitionKeyList.last()
                partitionKey = lastPartitionKey.toInt() + 1
            }
        }
        else
        {
            throw Exception("Trace does not exist ${targetTrace.key}")
        }
        Paper.book(targetTrace.key.toStringKey()).write(partitionKey.toStringKey(), measurementPartition)
        return partitionKey.toStringKey()
    }

    public fun GetTraceList() : List<PaperTrace>{
        var traceList: MutableList<PaperTrace> = mutableListOf()
        var traceListKeys = Paper.book().allKeys
        traceListKeys.sortBy{it}
        traceListKeys.forEach {
            var nextTrace = Paper.book().read<PaperTrace>(it)
            if (nextTrace.key != activeTrace?.key) {
                traceList.add(nextTrace)
            }
        }
        return traceList
    }

    public fun GetPartionList(trace: PaperTrace): List<List<WindMeasurement>>{
        var partitionList: MutableList<List<WindMeasurement>> = mutableListOf()
        if (checkTrace(trace)) {
            var partitionKeys = Paper.book(trace.key.toStringKey()).allKeys
            partitionKeys.sort()
            partitionKeys.forEach {
                var partition =
                    Paper.book(trace.key.toStringKey()).read<MutableList<WindMeasurement>>(it)
                partitionList.add(partition)
            }
        }

        return partitionList
    }

    public fun GetWindMeasurementList(trace: PaperTrace) : List<WindMeasurement>{
        var windMeasurementList: MutableList<WindMeasurement> = mutableListOf()
        if (checkTrace(trace)) {
            var partitionKeys = Paper.book(trace.key.toStringKey()).allKeys
            partitionKeys.sort()
            partitionKeys.forEach {
                var partition =
                    Paper.book(trace.key.toStringKey()).read<MutableList<WindMeasurement>>(it)
                windMeasurementList.addAll(partition)
            }
        }
        return windMeasurementList
    }

    public fun DeleteTrace(trace: PaperTrace): DeleteTraceResult{
        var stringKey =trace.key.toStringKey()
        if ( !Paper.book().contains(stringKey)){
            Paper.book().delete(stringKey)
            return DeleteTraceResult.NotFound
        }
        else {
            Paper.book(stringKey).destroy()
            Paper.book().delete(stringKey)
            myBinder.storageActionChannel.onNext(StorageStatus(StorageStatusEnum.Delete, trace))
            return DeleteTraceResult.Success
        }
    }

    private fun checkTrace(traceToVerify: PaperTrace): Boolean{
        var trace = Paper.book().read<PaperTrace>(traceToVerify.key.toStringKey())
        return trace != null
    }

    public enum class DeleteTraceResult {
        Success,
        NotFound
    }
}