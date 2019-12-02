package com.paramsen.noise.sample.view

import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.*
import android.support.annotation.AnyThread
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.menu.ActionMenuItemView
import android.support.v7.widget.ActionMenuView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import com.paramsen.noise.Noise
import com.paramsen.noise.sample.FileManager
import com.paramsen.noise.sample.R
import com.paramsen.noise.sample.source.AudioSource
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import net.mabboud.android_tone_player.ContinuousBuzzer
import java.io.File
import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    private val TAG = javaClass.simpleName!!

    private val disposable: CompositeDisposable = CompositeDisposable()

    private val p0 = Profiler("p0")
    private val p1 = Profiler("p1")
    val p2 = Profiler("p2")
    private val p3 = Profiler("p3")
    private var tonePlayer: ContinuousBuzzer? = null

    // set to positive number when taking sample,
    // and decremented by 1 each time sample is added to fftSamples.
    private val sampleCount = 30
    private var currentSampleCount = AtomicInteger(0)
    private val recordType = AtomicReference<RecordType>(RecordType.EMPTY)

    private val fileManager = FileManager(this)

    enum class RecordType(val filename: String) { NOISE("noise"), SIGNAL("signal"), EMPTY("empty") }

    @AnyThread
    fun showToast(msg: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } else {
            Handler(Looper.getMainLooper()).post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scheduleAbout()
        tonePlayer = ContinuousBuzzer()
        tonePlayer?.toneFreqInHz = 10000.0
        tonePlayer?.pausePeriodSeconds = 5.0

        record_noise.setOnClickListener {
            if (currentSampleCount.get() > 0) {
                showToast("Sample already taking .. ${currentSampleCount.get()}")
                return@setOnClickListener
            }
            if (recordType.get() != RecordType.EMPTY) {
                showToast("Record type is not empty .. ${recordType.get()}")
                return@setOnClickListener
            }
            recordType.set(RecordType.NOISE)
            currentSampleCount.set(sampleCount)
            showToast("Start sampling noise!")
        }

        record_signal.setOnClickListener {
            if (currentSampleCount.get() > 0) {
                showToast("Sample already taking .. ${currentSampleCount.get()}")
                return@setOnClickListener
            }
            if (recordType.get() != RecordType.EMPTY) {
                showToast("Record type is not empty .. ${recordType.get()}")
                return@setOnClickListener
            }
            recordType.set(RecordType.SIGNAL)

            //tonePlayer?.play()
            playsound(1500.0,44100)

            // wait for sound to play
            AsyncTask.execute {
                Thread.sleep(1000)
                //playsound(1500.0,4410)
                currentSampleCount.set(sampleCount)
            }
            showToast("Start sampling signal!")
        }

        share_noise.setOnClickListener {
            fileManager.shareFile(RecordType.NOISE)
        }

        share_signal.setOnClickListener {
            fileManager.shareFile(RecordType.SIGNAL)
        }
    }

    override fun onResume() {
        super.onResume()
        if (requestAudio() && disposable.size() == 0) {
            start()
        }
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
//                .doOnNext { p0.next() }
                .subscribe(audioView::onWindow) { e -> Log.e(TAG, e.message) })
        //FFTView
        disposable.add(src.observeOn(Schedulers.newThread())
//                .doOnNext { p1.next() }
                .map {
                    for (i in 0 until it.size) {
                        it[i] *= 2.0f
                    }
                    return@map it
                }
                .onBackpressureDrop()
                .map { noise.fft(it, FloatArray(4096 + 2)) }
//                .doOnNext { Log.d("MainActivity", it[1024].toString()) }
//                .doOnNext { p3.next() }
                .subscribe({ fft ->
                    fftHeatMapView.onFFT(fft)
                    fftBandView.onFFT(fft)
                    if (currentSampleCount.get() > 0) {
                        currentSampleCount.set(currentSampleCount.get() - 1)
                        fileManager.writeDataToFile(recordType.get(), fft)
                        if (currentSampleCount.get() == 0) {
                            runOnUiThread { Toast.makeText(this, "sampling finished", Toast.LENGTH_SHORT).show() }
                            recordType.set(RecordType.EMPTY)
                        }
                    }
                }, { e -> Log.e(TAG, e.message) }))

//        tip.schedule()
    }

    private fun playsound(frequency: Double, duration: Int){

        // AudioTrack definition
        val mBufferSize = AudioTrack.getMinBufferSize(44100,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_8BIT)
        val mAudioTrack = AudioTrack(AudioManager.STREAM_MUSIC, 44100,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                mBufferSize, AudioTrack.MODE_STREAM)

        var mSound = DoubleArray(4410)
        var mBuffer = ShortArray(duration)
        for(i in mSound.indices){
            mSound[i] = Math.sin((2.0*Math.PI * i/(44100/frequency)))
            mBuffer[i] = (mSound[i]*Short.MAX_VALUE).toShort()
        }

        mAudioTrack.setStereoVolume(AudioTrack.getMaxVolume(), AudioTrack.getMaxVolume())
        mAudioTrack.play()

        mAudioTrack.write(mBuffer, 0, 4410)
        mAudioTrack.stop()
        mAudioTrack.release()

    }
    /**
     * Dispose microphone subscriptions
     */
    private fun stop() {
        disposable.clear()
        tonePlayer?.stop()
        fileManager.close()
    }

    /**
     * Output windows of 4096 len, ~10/sec for 44.1khz, accumulates for FFT
     */
    private fun accumulate(o: Flowable<FloatArray>): Flowable<FloatArray> {
        val size = 4096

        return o.map(object : Function<FloatArray, FloatArray> {
            val buf = FloatArray(size * 2)
            val empty = FloatArray(0)
            var c = 0

            override fun apply(window: FloatArray): FloatArray {
                System.arraycopy(window, 0, buf, c, window.size)
                c += window.size

                if (c >= size) {
                    val out = FloatArray(size)
                    System.arraycopy(buf, 0, out, 0, size)

                    if (c > size) {
                        System.arraycopy(buf, c % size, buf, 0, c % size)
                    }

                    c = 0

                    return out
                }

                return empty
            }
        }).filter { fft -> fft.size == size } //filter only the emissions of complete 4096 windows
    }

    private fun accumulate1(o: Flowable<FloatArray>): Flowable<FloatArray> {
        return o.window(6).flatMapSingle { it.collect({ ArrayList<FloatArray>() }, { a, b -> a.add(b) }) }.map { window ->
            val out = FloatArray(4096)
            var c = 0
            for (each in window) {
                if (c + each.size >= 4096)
                    break

                System.arraycopy(each, 0, out, c, each.size)
                c += each.size - 1
            }
            out
        }
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
        tonePlayer?.play()
        return true
    }

    private fun scheduleAbout() {
        container.postDelayed({
            if (!info.showed) {
                try {
                    val anim = AnimationUtils.loadAnimation(this, R.anim.nudge).apply {
                        repeatCount = 3
                        repeatMode = Animation.REVERSE
                        duration = 200
                        interpolator = AccelerateDecelerateInterpolator()
                        onTerminate { scheduleAbout() }
                    }

                    (((((container.parent.parent as ViewGroup).getChildAt(1) as ViewGroup) //container
                            .getChildAt(0) as ViewGroup) //actionbar
                            .getChildAt(1) as ActionMenuView)
                            .getChildAt(0) as ActionMenuItemView)
                            .startAnimation(anim)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not animate nudge / ${Log.getStackTraceString(e)}")
                }
            }
        }, 3000)
    }

}
