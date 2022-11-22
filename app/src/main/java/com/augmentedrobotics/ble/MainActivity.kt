package com.augmentedrobotics.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import org.jetbrains.anko.alert
import org.jetbrains.anko.toast
import timber.log.Timber
import java.util.*


@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 2
    private val BLUETOOTH_SCAN_PERMISSION_REQUEST_CODE= 3;
    private val BLUETOOTH_CONNECT_PERMISSION_REQUEST_CODE= 4;
    lateinit var scan_button : Button

    lateinit var scan_results_recycler_view: RecyclerView
    lateinit var bluetoothGatt : BluetoothGatt
    val serviceUuid = UUID.fromString("000000ff-0000-1000-8000-00805f9b34fb") //UUID of the service
    val serviceCharUuid = UUID.fromString("0000ff03-0000-1000-8000-00805f9b34fb") //UUID of the characteristics
    lateinit var controller:ControlActivity

    companion object {
        lateinit var bluetoothGatt_copy: BluetoothGatt
    }

    //check that location services are enabled
    fun checkLocationServicesEnabled(){
       val l_manager : LocationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        //check if GPS is enabled
        if(!l_manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){

            //enable GPS
            AlertDialog.Builder(this)
                .setMessage("This application needs the Location Services enabled.")
                .setPositiveButton(
                    "Enable",
                    object : DialogInterface.OnClickListener{
                        override fun onClick(paramDialogInterface: DialogInterface?, paramInt: Int) {
                            this@MainActivity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    })
                .setNegativeButton("Cancel", null)
                .show()
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(BluetoothAdapter.getDefaultAdapter()==null){this.finish()}
        setContentView(R.layout.activity_main)
        scan_button = findViewById<Button>(R.id.scan_button)
        scan_button.setOnClickListener{
            if (isScanning) { stopBleScan() }    else { startBleScan() } }

        scan_results_recycler_view = findViewById<RecyclerView>(R.id.scan_results_recycler_view)
        setupRecyclerView()
        checkLocationServicesEnabled()

    }

    //The bleScanner property is a val property whose value is set lazily,
    // meaning it’s initialized only when needed.
    // The reason why we didn’t declare it as just bluetoothAdapter.bluetoothLeScanner is because bluetoothAdapter —
    // itself a lazy property — relies on the getSystemService() function,
    // which is only available after onCreate() has already been called for our Activity.
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }
    //our bluetooth adapter and manager

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
    }

    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultAdapter: ScanResultAdapter by lazy {

        ScanResultAdapter(scanResults) { result ->
            // User tapped on a scan result
            if (isScanning) {
                stopBleScan()
            }
            with(result.device) {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                    ||ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    connectGatt(this@MainActivity, false, gattCallback)
                }
            }
        }
    }

    private fun giveIntent(): Intent? {
        return Intent(this, ControlActivity::class.java)
    }

    //we want to stop the BLE scan if it’s ongoing,
    // and we call connectGatt() on the relevant ScanResult’s BluetoothDevice handle, passing in a BluetoothGattCallback object

    //callback function for all BLE related activites
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    //toast("connected")
                    bluetoothGatt = gatt
                    Handler(Looper.getMainLooper()).post {
                        if (ActivityCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                            || ActivityCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.BLUETOOTH
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            bluetoothGatt?.discoverServices()
                        }
                    }
                   // gatt.requestMtu(GATT_MAX_MTU_SIZE)
                    if(isScanning){stopBleScan()
                    isScanning=false}
                    val intent = Intent(this@MainActivity, ControlActivity::class.java)
                    bluetoothGatt_copy=bluetoothGatt
                    startActivity(intent)
                    //toast("connecting...")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close()
                }
            } else {
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                printGattTable()
                // Consider connection setup as complete here
            }
        }
        //
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Timber.w("ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Battery Percentage: ${littleEndianConversion(value)}%")
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                    }
                }
            }
        }
        //on Write
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Wrote to characteristic $uuid | value: ${value.toHexString()}")
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic write failed for $uuid, error: $status")
                    }
                }
            }
        }
    }


    //check if bluetooth+GPS is enabled
    override fun onResume() {
        super.onResume()
        if(BluetoothAdapter.getDefaultAdapter()!=null){
            if (!bluetoothAdapter.isEnabled) {
                promptEnableBluetooth()
            }
        }else{
            this.finish();
        }


    }
    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            resultLauncher.launch(enableBluetoothIntent)
        }
    }




    //https://stackoverflow.com/questions/62671106/onactivityresult-method-is-deprecated-what-is-the-alternative
    var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data
                if (bluetoothAdapter.isEnabled) {
                    toast("Bluetooth/GPS has been enabled")
                }
            } else {
                toast("Bluetooth enabling has been canceled")
            }
        }


    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }


    //check if the characteristics are readable-writable
    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }
    //check location permission - needs to be enabled!!!!
    val isLocationPermissionGranted
        get() = hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)

    val isBLEScanPermissionGranted
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)
        } else {
            hasPermission(android.Manifest.permission.BLUETOOTH)
        }

    fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }
    val isBluetoothConnectPermissionGranted
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(android.Manifest.permission.BLUETOOTH)
        }


    //scan buttin onItemClickListener
    fun startBleScan() {
        if (bleScanner==null){
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted) {
            requestLocationPermission()
        }
        else {
            scanResults.clear()
            scanResultAdapter.notifyDataSetChanged()
            if (!isBLEScanPermissionGranted) {
                requestBLEScanPermission()
                return
            }
            bleScanner.startScan(mutableListOf(scanFilter), scanSettings, scanCallback)
             //optionally add scan filtes!!
            isScanning = true
        }
    }
    //stop scanning
    private fun stopBleScan() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
            &&ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    //scan filter - only find the devices with our UUID
    val scanFilter = ScanFilter.Builder().setServiceUuid(
        ParcelUuid.fromString(serviceUuid.toString())
    ).build()

    //scan settings
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //means app will only be scanning for a brief period of time,
        .build()                                           // typically to find a very specific type of device


    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                scanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with(result.device) {
                    if (!isBluetoothConnectPermissionGranted) {
                        requestBluetoothConnectPermissions();
                        return
                    }
                    Log.i("ScanCallback", "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                }
                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
                scanResultAdapter.notifyDataSetChanged()


                return
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "onScanFailed: code $errorCode")
        }
    }
    private var isScanning = false
        set(value) {
            field = value
            runOnUiThread { scan_button.text = if (value) "Stop Scan" else "Start Scan" }
        }



    private fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }

        ControlActivity.bluetoothGatt?.let { gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
                ||ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                Log.e("RoboHeartBLE", "i don't have the permission to connect anymore")
                return
            }
            gatt.writeCharacteristic(characteristic)
        } ?: error("Not connected to a BLE device!")
    }



    ///PERMISSIONS - HELPER FUNCTIONS
    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }
    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }
        runOnUiThread {
            alert {
                title = "Location permission required"
                message = "Starting from Android M (6.0), the system requires apps to be granted " +
                        "location access in order to scan for BLE devices."
                isCancelable = false
                positiveButton(android.R.string.ok) {
                    requestPermission(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }.show()
        }
    }

    private fun requestBLEScanPermission() {
        if (isBLEScanPermissionGranted) {
            return
        }
        runOnUiThread {
            alert {
                title = "BLE Scan permissions required"
                message = "This app provides functionality with the RoboHeart via BLE" +
                        " it needs the permission to scan for bluetooth devices to connect with the RoboHeart"
                isCancelable = false
                positiveButton(android.R.string.ok) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        requestPermission(
                            android.Manifest.permission.BLUETOOTH_SCAN,
                            BLUETOOTH_SCAN_PERMISSION_REQUEST_CODE
                        )
                    }else{
                        requestPermission(
                            android.Manifest.permission.BLUETOOTH,
                            BLUETOOTH_SCAN_PERMISSION_REQUEST_CODE
                        )
                    }

                }
            }.show()
        }
    }
    private var currentlyRequestingBluetooth: Boolean = false
    private fun requestBluetoothConnectPermissions() {
        if (isBluetoothConnectPermissionGranted||currentlyRequestingBluetooth) {
            return
        }
        currentlyRequestingBluetooth= true
        runOnUiThread {
            alert {
                title = "Permission to connect to known bluetooth devices"
                message = "After scanning and finding! a RoboHeart the app also needs the permission" +
                        "to actually connect to bluetooth devices"
                isCancelable = false
                positiveButton(android.R.string.ok) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        requestPermission(
                            android.Manifest.permission.BLUETOOTH_CONNECT,
                            BLUETOOTH_CONNECT_PERMISSION_REQUEST_CODE
                        )
                    }else{
                        requestPermission(
                            android.Manifest.permission.BLUETOOTH,
                            BLUETOOTH_CONNECT_PERMISSION_REQUEST_CODE
                        )
                    }

                }
            }.show()
        }
    }


    private fun setupRecyclerView() {
        scan_results_recycler_view.apply {
            adapter = scanResultAdapter
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }
        val animator = scan_results_recycler_view.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }



    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                } else {
                    startBleScan()
                }
            }
            BLUETOOTH_SCAN_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestBLEScanPermission()
                } else {
                    startBleScan()
                }
            }
            BLUETOOTH_CONNECT_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestBluetoothConnectPermissions()
                } else {
                    currentlyRequestingBluetooth = false
                }
            }
        }
    }
    fun littleEndianConversion(bytes: ByteArray): Int {
        var result = 0
        for (i in bytes.indices) {
            result = result or (bytes[i].toInt() shl 8 * i)
        }
        return result
    }


}