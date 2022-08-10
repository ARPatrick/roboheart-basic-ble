package com.example.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.zerokol.views.joystickView.JoystickView
import com.zerokol.views.joystickView.JoystickViewHorizontal
import org.jetbrains.anko.toast
import java.util.*


class ControlActivity: AppCompatActivity() {
    val serviceUuid = UUID.fromString("000000ff-0000-1000-8000-00805f9b34fb") //UUID of the service
    val serviceCharUuid = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb") //UUID of the characteristics
    var characteristic: BluetoothGattCharacteristic?= null


    private lateinit var disconnect_button: Button

    // Importing also other views
    lateinit var joystick: JoystickView
    lateinit var joystick2: JoystickViewHorizontal


    //global variables for commands
    private var vertical_direction = 0
    private var horizontal_direction = 0
    private var  fPower_vertical : Double = 0.0
    private var  fPower_horizontal : Double = 0.0

    companion object {
     lateinit var bluetoothGatt: BluetoothGatt
    }



    @SuppressLint("LogNotTimber")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_landscape)

        bluetoothGatt=MainActivity.bluetoothGatt_copy!!


        //disconnect button
        disconnect_button = findViewById(R.id.cancel_button)
        disconnect_button.setOnClickListener{
            bluetoothGatt.disconnect()
            toast("disconnecting...")
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        //Referencing also other views
        joystick =  findViewById(R.id.joystickView)
        joystick2 = findViewById<JoystickViewHorizontal>(R.id.joystickViewHorizontal)


        //joystick for the vertical axis
        joystick.setOnJoystickMoveListener({ angle, power, direction ->

            when (direction) {
                //to the front
                JoystickView.FRONT, JoystickView.FRONT_RIGHT, JoystickView.LEFT_FRONT -> {
                    vertical_direction=1
                }

                //to the back
                JoystickView.BOTTOM, JoystickView.BOTTOM_LEFT, JoystickView.RIGHT_BOTTOM -> {
                    vertical_direction=-1
                }

                else -> {
                    //center
                    vertical_direction=0
                }
            }

            //min-max values for the motor
            val t_min = 60.0
            val t_max = 100.0 //actually 100

            //scaling from range [0,100] to custom range [t_min, t_max]
            fPower_vertical  = ((power.toDouble()/100.00) * (t_max-t_min) )+ t_min

            updateCommand()
        }, JoystickView.DEFAULT_LOOP_INTERVAL)


        //joystick for the horizontal commands
        joystick2.setOnJoystickMoveListener({ angle, power, direction ->


            when (direction) {
                //to the right
                JoystickView.RIGHT, JoystickView.FRONT_RIGHT, JoystickView.RIGHT_BOTTOM -> {
                    horizontal_direction=1
                }

                //to the back
                JoystickView.LEFT, JoystickView.BOTTOM_LEFT, JoystickView.LEFT_FRONT -> {
                    horizontal_direction=-1
                }

                else -> {
                    //center
                    horizontal_direction=0
                }
            }

            val a_min = 60.0
            val a_max = 100.0
            fPower_horizontal  = ((power.toDouble()/100.00) * (a_max-a_min) )+ a_min

            updateCommand()

        }, JoystickView.DEFAULT_LOOP_INTERVAL)


    }

     fun updateCommand(){
        //our characteristic that is to be written
        characteristic=bluetoothGatt.getService(serviceUuid)!!.getCharacteristic(serviceCharUuid)!!

        val pow_y:Byte =hexToByte(java.lang.Integer.toHexString(fPower_vertical.toInt()))
        val pow_x:Byte = hexToByte(java.lang.Integer.toHexString(fPower_horizontal.toInt()))

        if(vertical_direction==0){
            //RIGHT
            if(horizontal_direction==1) {writeCharacteristic(characteristic!! ,byteArrayOf(0x04.toByte(), 0x64.toByte(), pow_x) )}
            //LEFT
            else if(horizontal_direction==-1) writeCharacteristic(characteristic!!, byteArrayOf(0x03.toByte(), 0x64.toByte(), pow_x))
            //CENTER
            else writeCharacteristic(characteristic!!, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00))
        }
        else if(vertical_direction==1){
            //FRONT RIGHT
            if(horizontal_direction==1) {writeCharacteristic(characteristic!! ,byteArrayOf(0x06.toByte(), pow_y, pow_x) )}
            //FRO
            // NT LEFT
            else if(horizontal_direction==-1) writeCharacteristic(characteristic!!, byteArrayOf(0x05.toByte(), pow_y, pow_x))
            //FRONT
            else writeCharacteristic(characteristic!!, byteArrayOf(0x01.toByte(), pow_y, 0x00))
        }
        else{
            //BOTTOM RIGHT
            if(horizontal_direction==1) {writeCharacteristic(characteristic!! ,byteArrayOf(0x08.toByte(), pow_y, pow_x) )}
            //BOTTOM LEFT
            else if(horizontal_direction==-1) writeCharacteristic(characteristic!!, byteArrayOf(0x07.toByte(), pow_y, pow_x))
            //BOTTOM
            else writeCharacteristic(characteristic!!, byteArrayOf(0x02.toByte(), pow_y, 0x00))

        }

    }



    fun hexToByte(hexString: String): Byte {
        val firstDigit = toDigit(hexString[0])
        if(hexString.length>1){ val secondDigit = toDigit(hexString[1])
            return ((firstDigit shl 4) + secondDigit).toByte()}
        else return firstDigit.toByte()

    }

    private fun toDigit(hexChar: Char): Int {
        val digit = Character.digit(hexChar, 16)
        require(digit != -1) { "Invalid Hexadecimal Character: $hexChar" }
        return digit
    }


    private fun readService() {

        val batteryLevelChar = bluetoothGatt!!
            .getService(serviceUuid)?.getCharacteristic(serviceCharUuid)
        if (batteryLevelChar?.isReadable() == true) {
            bluetoothGatt!!.readCharacteristic(batteryLevelChar)
        }
    }

    private fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }

        bluetoothGatt?.let { gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        } ?: error("Not connected to a BLE device!")
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


    /*
    joystick.setOnJoystickMoveListener({ angle, power, direction ->
            angleTextView!!.text = " $angle°"
            powerTextView!!.text = " $power%"

            //min-max values for the motor
            val t_min = 60.0
            val t_max = 100.0 //actually 100

            //scaling from range [0,100] to custom range [t_min, t_max]
            var fPower : Double = ((power.toDouble()/100.00) * (t_max-t_min) )+ t_min
            var pow:Byte =hexToByte(java.lang.Integer.toHexString(fPower.toInt()))

            Log.i("int", fPower.toString()+"---power:"+power.toString())
            Log.i("pow", pow.toString())


            //make the given angle usable for the car application
            var f_angle : Double
            if(angle<0){f_angle=-angle.toDouble()}else f_angle=angle.toDouble()
            if(angle>90){f_angle=180.00-angle}



            val a_min = 60.0
            val a_max = 100.0

            f_angle=(f_angle/90.00)*(a_max-a_min)+a_min
            val ang: Byte
            ang=hexToByte(java.lang.Integer.toHexString(f_angle.toInt()))
            Log.i("a", f_angle.toString()+"IN" + ang.toString())


            when (direction) {
                JoystickView.FRONT -> {
        //            directionTextView.setText(R.string.front_lab)
                    writeCharacteristic(characteristic!!,byteArrayOf(0x01.toByte(),  pow, 0x55.toByte()) )
                }
                JoystickView.FRONT_RIGHT -> {
       //             directionTextView.setText(R.string.front_right_lab)
                    writeCharacteristic(characteristic!!, byteArrayOf(0x06.toByte(),  pow, ang) )
                }
                JoystickView.RIGHT -> {
         //           directionTextView.setText(R.string.right_lab)
                    writeCharacteristic(characteristic!!,byteArrayOf(0x04.toByte(),  pow, ang) )
                }
                JoystickView.RIGHT_BOTTOM -> {
          //         directionTextView.setText(R.string.right_bottom_lab)
                    writeCharacteristic(characteristic!!,byteArrayOf(0x08.toByte(),  pow, ang) )
                }
                JoystickView.BOTTOM -> {
           //         directionTextView.setText(R.string.bottom_lab)
                    writeCharacteristic(characteristic!!,byteArrayOf(0x02.toByte(),  pow, 0x55.toByte()) )
                }
                JoystickView.BOTTOM_LEFT -> {
         //           directionTextView.setText(R.string.bottom_left_lab)
                    writeCharacteristic(characteristic!!,byteArrayOf(0x07.toByte(),  pow, ang) )
                }
                JoystickView.LEFT -> {
           //         directionTextView.setText(R.string.left_lab)
                    writeCharacteristic(characteristic!!,byteArrayOf(0x03.toByte(),  0x50.toByte(), ang) )
                }
                JoystickView.LEFT_FRONT -> {
         //           directionTextView.setText(R.string.left_front_lab)
                    writeCharacteristic(characteristic!!,byteArrayOf(0x05.toByte(),  pow, ang) )
                }
                else -> {
         //           directionTextView.setText(R.string.center_lab)
                    writeCharacteristic(bluetoothGatt.getService(serviceUuid)?.getCharacteristic(serviceCharUuid)!! ,byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte()) )
                }
            }
        }, JoystickView.DEFAULT_LOOP_INTERVAL)


    //temporary functions for testing purposes
    fun forward(view: View){
        toast("forward")
        sendCommand("015555")
    }

    fun  back(view: View){
        sendCommand("025555")
    }


    private fun sendCommand(input: String) {
        if (m_bluetoothSocket != null) {
            try{
                m_bluetoothSocket!!.outputStream.write(input.toByteArray())
            } catch(e: IOException) {
                e.printStackTrace()
            }
        }
    }


    fun disconnect(view: View) {
        if (m_bluetoothSocket != null) {
            try {
                m_bluetoothSocket!!.close()
                m_bluetoothSocket = null
                m_isConnected = false
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        finish()
    }

    private class ConnectToDevice(c: Context) : AsyncTask<Void, Void, String>() {
        private var connectSuccess: Boolean = true
        private val context: Context

        init {
            this.context = c
        }

        override fun onPreExecute() {
            super.onPreExecute()
            m_progress = ProgressDialog.show(context, "Connecting...", "please wait")
        }

        override fun doInBackground(vararg p0: Void?): String? {
            try {
                if (m_bluetoothSocket == null || !m_isConnected) {
                    m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val device: BluetoothDevice = m_bluetoothAdapter.getRemoteDevice(m_address)
                    m_bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(m_myUUID)
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    m_bluetoothSocket!!.connect()
                }
            } catch (e: IOException) {
                connectSuccess = false
                e.printStackTrace()
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (!connectSuccess) {
                Log.i("data", "couldn't connect")
            } else {
                m_isConnected = true
            }
            m_progress.dismiss()
        }
    }*/
}


