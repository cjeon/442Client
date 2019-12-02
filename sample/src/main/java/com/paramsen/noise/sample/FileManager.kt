package com.paramsen.noise.sample

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.StrictMode
import android.support.annotation.AnyThread
import android.support.annotation.MainThread
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import com.paramsen.noise.sample.view.MainActivity.RecordType
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.*


class FileManager(private val context: Context) {
    var msgView: TextView? = null
    private val disposable = CompositeDisposable()
    private val processors = hashMapOf<String, BehaviorProcessor<FloatArray>>()

    init {
        val builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())
    }

    fun writeDataToFile(recordType: RecordType, data: FloatArray) {
        val processor: BehaviorProcessor<FloatArray>
        if (processors.containsKey(recordType.filename)) {
            processor = processors[recordType.filename]!!
        } else {
            processor = BehaviorProcessor.create()
            processors[recordType.filename] = processor
            subscribeProcessor(recordType, processor)
        }
        processor.onNext(data)
    }

    fun writeLineBreak(recordType: RecordType) {
        val processor: BehaviorProcessor<FloatArray>
        if (processors.containsKey(recordType.filename)) {
            processor = processors[recordType.filename]!!
        } else {
            processor = BehaviorProcessor.create()
            processors[recordType.filename] = processor
            subscribeProcessor(recordType, processor)
        }
        processor.onNext(FloatArray(0))

    }

    private var isFirstData = true

    private fun subscribeProcessor(recordType: RecordType,
                                   processor: BehaviorProcessor<FloatArray>) {
        processor
                .observeOn(Schedulers.io())
                .onBackpressureBuffer()
                .doOnError { Log.e("FileManager", "subscribeProcessor", it)}
                .subscribe {
                    val file = getTextFile(recordType)
                    if (it.isEmpty()) {
                        file.appendText("]\n")
                        Handler(context.mainLooper).post {
                            Toast.makeText(context, "text file prepared!", Toast.LENGTH_LONG).show()
                            msgView?.text = "text file prepared!"
                            isFirstData = true
                        }

                    } else {
                        if (isFirstData) {
                            file.appendText("[")
                        } else {
                            file.appendText(",")
                        }
                        val str = Arrays.toString(it)
                        file.appendText(str.substring(1, str.length - 1))
                        isFirstData = false
                    }
                }
                .let { disposable.add(it) }
    }

    @AnyThread
    private fun getTextFile(recordType: RecordType): File {
        val rootDir = context.getExternalFilesDir(null)
        val file = File(rootDir, "${recordType.filename}.txt")
        file.createNewFile() // file created only when file does not exist.
        return file
    }

    @MainThread
    fun shareFile(recordType: RecordType) {
        val file = getTextFile(recordType)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/*"
        intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + file.absolutePath))
        context.startActivity(Intent.createChooser(intent, "share file with"))
    }

    fun close() {
        disposable.dispose()
        getTextFile(RecordType.SIGNAL).delete()
        getTextFile(RecordType.NOISE).delete()
    }
}

