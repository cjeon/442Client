package com.paramsen.noise.sample

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import android.support.annotation.AnyThread
import android.support.annotation.MainThread
import android.util.Log
import com.paramsen.noise.sample.view.MainActivity.RecordType
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.*


class FileManager(private val context: Context) {
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

    private fun subscribeProcessor(recordType: RecordType,
                                   processor: BehaviorProcessor<FloatArray>) {
        processor
                .observeOn(Schedulers.io())
                .onBackpressureDrop()
                .doOnError { Log.e("FileManager", "subscribeProcessor", it)}
                .subscribe {
                    val file = getTextFile(recordType)
                    file.appendText(Arrays.toString(it) + "\n")
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

