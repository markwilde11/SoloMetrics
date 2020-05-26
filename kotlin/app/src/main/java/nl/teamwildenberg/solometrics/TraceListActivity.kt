package nl.teamwildenberg.solometrics

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ListView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.device_list_activity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import nl.teamwildenberg.solometrics.Adapter.DeviceListAdapter
import nl.teamwildenberg.solometrics.Adapter.TraceListAdapter
import nl.teamwildenberg.solometrics.Ble.BleService
import nl.teamwildenberg.solometrics.Ble.BlueDevice
import nl.teamwildenberg.solometrics.Ble.DeviceTypeEnum
import nl.teamwildenberg.solometrics.Extensions.toStringKey
import nl.teamwildenberg.solometrics.Service.PaperTrace
import nl.teamwildenberg.solometrics.Service.ScreenDuinoService
import nl.teamwildenberg.solometrics.Service.StorageService
import java.util.concurrent.TimeUnit


class TraceListActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private var storageBinding: StorageService.LocalBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        lateinit var traceListView: ListView
        lateinit var traceAdapter: TraceListAdapter
        var traceList: MutableList<PaperTrace> = mutableListOf()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.device_list_activity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        traceAdapter = TraceListAdapter(this, traceList)
        traceListView = findViewById<ListView>(R.id.traceListView)
        traceListView.adapter = traceAdapter
        traceListView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            // This is your listview's selected item
//            var selectedItem = parent.getItemAtPosition(position) as PaperTrace
//            var finishIntent = Intent(this, TraceListActivity::class.java)
//            finishIntent.putExtra("traceKey", selectedItem.key.toStringKey())
//            setResult(Activity.RESULT_OK, finishIntent)
//            this.finish();
        }

        var progbar = findViewById<ProgressBar>(R.id.toolbarprogress)
        progbar.setProgress(0)

        var startButton = findViewById<FloatingActionButton>(R.id.startButton)
        startButton.setOnClickListener{
        }

        var storageServiceIntent = Intent(this, StorageService::class.java)
        startService(storageServiceIntent)
    }

    override fun onResume() {
        super.onResume()

        val bindStorageServiceIntent = Intent(this, StorageService::class.java)
        this.bindService(bindStorageServiceIntent, storageServiceConnection, Context.BIND_NOT_FOREGROUND)
    }

    override fun onPause() {
        super.onPause()
        this.unbindService(storageServiceConnection)
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
        var finishIntent = Intent(this, TraceListActivity::class.java)
        setResult(Activity.RESULT_CANCELED, finishIntent)
        this.finish();
    }

    private val storageServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            storageBinding = service as StorageService.LocalBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            storageBinding = null
        }
    }
}