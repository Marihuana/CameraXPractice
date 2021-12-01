package kr.bracket.homework.ocrcamerapractice

import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition

class TextRecognitionAnalyzer(
    private val result: MutableLiveData<List<String>>,
    private val widthPercentage : Float,
    private val heightPercentage : Float,
    private val frameRatio : Float,
) : ImageAnalysis.Analyzer{

    private val detector = TextRecognition.getClient()
    var frameWidth : Float? = null
    var frameHeight : Float? = null

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
       val mediaImage = imageProxy.image ?: return


        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // We requested a setTargetAspectRatio, but it's not guaranteed that's what the camera
        // stack is able to support, so we calculate the actual ratio from the first frame to
        // know how to appropriately crop the image we want to analyze.
        val imageHeight = mediaImage.height
        val imageWidth = mediaImage.width

        val actualAspectRatio = imageWidth / imageHeight

        val convertImageToBitmap = ImageUtils.convertYuv420888ImageToBitmap(mediaImage)
        val cropRect = Rect(0, 0, imageWidth, imageHeight)

        // 이미지의 가로 세로 비율이 예상보다 훨씬 넓은 경우 이미지를 너무 많이 자르지 않도록 높이를 적게 자릅니다.
        // 이미지 가로 세로 비율이 예상보다 훨씬 높으면 자른 부분을 변경하지 않아도 되므로 여기서는 취급하지 않습니다.
//        if (actualAspectRatio > 3) {
//            val originalHeightCropPercentage = currentCropPercentages.first
//            val originalWidthCropPercentage = currentCropPercentages.second
//            imageCropPercentages.value =
//                Pair(originalHeightCropPercentage / 2, originalWidthCropPercentage)
//        }

        // If the image is rotated by 90 (or 270) degrees, swap height and width when calculating
        // the crop.
//        val cropPercentages = imageCropPercentages.value ?: return
//        val heightCropPercent = cropPercentages.first
//        val widthCropPercent = cropPercentages.second
//        val (widthCrop, heightCrop) = when (rotationDegrees) {
//            90, 270 -> Pair(heightCropPercentage / 100f, widthCropPercentage / 100f)
//            else -> Pair(widthCropPercentage / 100f, heightCropPercentage / 100f)
//        }


        if(frameWidth == null || frameHeight == null) {
            if(imageWidth > imageHeight){
                frameWidth = imageWidth * frameRatio
                frameHeight = frameWidth!! / widthPercentage * heightPercentage
            }else{
                frameHeight = imageHeight * frameRatio
                frameWidth = frameHeight!! / widthPercentage * heightPercentage
            }
        }

        //패딩
        cropRect.inset(
            ((imageWidth - frameWidth!!) / 2).toInt(),
            ((imageHeight - frameHeight!!) / 2).toInt(),
        )

        val croppedBitmap =
            ImageUtils.rotateAndCrop(convertImageToBitmap, rotationDegrees, cropRect)

        recognizeTextOnDevice(InputImage.fromBitmap(croppedBitmap, 0)).addOnCompleteListener {
            imageProxy.close()
        }

    }

    private fun recognizeTextOnDevice(
        image: InputImage
    ): Task<Text> {
        // Pass image to an ML Kit Vision API
        return detector.process(image)
            .addOnSuccessListener { visionText ->
                // Task completed successfully
                result.value = visionText.textBlocks.map { it.text }
            }
            .addOnFailureListener { exception ->
                // Task failed with an exception
                Log.e(TAG, "Text recognition error", exception)
//                val message = getErrorMessage(exception)
//                message?.let {
//                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
//                }
            }
    }

    companion object {
        private const val TAG = "TextAnalyzer"
    }

    private fun croppedRect() : Rect{

        return Rect()
    }
}