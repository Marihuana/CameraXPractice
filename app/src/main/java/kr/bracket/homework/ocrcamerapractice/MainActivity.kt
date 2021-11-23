package kr.bracket.homework.ocrcamerapractice

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import kr.bracket.homework.ocrcamerapractice.databinding.ActivityMainBinding
import java.io.File
import java.lang.Exception
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        //request camera permissions
        if(allPermissionsGranted()){
            startCamera()
        }else{
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        //촬영 버튼 리스너 등록
        binding.captureButton.setOnClickListener {
            takePhoto()
        }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == REQUEST_CODE_PERMISSIONS){
            if(allPermissionsGranted()){
                startCamera()
            }else{
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


    private fun takePhoto() {
        /*
            먼저 ImageCapture 사용 사례를 참조하십시오.
            use case 가 null인 경우 함수를 종료합니다.
            image capture가 설정되기전에 포토 버튼을 누르면 이것은 null 입니다
            return 구문 없이 null 값이라면 앱은 crash 날 것입니다.
         */

        //수정 가능한 이미지 캡처 use case 에 대한 안정적인 reference 를 가져온다
        val imageCapture = imageCapture ?: return

        //이미지를 담을 파일을 생성한다. 타임스탬프를 사용하여 유니크한 파일명을 작성한다
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )

        /*
            OutputFileOptions 객체를 만듭니다.
            해당 객체는 원하는 출력 방법을 지정할 수 있습니다.
            파일에 출력을 원하면 파일을 추가합니다.
         */
        //파일 + metadata 를 포함한 아웃풋 옵션을 생성한다.
        val outputOption = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        /*
            imageCapture 개체에서 takePicture()를 호출합니다.
            outputOptions, 실행자, 이미지 저장 시 콜백을 파라미터로 전달합니다.
         */
        //사진 촬영 후 발동되는 이미지 캡쳐 리스너를 설정한다.
        imageCapture.takePicture(
            outputOption, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    /*
                        만일 캡쳐가 실패하지 않았다면 사진을 성공적으로 가져오게 됩니다.
                        이전에 생성한 파일에 사진을 저장하고 토스트 메세지를 작성하여 유저가 이를 인지하게 합니다.
                        로그를 작성합니다
                     */
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded : $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }

                override fun onError(e: ImageCaptureException) {
                    /*
                        이미지 캡처가 실패하거나 이미지 캡처 저장이 실패한 경우의 로그를 작성합니다.
                     */
                    Log.e(TAG, "Photo capture failed : ${e.message}", e)
                }

            }
        )

    }
    private fun startCamera() {
        /*
           ProcessCameraProvider의 인스턴스를 생성합니다.
           이것은 카메라의 라이프 사이클을 라이프 사이클 소유자에 바인딩하는 데 사용됩니다.
           이렇게 하면 카메라X가 라이프사이클을 인식하므로 카메라를 열고 닫을 필요가 없습니다.
            */
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)


        /*
            cameraProviderFuture에 리스너를 등록합니다.
            Runnable 을 하나의 인수로 추가합니다.
            ContextCompat.getMainExecutor()를 두 번째 인수로 추가합니다.(기본 스레드에서 실행되는 실행자를 반환합니다.)
         */
        cameraProviderFuture.addListener({
            //카메라의 라이프사이클을 lifecycle owner 에 바인딩하기 위해 사용됨
            val cameraProvider : ProcessCameraProvider = cameraProviderFuture.get()


            /*
                미리보기 객체를 초기화하고 빌드를 호출한 다음 뷰파인더에서 surfaceProvider 를 가져온 다음 preview 에 설정합니다.
             */
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        Log.d(TAG, "Average luminosity : $luma")
                    })
                }

            //후면 카메라를 default 로 지정
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            /*
                try catch 블록을 만듭니다.
                해당 블록 내에서 cameraProvider 에 바인딩된 것이 없는지 확인한 다음
                cameraSelector 및 Preview 객체를 cameraProvider 에 바인딩합니다.
             */
            try {
                //다시 binding 하기전에 언바인딩 해준다.
                cameraProvider.unbindAll()

                //카메라 유즈케이스를 바인드 한다.
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (e : Exception){
                /*
                    앱이 더 이상 포커스가 맞지 않는 것처럼 이 코드가 실패할 수 있는 몇 가지 방법이 있습니다.
                     오류가 발생하면 이 코드를 캐치 블록에 래핑하여 기록합니다.
                 */
                Log.e(TAG, "Use case binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))

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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private class LuminosityAnalyzer(private val listener: (Double) -> Unit) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }
}

