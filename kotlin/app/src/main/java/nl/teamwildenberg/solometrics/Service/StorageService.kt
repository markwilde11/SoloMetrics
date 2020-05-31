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
    private val myBinder = LocalBinder()
    public val storageStatusChannel: Subject<StorageStatus> = PublishSubject.create<StorageStatus>()
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
    ): Binder(){
        fun getService(): StorageService{
            return this@StorageService
        }
    }

    private fun StartNewTrace() {
        if (activeTrace == null) {
            var traceKeys = Paper.book().allKeys
            var newKey = 0
            if (traceKeys.size > 0) {
                var lastKey = traceKeys.last()
                var lastTrace = Paper.book().read<PaperTrace>(lastKey)
                newKey = lastTrace.key
            }
            activeTrace = PaperTrace(++newKey, Instant.now().epochSecond)
            Paper.book().write(newKey.toStringKey(), activeTrace)
            storageStatusChannel.onNext(StorageStatus(StorageStatusEnum.StartNew, activeTrace!!))
        }
    }

    public fun StopTrace(){
        if (activeTrace != null){
            storageStatusChannel.onNext(StorageStatus(StorageStatusEnum.Stopped, activeTrace!!))
            activeTrace = null
        }

    }

    public fun bindMeasurementObserver(obs: Observable<WindMeasurement>){
        var counter = 0
        measurementDisposable += obs
            .map{msmnt:WindMeasurement -> msmnt.toPaper( ++counter)}
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

        checkTrace(targetTrace)
        val traceKey = targetTrace.key.toStringKey()
        var partitionKeyList = Paper.book(traceKey).allKeys
        if (partitionKeyList.size > 0) {
            var lastPartitionKey = partitionKeyList.last()

            partitionKey = lastPartitionKey.toInt() + 1
        }

        Paper.book(targetTrace.key.toStringKey()).write(partitionKey.toStringKey(), measurementPartition)
        return partitionKey.toStringKey()
    }

    public fun GetTraceList() : List<PaperTrace>{
        var traceList: MutableList<PaperTrace> = mutableListOf()
        var traceListKeys = Paper.book().allKeys
        traceListKeys.forEach {
            var nextTrace = Paper.book().read<PaperTrace>(it)
            traceList.add(nextTrace)
        }
        return traceList
    }

    public fun GetPartionList(trace: PaperTrace): List<List<WindMeasurement>>{
        var partitionList: MutableList<List<WindMeasurement>> = mutableListOf()
        checkTrace(trace)
        var partitionKeys = Paper.book(trace.key.toStringKey()).allKeys
        partitionKeys.forEach {
            var partition = Paper.book(trace.key.toStringKey()).read<MutableList<WindMeasurement>>(it)
            partitionList.add(partition)
        }

        return partitionList
    }

    public fun GetWindMeasurementList(trace: PaperTrace) : List<WindMeasurement>{
        var windMeasurementList: MutableList<WindMeasurement> = mutableListOf()
        checkTrace(trace)
        var partitionKeys = Paper.book(trace.key.toStringKey()).allKeys
        partitionKeys.forEach {
            var partition = Paper.book(trace.key.toStringKey()).read<MutableList<WindMeasurement>>(it)
            windMeasurementList.addAll(partition)
        }

        return windMeasurementList
    }

    public fun DeleteTrace(trace: PaperTrace): DeleteTraceResult{
        var stringKey =trace.key.toStringKey()
        if ( !Paper.book().contains(stringKey)){
            return DeleteTraceResult.NotFound
        }
        else {
            Paper.book(stringKey).destroy()
            Paper.book().delete(stringKey)
            storageStatusChannel.onNext(StorageStatus(StorageStatusEnum.Delete, trace))
            return DeleteTraceResult.Success
        }
    }

    private fun checkTrace(traceToVerify: PaperTrace){
        var trace = Paper.book().read<PaperTrace>(traceToVerify.key.toStringKey())
        if (trace == null){
            throw Exception("Trace '${traceToVerify.key}' does not exist")
        }
    }

    public enum class DeleteTraceResult {
        Success,
        NotFound
    }
}