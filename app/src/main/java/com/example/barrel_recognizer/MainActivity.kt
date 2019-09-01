package com.example.barrel_recognizer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import com.google.firebase.ml.common.FirebaseMLException
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : BaseActivity() {

    private var currentPhotoFile: File? = null
    private var imagePreview: ImageView? = null
    private var textView: TextView? = null
    private var classifier: ImageClassifier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imagePreview = findViewById(R.id.image_preview)
        textView = findViewById(R.id.result_text)
        findViewById<Button>(R.id.photo_camera_button)?.setOnClickListener { takePhoto() }

        // ImageClassifier クラスを初期化
        try {
            classifier = ImageClassifier()
        } catch (e: FirebaseMLException) {
            textView?.text = "ImageClassifierの初期化に失敗しました"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_IMAGE_CAPTURE -> {
                FirebaseVisionImage.fromFilePath(this, Uri.fromFile(currentPhotoFile)).also {
                    classifyImage(it.bitmap)
                }
            }
            REQUEST_PHOTO_LIBRARY -> {
                val selectedImageUri = data?.data ?: return
                FirebaseVisionImage.fromFilePath(this, selectedImageUri).also {
                    classifyImage(it.bitmap)
                }
            }
        }
    }

    override fun onDestroy() {
        classifier?.close()
        super.onDestroy()
    }

    private fun takePhoto() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (e: IOException) {
                    Log.e(TAG, "Unable to save image to run classification.", e)
                    null
                }

                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.example.barrel_recognizer.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    /** カメラで撮った写真をJPEG形式に変換する */
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir = cacheDir
        return createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoFile = this
        }
    }


    private fun classifyImage(bitmap: Bitmap) {
        if (classifier == null) {
            textView?.text = "ImageClassifierが読み込めませんでした。"
            return
        }

        // スクリーンに選択/撮影した画像を表示
        imagePreview?.setImageBitmap(bitmap)
        // ラベル付けを行う
        classifier?.classifyFrame(bitmap)?.
            addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    textView?.text = task.result
                } else {
                    val e = task.exception
                    Log.e(TAG, "Error classifying frame", e)
                    textView?.text = e?.message
                }
            }
    }

    companion object {

        /** Tag for the [Log].  */
        private const val TAG = "MainActivity"

        /** Request code for starting photo capture activity  */
        private const val REQUEST_IMAGE_CAPTURE = 1

        /** Request code for starting photo library activity  */
        private const val REQUEST_PHOTO_LIBRARY = 2

    }
}
