package nl.teamwildenberg.SoloMetrics

import android.R.attr.*
import android.app.Activity
import android.content.Intent
import android.graphics.Matrix
import android.os.Bundle
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.device_list_activity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import nl.teamwildenberg.SoloMetrics.Adapter.DeviceListAdapter
import nl.teamwildenberg.SoloMetrics.Ble.BleService
import nl.teamwildenberg.SoloMetrics.Ble.BlueDevice
import nl.teamwildenberg.SoloMetrics.Ble.DeviceTypeEnum
import java.util.concurrent.TimeUnit


class DeviceListActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private lateinit var deviceDiscoveryList: ListView
    private lateinit var deviceDiscoveryAdapter: DeviceListAdapter
    private var deviceList: MutableList<BlueDevice> = mutableListOf()
    private val compositeDisposable: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.device_list_activity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        var deviceType = intent.getSerializableExtra("deviceType") as DeviceTypeEnum
        if (deviceType == DeviceTypeEnum.SoloScreenDuino){
            backgroundImage.setImageResource(R.drawable.ic_web_black_24dp)
        }
        else if(deviceType == DeviceTypeEnum.Ultrasonic){
            backgroundImage.setImageResource(R.drawable.ic_nature_black_24dp)
        }

        deviceDiscoveryAdapter = DeviceListAdapter(this, deviceList)
        deviceDiscoveryList = findViewById<ListView>(R.id.discoverListView)
        deviceDiscoveryList.adapter = deviceDiscoveryAdapter
        deviceDiscoveryList.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            // This is your listview's selected item
            var selectedItem = parent.getItemAtPosition(position) as BlueDevice
            var finishIntent = Intent(this, DeviceListActivity::class.java)
            finishIntent.putExtra("deviceId", selectedItem)
            setResult(Activity.RESULT_OK, finishIntent)
            this.finish();
        }

        var progbar = findViewById<ProgressBar>(R.id.toolbarprogress)
        progbar.setProgress(0)

        var startButton = findViewById<FloatingActionButton>(R.id.startButton)
        startButton.setOnClickListener{
            startDiscovery(deviceType)
        }

        startDiscovery(deviceType)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }

    /// KUDOS:
    /// https://stackoverflow.com/a/46499387/553589
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.getItemId()) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        var finishIntent = Intent(this, DeviceListActivity::class.java)
        setResult(Activity.RESULT_CANCELED, finishIntent)
        this.finish();
    }

    private fun startDiscovery(deviceType: DeviceTypeEnum) {
        var startButton = findViewById<FloatingActionButton>(R.id.startButton)

        if (compositeDisposable.size() == 0){
            startButton.setImageResource(android.R.drawable.ic_media_pause);
            //startButton.setBackgroundColor(Color.RED);
            deviceList.clear()
            deviceDiscoveryAdapter.notifyDataSetChanged()

            var progbar = findViewById<ProgressBar>(R.id.toolbarprogress)
            var timerGuardObservable = Observable
                .interval(0, 10, TimeUnit.MILLISECONDS)
                .take(1000)
                .doFinally() {
                    progbar.setProgress(0)
                }
                .doOnNext(
                    {
                        progbar.setProgress((it / 10).toInt(), false);
                    }
                )
                .map { t -> (t + 1).toInt() }
                .filter { t -> t >= 999 }

            launch {
                val bls = BleService(this@DeviceListActivity)
                val obs = bls.GetDeviceList(deviceType, timerGuardObservable)
                compositeDisposable += obs
                    //.observeOn(AndroidSchedulers.mainThread())
                    .doFinally{
                        stopDiscovery()
                    }
                    .subscribe(
                        { device ->
                            //this.adapter.add(device) {
                            println(device.address)
                            if (deviceList.filter { it.type == deviceType }
                                    .count() == 0) {
                                deviceList.add(device)
                                deviceDiscoveryAdapter.notifyDataSetChanged()
                            }
                        }, { e ->
                            println("error thingie")
                            // exception
                        }, {
                            println("deviceobservable completed")
                        }
                    )
            }
            println("number of disp. ${compositeDisposable.size()}")
        }
        else{
            stopDiscovery()
        }


    }

    private fun stopDiscovery(){
        var startButton = findViewById<FloatingActionButton>(R.id.startButton)
        startButton.setImageResource(android.R.drawable.ic_media_play);
//        startButton.setBackgroundColor(Color.BLUE);
        compositeDisposable.clear()

    }
}