package com.example.bluetoothmoduldef

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.navigation.fragment.findNavController
import com.example.bluetoothmoduldef.databinding.FragmentMainBinding
import com.example.bt_def.BluetoothConstants
import com.example.bt_def.ItemAdapter
import com.example.bt_def.bluetooth.BluetoothController
import kotlin.coroutines.Continuation


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class MainFragment : Fragment(), BluetoothController.Listener {

    private lateinit var binding: FragmentMainBinding
    private lateinit var bluetoothController: BluetoothController
    private lateinit var btAdapter: BluetoothAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initBtAdapter()
        val pref =
            activity?.getSharedPreferences(BluetoothConstants.PREFERENCES, Context.MODE_PRIVATE)
        val mac = pref?.getString(BluetoothConstants.MAC, "")
        bluetoothController = BluetoothController(btAdapter)
        binding.bList.setOnClickListener {
            findNavController().navigate(R.id.action_mainFragment_to_deviceListFragment)
        }




        binding.bConnect.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val permissions = arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
                requestBluetoothPermissions.launch(permissions)
            }



            bluetoothController.connect(mac ?: " ", this)


        }
        binding.bSend.setOnClickListener {
            bluetoothController.sendMessage("A")
        }

    }

    private fun initBtAdapter() {
        val bManager = activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = bManager.adapter


    }

    private val requestBluetoothPermissions =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Callback — сюда вернутся результаты запроса
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                Log.d("MyLog", "Bluetooth разрешения получены")
            } else {
                Log.d("MyLog", "Bluetooth разрешения не даны")
            }
        }

    override fun onReceive(message: String) {
        activity?.runOnUiThread {
            when (message) {
                BluetoothController.BLUETOOTH_CONNECTED -> {
                    binding.bConnect.backgroundTintList =
                        AppCompatResources.getColorStateList(requireContext(), R.color.red)
                    binding.bConnect.text = "Disconnect"

                }

                BluetoothController.BLUETOOTH_NO_CONNECTED -> {

                    binding.bConnect.backgroundTintList =
                        AppCompatResources.getColorStateList(requireContext(), R.color.green)
                    binding.bConnect.text = "Connect"
                }

                else -> {
                    binding.tvStatus2.text = message
                }
            }
        }
    }

}