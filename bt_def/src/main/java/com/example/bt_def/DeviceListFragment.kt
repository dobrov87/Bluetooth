package com.example.bt_def

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bt_def.databinding.FragmentListBinding
import com.google.android.material.snackbar.Snackbar

/**
 * Фрагмент для отображения списка Bluetooth-устройств:
 *  - включает Bluetooth
 *  - показывает список спаренных устройств
 *  - ищет новые устройства
 *  - обновляет имя найденного устройства, если оно приходит позже
 *  - сохраняет выбранное устройство
 */
class DeviceListFragment : Fragment(), ItemAdapter.Listener {

    private lateinit var binding: FragmentListBinding
    private lateinit var btAdapter: BluetoothAdapter
    private lateinit var enableBtLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var pairedAdapter: ItemAdapter
    private lateinit var discoveryAdapter: ItemAdapter
    private var prefs: SharedPreferences? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences(BluetoothConstants.PREFERENCES, Context.MODE_PRIVATE)

        initBluetooth()
        initRecyclerViews()
        initLaunchers()
        registerReceiver()
        updateBluetoothState()
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().unregisterReceiver(btReceiver)
    }

    // ---------- Инициализация Bluetooth ----------
    private fun initBluetooth() {
        val manager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = manager.adapter
    }

    private fun initRecyclerViews() = with(binding) {
        pairedAdapter = ItemAdapter(this@DeviceListFragment, false)
        discoveryAdapter = ItemAdapter(this@DeviceListFragment, true)

        rcViewPaired.layoutManager = LinearLayoutManager(requireContext())
        rcViewSearch.layoutManager = LinearLayoutManager(requireContext())

        rcViewPaired.adapter = pairedAdapter
        rcViewSearch.adapter = discoveryAdapter
    }

    private fun initLaunchers() {
        enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateBluetoothState()
        }

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    }

    private fun setupListeners() = with(binding) {

        imBluetoothOn.setOnClickListener {
            if (btAdapter.isEnabled) {
                Snackbar.make(root, "Bluetooth уже включён", Snackbar.LENGTH_SHORT).show()
            } else {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }

        imBluetoothSearch.setOnClickListener {
            if (!checkPermissions()) {
                requestPermissions()
                return@setOnClickListener
            }

            if (btAdapter.isEnabled) {
                try {
                    if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                        == PackageManager.PERMISSION_GRANTED) {
                        btAdapter.cancelDiscovery()
                        btAdapter.startDiscovery()
                    }
                } catch (e: SecurityException) {
                    Log.e("BT", "Нет разрешения BLUETOOTH_SCAN", e)
                }

                imBluetoothSearch.visibility = View.GONE
                pbSearch.visibility = View.VISIBLE
            } else {
                Snackbar.make(root, "Включите Bluetooth для поиска", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    private fun updateBluetoothState() {
        if (btAdapter.isEnabled) {
            binding.imBluetoothOn.setColorFilter(Color.GREEN)
            getPairedDevices()
        } else {
            binding.imBluetoothOn.setColorFilter(Color.RED)
            pairedAdapter.submitList(emptyList())
        }
    }

    private fun getPairedDevices() {
        try {
            val list = btAdapter.bondedDevices.map {
                ListItem(it, prefs?.getString(BluetoothConstants.MAC, "") == it.address)
            }
            pairedAdapter.submitList(list)
            binding.tvEmptyPaired.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        } catch (e: SecurityException) {
            Log.e("BT", "Нет разрешений на чтение спаренных устройств")
        }
    }

    // ---------- BroadcastReceiver ----------
    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {

                // Когда найдено новое устройство
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                    device?.let {
                        val current = discoveryAdapter.currentList.toMutableList()
                        if (!current.any { d -> d.device.address == device.address }) {
                            current.add(ListItem(device, false))
                            discoveryAdapter.submitList(current)
                            binding.tvEmptySearch.visibility = if (current.isEmpty()) View.VISIBLE else View.GONE

                            // ⚙️ Запрашиваем имя вручную, если возможно
                            if (ActivityCompat.checkSelfPermission(
                                    requireContext(),
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                try {
                                    Log.d("BT", " fetchUuidsWithSdp:")
                                    device.fetchUuidsWithSdp()
                                    Log.d("BT2", " fetchUuidsWithSdp:")
                                } catch (e: Exception) {
                                    Log.e("BT", "Ошибка fetchUuidsWithSdp: ${e.message}")
                                }
                            }
                        }
                    }
                }

                // Когда имя устройства обновлено (приходит после fetchUuidsWithSdp)
                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                    device?.let {
                        Log.d("BT", "Имя обновлено: ${it.name} (${it.address})")

                        val current = discoveryAdapter.currentList.toMutableList()
                        val index = current.indexOfFirst { d -> d.device.address == it.address }
                        if (index != -1) {
                            current[index] = ListItem(it, false)
                            discoveryAdapter.submitList(current)
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    binding.imBluetoothSearch.visibility = View.VISIBLE
                    binding.pbSearch.visibility = View.GONE
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    getPairedDevices()
                }
            }
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED) // добавлено для обновления имени
        }
        requireActivity().registerReceiver(btReceiver, filter)
    }

    override fun onClick(device: ListItem) {
        prefs?.edit()?.putString(BluetoothConstants.MAC, device.device.address)?.apply()
        Snackbar.make(binding.root, "Выбрано устройство: ${device.device.name ?: device.device.address}", Snackbar.LENGTH_SHORT).show()
    }
}
