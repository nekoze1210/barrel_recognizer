package com.example.barrel_recognizer

import android.graphics.Bitmap
import android.os.SystemClock
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.ml.common.FirebaseMLException
import com.google.firebase.ml.common.modeldownload.FirebaseLocalModel
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceAutoMLImageLabelerOptions
import java.io.IOException
import java.util.Locale

class ImageClassifier
@Throws(FirebaseMLException::class)
internal constructor() {
    private val labeler: FirebaseVisionImageLabeler?

    init {
        FirebaseModelManager.getInstance()
            .registerLocalModel(
                FirebaseLocalModel.Builder(LOCAL_MODEL_NAME)
                    .setAssetFilePath(LOCAL_MODEL_PATH)
                    .build()
            )

        val options = FirebaseVisionOnDeviceAutoMLImageLabelerOptions.Builder()
            .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
            .setLocalModelName(LOCAL_MODEL_NAME)
            .build()

        labeler = FirebaseVision.getInstance().getOnDeviceAutoMLImageLabeler(options)
    }

    /** 画像のラベル付けを行う */
    internal fun classifyFrame(bitmap: Bitmap): Task<String> {
        if (labeler == null) {
            val e = IllegalStateException("Uninitialized Classifier.")

            val completionSource = TaskCompletionSource<String>()
            completionSource.setException(e)
            return completionSource.task
        }

        val startTime = SystemClock.uptimeMillis()
        val image = FirebaseVisionImage.fromBitmap(bitmap)

        return labeler.processImage(image).continueWith { task ->
            val endTime = SystemClock.uptimeMillis()

            val labelProbList = task.result

            var textToShow = "Latency: " + java.lang.Long.toString(endTime - startTime) + "ms\n"
            textToShow += if (labelProbList.isNullOrEmpty())
                "No Result"
            else
                printResult(labelProbList)

            textToShow
        }
    }

    /** ラベルを非表示にする */
    internal fun close() {
        try {
            labeler?.close()
        } catch (e: IOException) {
        }
    }

    /** ラベル付けの結果を出力する **/
    private val printResult: (List<FirebaseVisionImageLabel>) -> String = {
        it.joinToString(
            separator = "\n",
            limit = RESULTS_TO_SHOW
        ) { label ->
            String.format(
                Locale.getDefault(),
                "Label: %s, Confidence: %4.2f",
                label.text,
                label.confidence
            )
        }
    }

    companion object {
        /** Auto ML Vision Edge で作成したモデル名 */
        private const val LOCAL_MODEL_NAME = "barrel_201981112318"
        /** assets配下に格納されたモデルファイルのパス */
        private const val LOCAL_MODEL_PATH = "automl/manifest.json"
        private const val RESULTS_TO_SHOW = 3
        private const val CONFIDENCE_THRESHOLD = 0.6f
    }
}
