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
 *  - включает Bluetooth (через системный интент)
 *  - показывает список спаренных устройств
 *  - ищет новые устройства
 *  - сохраняет выбранное устройство в SharedPreferences
 *
 * Работает на Android 6–15.
 */
class DeviceListFragment : Fragment(), ItemAdapter.Listener {

    private lateinit var binding: FragmentListBinding             // ViewBinding для layout
    private lateinit var btAdapter: BluetoothAdapter               // Основной Bluetooth адаптер
    private lateinit var enableBtLauncher: ActivityResultLauncher<Intent> // Для включения Bluetooth
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>> // Для запроса разрешений
    private lateinit var pairedAdapter: ItemAdapter                // Адаптер для спаренных устройств
    private lateinit var discoveryAdapter: ItemAdapter             // Адаптер для найденных устройств
    private var prefs: SharedPreferences? = null                   // Для сохранения MAC выбранного устройства

    // ---------- Создание представления ----------
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    // ---------- После создания view ----------
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Получаем SharedPreferences для хранения MAC выбранного устройства
        prefs = requireContext().getSharedPreferences(BluetoothConstants.PREFERENCES, Context.MODE_PRIVATE)

        // Инициализация основных компонентов
        initBluetooth()
        initRecyclerViews()
        initLaunchers()
        registerReceiver()

        // Проверяем текущее состояние Bluetooth и обновляем UI
        updateBluetoothState()

        // Устанавливаем слушатели кнопок
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Всегда важно снимать регистрацию ресиверов во избежание утечек памяти
        requireActivity().unregisterReceiver(btReceiver)
    }

    // ---------- Инициализация Bluetooth ----------
    private fun initBluetooth() {
        val manager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = manager.adapter
    }

    // ---------- Настройка RecyclerView ----------
    private fun initRecyclerViews() = with(binding) {
        pairedAdapter = ItemAdapter(this@DeviceListFragment, false)
        discoveryAdapter = ItemAdapter(this@DeviceListFragment, true)

        rcViewPaired.layoutManager = LinearLayoutManager(requireContext())
        rcViewSearch.layoutManager = LinearLayoutManager(requireContext())

        rcViewPaired.adapter = pairedAdapter
        rcViewSearch.adapter = discoveryAdapter
    }

    // ---------- Регистрация ActivityResult-обработчиков ----------
    private fun initLaunchers() {
        // 1️⃣ Лаунчер для включения Bluetooth
        enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Вызывается после возвращения из системного диалога включения Bluetooth
            updateBluetoothState()
        }

        // 2️⃣ Лаунчер для запроса разрешений
        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    }

    // ---------- Установка слушателей кнопок ----------
    private fun setupListeners() = with(binding) {

        // 🔘 Кнопка включения Bluetooth
        imBluetoothOn.apply {
            isClickable = true
            isFocusable = true

            setOnClickListener {
                if (btAdapter.isEnabled) {
                    // Уже включен — просто уведомляем
                    Snackbar.make(root, "Bluetooth уже включён", Snackbar.LENGTH_SHORT).show()
                } else {
                    // Запускаем системный диалог включения Bluetooth
                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            }
        }

        // 🔍 Кнопка поиска устройств
        imBluetoothSearch.setOnClickListener {
            if (!checkPermissions()) {
                // Если нет разрешений — запрашиваем
                requestPermissions()
                return@setOnClickListener
            }

            // Если Bluetooth включён — начинаем поиск
            if (btAdapter.isEnabled) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED) {
                    btAdapter.cancelDiscovery()
                }

                btAdapter.startDiscovery()  // запуск нового

                // Меняем отображение кнопки / прогресс-бара
                imBluetoothSearch.visibility = View.GONE
                pbSearch.visibility = View.VISIBLE
            } else {
                Snackbar.make(root, "Включите Bluetooth для поиска", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- Проверка разрешений ----------
    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Начиная с Android 12 нужны дополнительные разрешения
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            // До Android 12 достаточно только доступа к местоположению
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    // ---------- Запрос разрешений ----------
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    // ---------- Обновление состояния Bluetooth ----------
    private fun updateBluetoothState() {
        if (btAdapter.isEnabled) {
            // Если Bluetooth включен — окрашиваем кнопку в зелёный и показываем устройства
            binding.imBluetoothOn.setColorFilter(Color.GREEN)
            getPairedDevices()
        } else {
            // Если выключен — красный цвет и очищаем список
            binding.imBluetoothOn.setColorFilter(Color.RED)
            pairedAdapter.submitList(emptyList())
        }
    }

    // ---------- Получение списка спаренных устройств ----------
    private fun getPairedDevices() {
        try {
            val list = btAdapter.bondedDevices.map {
                // Проверяем, совпадает ли MAC с сохранённым в настройках
                ListItem(it, prefs?.getString(BluetoothConstants.MAC, "") == it.address)
            }
            pairedAdapter.submitList(list)
            binding.tvEmptyPaired.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        } catch (e: SecurityException) {
            Log.e("BT", "Нет разрешений на чтение спаренных устройств")
        }
    }

    // ---------- BroadcastReceiver для Bluetooth-событий ----------
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

                    // Добавляем устройство в список, если его там ещё нет
                    device?.let {
                        val current = discoveryAdapter.currentList.toMutableList()
                        if (!current.any { d -> d.device.address == device.address }) {
                            current.add(ListItem(device, false))
                            discoveryAdapter.submitList(current)
                        }
                        binding.tvEmptySearch.visibility = if (current.isEmpty()) View.VISIBLE else View.GONE
                    }




                }

                // Когда завершён процесс поиска
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    binding.imBluetoothSearch.visibility = View.VISIBLE
                    binding.pbSearch.visibility = View.GONE
                }

                // Когда изменилось состояние спаривания
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    getPairedDevices()
                }
            }
        }
    }

    // ---------- Регистрация ресивера ----------
    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        requireActivity().registerReceiver(btReceiver, filter)
    }

    // ---------- Обработка кликов по элементам списка ----------
    override fun onClick(device: ListItem) {
        prefs?.edit()?.putString(BluetoothConstants.MAC, device.device.address)?.apply()
        Snackbar.make(binding.root, "Выбрано устройство: ${device.device.name}", Snackbar.LENGTH_SHORT).show()
    }
}
