package com.paramsen.noise.sample

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import android.support.annotation.AnyThread
import android.util.Log
import com.paramsen.noise.sample.view.MainActivity
import com.paramsen.noise.sample.view.MainActivity.RecordType
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class FileManager(private val mainActivity: MainActivity) {
    private val disposable = CompositeDisposable()
    private val processors = hashMapOf<String, BehaviorProcessor<FloatArray>>()
    private val sharedPref = mainActivity.getSharedPreferences("default", Context.MODE_PRIVATE)

    init {
        val builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())
    }

    fun deleteAllFiles() {
        val rootDir = mainActivity.getExternalFilesDir(null)
        rootDir.listFiles().forEach { it.deleteRecursively() }
    }

    fun writeDataToFile(recordType: RecordType, data: FloatArray) {
        Log.d("writeDataToFile", "record type = ${recordType.filename}")
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
                .onBackpressureBuffer()
                .subscribe {
                    val file = getTextFile(recordType)
                    file.appendText(Arrays.toString(it) + "\n")
                }
                .let { disposable.add(it) }
    }

    @AnyThread
    private fun getTextFile(recordType: RecordType): File {
        val rootDir = mainActivity.getExternalFilesDir(null)
        val file = File(rootDir, "${sharedPref.getString(MainActivity.ConfigOptions.FILE_NAME.optionString, "")}_${recordType.filename}.txt")
        file.createNewFile() // file created only when file does not exist.
        return file
    }

    @AnyThread
    private fun getZipFile(): File {
        val rootDir = mainActivity.getExternalFilesDir(null)
        val file = File(rootDir, "archive.zip")
        file.createNewFile() // file created only when file does not exist.
        return file
    }

    @AnyThread
    fun shareFile() {
        Single.create<File> {
            zipFile()
            it.onSuccess(getZipFile())
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { file ->
                    if (file.length() == 0L) {
                        return@subscribe
                    }
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "application/zip"
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + file.absolutePath))
                    mainActivity.startActivity(Intent.createChooser(intent, "share file with"))
                }
                .let { disposable.add(it) }
    }

    fun close() {
        disposable.dispose()
    }

    private fun zipFile() {
        mainActivity.showToast("압축시작!")
        val rootDir = mainActivity.getExternalFilesDir(null)
        val files = rootDir.listFiles()

        val buffer = ByteArray(1024)
        val zipFile = getZipFile()
        val fileOut = FileOutputStream(zipFile)
        val zipOut = ZipOutputStream(fileOut)
        zipOut.setLevel(9)

        for (file in files) {
            if (file.length() == 0L) {
                mainActivity.showToast("Zip 실패: ${file.name}이 비어있습니다.")
                continue
            }
            if (file == zipFile) {
                continue
            }

            val zipEntry = ZipEntry(file.name)
            zipOut.putNextEntry(zipEntry)

            val fileIn= FileInputStream(file)

            var read = fileIn.read(buffer)
            do {
                zipOut.write(buffer, 0, read)
                read = fileIn.read(buffer)
            }
            while (read > 0)

            fileIn.close()
            zipOut.closeEntry()
        }
        zipOut.close()
    }
}

