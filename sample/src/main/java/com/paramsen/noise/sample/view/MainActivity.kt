package com.paramsen.noise.sample.view

import android.Manifest.permission.RECORD_AUDIO
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.annotation.AnyThread
import android.support.annotation.MainThread
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import com.paramsen.noise.Noise
import com.paramsen.noise.sample.FileManager
import com.paramsen.noise.sample.R
import com.paramsen.noise.sample.TonePlayer.ContinuousBuzzer
import com.paramsen.noise.sample.source.AudioSource
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private val TAG = javaClass.simpleName!!

    private val disposable: CompositeDisposable = CompositeDisposable()

    private val isSignalPlaying = AtomicBoolean(false)
    private val isInSignalMode = AtomicBoolean(false)
    private val isInTailMode = AtomicBoolean(false)

    private lateinit var fileManager: FileManager

    private lateinit var tonePlayer: ContinuousBuzzer

    enum class ConfigOptions(var value: Int, val optionString: String) {
        SAMPLE_COUNT(1, "한 번에 녹음하는 횟수"),
        SIGNAL_LENGTH_IN_MS(5000, "Signal 길이(ms)"),
        SIGNAL_MODE_LENGTH_IN_MS(5000, "signal 녹음 길이(ms)"),
        TAIL_MODE_LENGTH_IN_MS(3000, "tail 녹음 길이(ms)"),
        CLEAR_ALL_FILES(0, "텍스트 파일 전부 삭제");

        fun loadValueFromSharedPref(sharedPref: SharedPreferences) {
            this.value = sharedPref.getInt(this.optionString, this.value)
        }
    }

    enum class RecordType(val filename: String) {
        ALL("all"),
        TAIL("tail"),
        EMPTY("empty")
    }

    @SuppressLint("WrongThread")
    @AnyThread
    fun showToast(msg: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            msg_view.text = msg
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                msg_view.text = msg
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fileManager = FileManager(this)

        // init ConfigOptions
        val sharedPref = getSharedPreferences("default", Context.MODE_PRIVATE)
        ConfigOptions.values().forEach { it.loadValueFromSharedPref(sharedPref) }

        tonePlayer = ContinuousBuzzer()
        tonePlayer.toneFreqInHz = 10000.0
        tonePlayer.pausePeriodInMs = ConfigOptions.SIGNAL_LENGTH_IN_MS.value.toDouble()

        record_signal.setOnClickListener {
            if (isInSignalMode.get() || isInTailMode.get()) {
                showToast("Sample already taking ..")
                return@setOnClickListener
            }

            recordSignal(ConfigOptions.SAMPLE_COUNT.value)
        }

        share_signal_all.setOnClickListener {
            fileManager.shareFile(RecordType.ALL)
        }

        share_signal_tail.setOnClickListener {
            fileManager.shareFile(RecordType.TAIL)
        }

        config.setOnClickListener {

            val options: Array<CharSequence> = Array(ConfigOptions.values().size) { "" }
            ConfigOptions.values().map { it.optionString }.forEachIndexed { index, string -> options[index] = string }

            val mainDialog = AlertDialog.Builder(this)
                    .setTitle("설정")
                    .setItems(options, null)
                    .setPositiveButton("끝!") { dialog, _ -> dialog.dismiss() }
                    .create()

            mainDialog.listView.setOnItemClickListener { _, _, position, _ ->
                if (0 > position || position >= ConfigOptions.values().size) {
                    throw RuntimeException()
                }
                val option = ConfigOptions.values().find { it.optionString == options[position] }
                        ?: throw RuntimeException()

                if (option == ConfigOptions.CLEAR_ALL_FILES) {
                    fileManager.deleteAllFiles()
                    showToast("파일 삭제 완료!")
                    return@setOnItemClickListener
                }

                val editText = EditText(this)
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.setText(option.value.toString())

                AlertDialog.Builder(this)
                        .setTitle(option.optionString)
                        .setView(editText)
                        .setNegativeButton("취소") { d, _ -> d.cancel() }
                        .setPositiveButton("설정") { d, _ ->
                            if (editText.text.isBlank()) {
                                showToast("Input is empty, nothing changed.")
                            } else {
                                option.value = editText.text.toString().toInt()
                                sharedPref.edit().putInt(option.optionString, option.value).apply()
                                if (option == ConfigOptions.SIGNAL_LENGTH_IN_MS) {
                                    tonePlayer.pausePeriodInMs = ConfigOptions.SIGNAL_LENGTH_IN_MS.value.toDouble()
                                }
                            }
                            d.dismiss()
                        }
                        .show()
            }

            mainDialog.show()
        }
    }

    /**
     * @param count times to repeat
     */
    @MainThread
    fun recordSignal(count: Int) {
        if (count <= 0) {
            return
        }

//        playFMCW(count)

        tonePlayer.setOnPlayListener {
            isSignalPlaying.set(true)
            isInSignalMode.set(true)
            Single.timer(ConfigOptions.SIGNAL_MODE_LENGTH_IN_MS.value.toLong(), TimeUnit.MILLISECONDS)
                    .subscribe { _ -> isInSignalMode.set(false) }
        }
        tonePlayer.setOnStopListener {
            isSignalPlaying.set(false)
            if (ConfigOptions.TAIL_MODE_LENGTH_IN_MS.value <= 0) {
                showToast("sampling finished")
                recordSignal(count - 1)
                return@setOnStopListener
            }
            isInTailMode.set(true)
            Single.timer(ConfigOptions.TAIL_MODE_LENGTH_IN_MS.value.toLong(), TimeUnit.MILLISECONDS)
                    .subscribe { _ ->
                        // recording finished.
                        isInTailMode.set(false)
                        showToast("sampling finished")
                        recordSignal(count - 1)
                    }
        }
        tonePlayer.play()

        showToast("Start sampling signal! Count: $count")
    }

    private var player = MediaPlayer()

    @MainThread
    fun playFMCW(count: Int) {
        player.setOnCompletionListener {
            recordSignal(count - 1)
        }
        try {
            player.start()
        } catch (e: IOException) {
            Log.e(e.localizedMessage, e.toString())
        }

    }

    override fun onResume() {
        super.onResume()
        if (requestAudio() && disposable.size() == 0) {
            start()
        }
        setupFmcwPlayer()
    }

    fun setupFmcwPlayer() {
        player = MediaPlayer()
        val afd = resources.openRawResourceFd(R.raw.fmcw) // 500-2000
        val fd = afd.fileDescriptor
        player.setDataSource(fd, afd.startOffset, afd.length)
        player.isLooping = false
        player.prepare()
    }

    override fun onStop() {
        stop()
        super.onStop()
    }

    /**
     * Subscribe to microphone
     */
    private fun start() {
        val src = AudioSource().stream()
        val noise = Noise.real().optimized().init(4096, false)
        //AudioView
        disposable.add(src.observeOn(Schedulers.newThread())
                .subscribe(audioView::onWindow) { e -> Log.e(TAG, e.message) })
        //FFTView
        disposable.add(src.observeOn(Schedulers.newThread())
                .map {
                    for (i in 0 until it.size) {
                        it[i] *= 2.0f
                    }
                    return@map it
                }
                .onBackpressureBuffer()
                .map { noise.fft(it, FloatArray(4096 + 2)) }
                .subscribe({ fft ->
//                    fftHeatMapView.onFFT(fft)
//                    fftBandView.onFFT(fft)
                    if (isInSignalMode.get()) {
                        fileManager.writeDataToFile(RecordType.ALL, fft)
                    }
                    if (isInTailMode.get()) {
                        fileManager.writeDataToFile(RecordType.ALL, fft)
                        fileManager.writeDataToFile(RecordType.TAIL, fft)
                    }
                }, { e -> Log.e(TAG, e.message) }))

    }

    /**
     * Dispose microphone subscriptions
     */
    private fun stop() {
        disposable.clear()
        fileManager.close()
        fileManager = FileManager(this)
        player.stop()
        player.release()
    }

    private fun requestAudio(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(this, RECORD_AUDIO) != PERMISSION_GRANTED) {
            requestPermissions(arrayOf(RECORD_AUDIO), 1337)
            return false
        }

        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults[0] == PERMISSION_GRANTED)
            start()
        //playsound(10000.0,200)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
//        info.onShow()
        return true
    }
}
