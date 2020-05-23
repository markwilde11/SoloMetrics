package nl.teamwildenberg.solometrics

import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import io.paperdb.Paper
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.toObservable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.Subject
import nl.teamwildenberg.SoloMetrics.Extensions.toStringKey
import nl.teamwildenberg.SoloMetrics.Service.PaperTrace
import nl.teamwildenberg.SoloMetrics.Service.StorageService
import nl.teamwildenberg.SoloMetrics.Service.WindMeasurement
import nl.teamwildenberg.solometrics.Service.PaperMeasurement
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.concurrent.TimeoutException

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class StorageServiceTest {
    @get:Rule
    val serviceRule = ServiceTestRule()
    private lateinit var service: StorageService

    @Before
    fun initializeService(){
        val serviceIntent = Intent(
            ApplicationProvider.getApplicationContext<Context>(),
            StorageService::class.java
        ).apply {
            // Data can be passed to the service via the Intent.
            putExtra("SEED_KEY", 42L)
        }

        // Bind the service and grab a reference to the binder.
        val binder: IBinder = serviceRule.bindService(serviceIntent)

        // Get the reference to the service, or you can call
        // public methods on the binder directly.
        service = (binder as StorageService.LocalBinder).getService()

        Paper.book().destroy()
        Paper.book("0001").destroy()
        Paper.book("0002").destroy()
    }

    @Test
    fun StorageServiceTest_traceStart() {
        @Test
        fun existingTraceTest_Success() {
            // ARRANGE

            // ACT
            var trace = service.StartNewTrace()
            // ASSERT
            assertThat(trace.key, `is`(2))
            assertThat(trace.epoch, `is`(Instant.now().epochSecond))
        }
    }
    @Test
    fun StorageServiceTest_traceStart_onExisting() {
        // ARRANGE
        var key:Int = 0;

        Paper.book().write((key++).toString(), PaperTrace(key, Instant.now().epochSecond))
        // ACT
        var trace = service.StartNewTrace()
        // ASSERT
        assertThat(trace.key, `is`(2))
    }

    @Test
    fun addPartitionToNewTrace(){
        var trace = service.StartNewTrace()
        var measurementList = generateMeasurementList(10)
        var partitionKey = service.AddMeasurements(trace, measurementList)
        assertThat(partitionKey, `is`(1.toStringKey()))
    }

    @Test
    fun addPartitionToExistingTrace(){
        // ARRANGE
        var trace = service.StartNewTrace()
        var measurementList = generateMeasurementList(10)
        service.AddMeasurements(trace, measurementList)

        // ACT
        var partitionKey = service.AddMeasurements(trace, measurementList)

        // ASSERT
        assertThat(partitionKey, `is`(2.toStringKey()))
        var partitionKeyList = Paper.book(trace.key.toStringKey()).allKeys
        assertThat(partitionKeyList.size, `is`(2))
        var measurementListResult = Paper.book(trace.key.toStringKey()).read<MutableList<PaperMeasurement>>(partitionKey)
        assertThat(measurementListResult.size, `is`(measurementList.size))
    }

    @Test
    fun startMeasurementObservable(){
        // ARRANGE
        RxJavaPlugins.setIoSchedulerHandler{ Schedulers.trampoline()}

        var trace = service.StartNewTrace()
        lateinit var partitionKeyList: List<String>
        val measurementList = generateWindMeasurementList(120)
        var measurementObservable = measurementList
            .toObservable()
            .observeOn(Schedulers.io())
            .doFinally(){
                partitionKeyList = Paper.book(trace.key.toStringKey()).allKeys
            }
        // ACT
        service.AddListener(trace, measurementObservable)

        // ASSERT
        assertThat(partitionKeyList.size, `is`(2))
    }

    private fun generateWindMeasurementList(arraySize:Int): MutableList<WindMeasurement>{
        var partitionKey : Int = 0;
        var counter: Int = 0;

        var msmnList : MutableList<WindMeasurement> = mutableListOf()
        for(i in 0..arraySize-1 ){
            val msmnt = WindMeasurement(100 * partitionKey, 100+partitionKey, -partitionKey, 0 )
            msmnList.add(msmnt)
            counter ++;
        }
        return msmnList
    }
    private fun generateMeasurementList(arraySize:Int): MutableList<PaperMeasurement>{
        var partitionKey : Int = 0;
        var counter: Int = 0;

        var msmnList : MutableList<PaperMeasurement> = mutableListOf()
        for(i in 0..arraySize-1 ){
            val msmnt = PaperMeasurement(counter, Instant.now().epochSecond,100 * partitionKey, 100+partitionKey, -partitionKey, 0 )
            msmnList.add(msmnt)
            counter ++;
        }
        return msmnList
    }
}
