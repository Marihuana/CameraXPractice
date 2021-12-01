package kr.bracket.homework.ocrcamerapractice

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Pair
import android.view.SurfaceHolder
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import kr.bracket.homework.ocrcamerapractice.databinding.ActivityMainBinding
import java.io.File
import java.lang.Exception
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var binding : ActivityMainBinding
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer : ImageAnalysis? = null
    private var result: MutableLiveData<List<String>> = MutableLiveData()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
        supportActionBar?.hide()

        //request camera permissions
        if(allPermissionsGranted()){
            setUpCamera()
        }else{
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.overlay.apply {
            //최상단으로
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSPARENT)   //surfaceView의 픽셀 포멧 설정(투명하게)
            holder.addCallback(object : SurfaceHolder.Callback{
                override fun surfaceCreated(holder: SurfaceHolder) {
                    drawOverlay(holder)
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                }


            })

        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == REQUEST_CODE_PERMISSIONS){
            if(allPermissionsGranted()){
                setUpCamera()
            }else{
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


//    private fun takePhoto() {
//        /*
//            먼저 ImageCapture 사용 사례를 참조하십시오.
//            use case 가 null인 경우 함수를 종료합니다.
//            image capture가 설정되기전에 포토 버튼을 누르면 이것은 null 입니다
//            return 구문 없이 null 값이라면 앱은 crash 날 것입니다.
//         */
//
//        //수정 가능한 이미지 캡처 use case 에 대한 안정적인 reference 를 가져온다
//        val imageCapture = imageCapture ?: return
//
//        //이미지를 담을 파일을 생성한다. 타임스탬프를 사용하여 유니크한 파일명을 작성한다
//        val photoFile = File(
//            outputDirectory,
//            SimpleDateFormat(FILENAME_FORMAT, Locale.US
//            ).format(System.currentTimeMillis()) + ".jpg"
//        )
//
//        /*
//            OutputFileOptions 객체를 만듭니다.
//            해당 객체는 원하는 출력 방법을 지정할 수 있습니다.
//            파일에 출력을 원하면 파일을 추가합니다.
//         */
//        //파일 + metadata 를 포함한 아웃풋 옵션을 생성한다.
//        val outputOption = ImageCapture.OutputFileOptions.Builder(photoFile).build()
//
//        /*
//            imageCapture 개체에서 takePicture()를 호출합니다.
//            outputOptions, 실행자, 이미지 저장 시 콜백을 파라미터로 전달합니다.
//         */
//        //사진 촬영 후 발동되는 이미지 캡쳐 리스너를 설정한다.
//        imageCapture.takePicture(
//            outputOption, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
//                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
//                    /*
//                        만일 캡쳐가 실패하지 않았다면 사진을 성공적으로 가져오게 됩니다.
//                        이전에 생성한 파일에 사진을 저장하고 토스트 메세지를 작성하여 유저가 이를 인지하게 합니다.
//                        로그를 작성합니다
//                     */
//                    val savedUri = Uri.fromFile(photoFile)
//                    val msg = "Photo capture succeeded : $savedUri"
//                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                    Log.d(TAG, msg)
//                }
//
//                override fun onError(e: ImageCaptureException) {
//                    /*
//                        이미지 캡처가 실패하거나 이미지 캡처 저장이 실패한 경우의 로그를 작성합니다.
//                     */
//                    Log.e(TAG, "Photo capture failed : ${e.message}", e)
//                }
//
//            }
//        )
//
//    }

    private fun setUpCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable{
            cameraProvider = cameraProviderFuture.get()

            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(){
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { binding.viewFinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = binding.viewFinder.display.rotation
        //rotation 0 -> portrait
        //rotation 1,3 -> landscape

        val preview = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    cameraExecutor
                    , TextRecognitionAnalyzer(
                        result,
                        DESIRE_WIDTH_PERCENT,
                        DESIRE_HEIGHT_PERCENT,
                        FRAME_RATIO
                    )
                )
            }

        result.observe(this){
            it.forEach(::checkValidData)
        }

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
//            cameraProvider.bindToLifecycle(
//                this, cameraSelector, preview, imageCapture, imageAnalyzer
//            )
            cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

        } catch (exc: IllegalStateException) {
            Log.e(TAG, "Use case binding failed. This must be running on main thread.", exc)
        }

    }

    private fun drawOverlay(
        holder: SurfaceHolder
    ){
        val canvas = holder.lockCanvas() //surface의 편집 시작점

        val bgPaint = Paint().apply {
            alpha = 140 //전체화면 어둡게
        }
        canvas.drawPaint(bgPaint)

        //인식될 사각 프레임 영역
        val rectPaint = Paint()
        //해당영역은 clear
        rectPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        rectPaint.style = Paint.Style.FILL
        rectPaint.color = Color.WHITE

        //사각 프레임 외곽선
        val outlinePaint = Paint()
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.color = Color.WHITE
        outlinePaint.strokeWidth = 4f

        val rotation = binding.viewFinder.display.rotation
        val surfaceWidth = holder.surfaceFrame.width()
        val surfaceHeight = holder.surfaceFrame.height()

        /*
             surfaceWidth * 2/5 = frameWidth
             1 : 1.6 = x : surfaceWidth
             x = surfaceWidth / 1.6 * 1

             surfaceWidth / 2 중앙 - frameWidth / 2
             surfaceHeight / 2 중앙 - frameHeight / 2

         */

        val (frameWidth, frameHeight) =
            if(surfaceWidth > surfaceHeight){
                Pair(surfaceWidth * FRAME_RATIO, surfaceWidth * FRAME_RATIO / DESIRE_WIDTH_PERCENT * DESIRE_HEIGHT_PERCENT)
            }else{
                Pair(surfaceHeight * FRAME_RATIO / DESIRE_WIDTH_PERCENT * DESIRE_HEIGHT_PERCENT, surfaceHeight * FRAME_RATIO)
            }

        val cornerRadius = 25f
        val rectTop = (surfaceHeight - frameHeight) / 2f
        val rectLeft = (surfaceWidth - frameWidth) / 2f
        val rectRight = (surfaceWidth + frameWidth) / 2f
        val rectBottom = (surfaceHeight + frameHeight) / 2f

        val rect = RectF(rectLeft, rectTop, rectRight, rectBottom)


        canvas.drawRoundRect(
            rect, cornerRadius, cornerRadius, rectPaint
        )
        canvas.drawRoundRect(
            rect, cornerRadius, cornerRadius, outlinePaint
        )
        holder.unlockCanvasAndPost(canvas)
    }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by comparing absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = ln(max(width, height).toDouble() / min(width, height))
        if (abs(previewRatio - ln(RATIO_4_3_VALUE))
            <= abs(previewRatio - ln(RATIO_16_9_VALUE))
        ) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all{
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun checkValidData(text : String){
        //카드 번호인지
        /*
            숫자 0000 0000 0000 0000
            숫자 0000-0000-0000-0000
         */

        val numPattern1 = "\\d{4} \\d{4} \\d{4} \\d{4}"
        val numPattern2 = "\\d{4}-\\d{4}-\\d{4}-\\d{4}"
        if(Pattern.compile(numPattern1).matcher(text).matches()){
            Log.d(TAG, "카드 번호 : $text")
            return
        }
        if(Pattern.compile(numPattern2).matcher(text).matches()){
            Log.d(TAG, "카드 번호 : $text")
            return
        }
        val invalidPattern1 = "\\d{2}/\\d{2}"
        val invalidPattern2 = "\\d{2} \\d{2}"
        if(Pattern.compile(invalidPattern1).matcher(text).matches()){
            Log.d(TAG, "유효기간 : $text")
            return
        }
        if(Pattern.compile(invalidPattern2).matcher(text).matches()){
            Log.d(TAG, "유효기간 : $text")
            return
        }

        //유효기간인지
        /*
            "/"포함하는지 포함하든 안하든 숫자2개씩 2개 인지
         */

    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        /*
            카드 크기는
            세로 5.39 : 가로 8.56
            비율로는 1:1.6
         */
        //가로 모드일 때

        //세로 모드일 때 (카드 내용이 세로로 되어있는 경우도 있다)

        //화면 : 카드 프레임 비율
        const val FRAME_RATIO = 0.4F

        const val DESIRE_WIDTH_PERCENT = 1.6F
        const val DESIRE_HEIGHT_PERCENT = 1F

        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }
}

