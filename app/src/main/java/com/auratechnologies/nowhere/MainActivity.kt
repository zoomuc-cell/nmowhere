package com.auratechnologies.nowhere

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var movementDetector: MovementDetector
    private lateinit var movementDatabase: MovementDatabase
    private lateinit var photoDatabase: PhotoDatabase
    private lateinit var directionSensor: DirectionSensor
    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var latestLocation: Location? = null
    private var pendingPhotoFile: File? = null
    private var pendingPhotoUri: Uri? = null

    // 카메라에서 돌아온 직후 방향 센서가 새 값을 다시 보내기까지 기다리는 시간
    private val CAPTURE_SENSOR_SETTLE_MS = 600L
    private val captureHandler = Handler(Looper.getMainLooper())

    private val LOCATION_PERMISSION_REQUEST = 100

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                // 사진을 찍고 돌아온 순간의 위치와 방향을 저장한다.
                // 카메라 앱이 떠 있는 동안에는 이 화면이 뒤로 가 있어서 센서 값이
                // 잠시 멈춰 있을 수 있으므로, 새 값이 들어오도록 아주 잠깐 기다린다.
                showStatus("촬영 위치와 방향을 기록하는 중...")
                captureHandler.postDelayed(
                    { saveCapturedPhoto() },
                    CAPTURE_SENSOR_SETTLE_MS
                )
            } else {
                pendingPhotoFile?.delete()
                pendingPhotoFile = null
                pendingPhotoUri = null
                showStatus("사진 촬영이 취소되었습니다.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        movementDetector = MovementDetector(this)
        movementDatabase = MovementDatabase(this)
        photoDatabase = PhotoDatabase(this)
        directionSensor = DirectionSensor(this)

        webView = findViewById(R.id.webView)
        setupWebView()
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        directionSensor.start()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        // 저장 기록 관리에서 날짜별 기록을 지우고 돌아왔을 때 개수가 맞도록 다시 읽는다.
        movementDatabase = MovementDatabase(this)
        photoDatabase = PhotoDatabase(this)
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }
        }

        webView.webViewClient = WebViewClient()

        val htmlContent = """
<!DOCTYPE html><html><head><meta charset='UTF-8'>
<style>
    * {
        box-sizing: border-box;
        -webkit-tap-highlight-color: transparent;
    }

    body {
        font-family: Arial, sans-serif;
        margin: 0;
        padding: 18px;
        color: #f5f7fb;
        min-height: 100vh;

        background:
            radial-gradient(
                circle at 15% 0%,
                rgba(100, 160, 210, 0.22),
                transparent 32%
            ),
            radial-gradient(
                circle at 90% 15%,
                rgba(126, 95, 220, 0.15),
                transparent 28%
            ),
            linear-gradient(
                145deg,
                #66727e 0%,
                #4d5965 35%,
                #3b4651 70%,
                #303a44 100%
            );
    }

    .container {
        max-width: 650px;
        margin: 0 auto;

        background: rgba(42, 52, 62, 0.50);

        border: 1px solid rgba(190, 220, 245, 0.25);
        border-radius: 26px;

        padding: 22px;

        box-shadow:
            0 18px 45px rgba(0,0,0,0.22),
            inset 0 1px 0 rgba(255,255,255,0.08);

        backdrop-filter: blur(18px);
        -webkit-backdrop-filter: blur(18px);
    }

    h1 {
        text-align: left;
        margin: 5px 0 3px;

        color: #ffffff;

        font-size: 34px;
        font-weight: 700;
        letter-spacing: 7px;
    }

    .subtitle {
        text-align: left;

        color: #b8d9ef;

        margin-bottom: 20px;

        font-size: 13px;
        letter-spacing: 2px;
    }

    .status {
        text-align: left;

        padding: 14px 16px;
        margin-bottom: 22px;

        border-radius: 18px;

        background:
            linear-gradient(
                135deg,
                rgba(124, 180, 220, 0.20),
                rgba(95, 110, 145, 0.16)
            );

        border: 1px solid rgba(184, 220, 245, 0.30);

        color: #eef8ff;

        line-height: 1.5;

        box-shadow:
            inset 0 1px 0 rgba(255,255,255,0.08);
    }

    .section-title {
        margin: 22px 2px 12px;

        font-size: 14px;
        font-weight: 600;

        color: #c5d6e3;

        letter-spacing: 1px;
    }

    .menu-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 12px;
    }

    .menu {
        position: relative;

        width: 100%;
        min-height: 126px;

        border-radius: 22px;

        /* 아래쪽 52px은 오른쪽 아래 화살표(›) 자리라서 글자가 겹치지 않게 비워 둔다 */
        padding: 18px 14px 52px 14px;

        text-align: left;

        /* 긴 글자는 단어 단위로 두 줄로 넘어가게 한다 */
        line-height: 1.3;
        word-break: keep-all;

        font-size: 15px;
        font-weight: 600;

        color: #ffffff;

        cursor: pointer;

        overflow: hidden;

        border: 1px solid rgba(185, 215, 238, 0.32);

        background:
            linear-gradient(
                145deg,
                rgba(91, 108, 124, 0.72),
                rgba(50, 62, 73, 0.76)
            );

        box-shadow:
            0 8px 18px rgba(0,0,0,0.16),
            inset 0 1px 0 rgba(255,255,255,0.10);

        transition:
            transform 0.15s ease,
            border-color 0.15s ease,
            background 0.15s ease;
    }

    .menu::after {
        content: "›";

        position: absolute;
        right: 14px;
        bottom: 12px;

        width: 32px;
        height: 32px;

        display: flex;
        align-items: center;
        justify-content: center;

        border-radius: 50%;

        background: rgba(140, 195, 230, 0.17);

        color: #aee6ff;

        font-size: 28px;
        font-weight: 300;
    }

   .menu span {
       display: flex;
       align-items: center;
       justify-content: center;

       width: 58px;
       height: 58px;

       margin-bottom: 14px;

       border-radius: 18px;

       background:
           linear-gradient(
               145deg,
               rgba(77, 196, 255, 0.16),
               rgba(144, 91, 255, 0.14)
           );

       border: 1px solid rgba(154, 217, 255, 0.20);

       box-shadow:
           inset 0 1px 0 rgba(255,255,255,0.10),
           0 6px 16px rgba(0,0,0,0.15);

       filter: none;
   }

   .menu-icon {
       width: 38px;
       height: 38px;

       stroke: url(#iconGradient);
       stroke-width: 1.8;
       stroke-linecap: round;
       stroke-linejoin: round;

       fill: none;

       filter:
           drop-shadow(
               0 3px 5px rgba(35, 175, 255, 0.22)
           );
   }

   .menu-icon .icon-fill {
       fill: rgba(111, 104, 255, 0.28);
       stroke: url(#iconGradient);
   }

    .active-green,
    .active-blue,
    .ready {
        color: #ffffff;

        background:
            linear-gradient(
                145deg,
                rgba(91, 108, 124, 0.76),
                rgba(50, 62, 73, 0.78)
            );

        border: 1px solid rgba(185, 215, 238, 0.32);
    }

    .menu:active {
        transform: scale(0.965);

        background:
            linear-gradient(
                145deg,
                rgba(105, 128, 148, 0.82),
                rgba(58, 71, 84, 0.84)
            );

        border-color: rgba(120, 210, 255, 0.65);
    }

    .footer {
        margin-top: 24px;

        padding: 17px;

        border-radius: 18px;

        background: rgba(30, 40, 49, 0.32);

        border: 1px solid rgba(185, 215, 238, 0.18);

        color: #b9cad7;

        font-size: 12px;
        line-height: 1.7;

        text-align: center;

        letter-spacing: 0.3px;
    }
</style>
</head>

<body>
<svg width="0" height="0" style="position:absolute">
    <defs>
        <linearGradient id="iconGradient"
                        x1="0%" y1="0%"
                        x2="100%" y2="100%">
            <stop offset="0%" stop-color="#58dcff" />
            <stop offset="48%" stop-color="#4297ff" />
            <stop offset="100%" stop-color="#a765ff" />
        </linearGradient>
    </defs>
</svg>
<div class="container">

    <h1>NOWHERE</h1>

    <div class="subtitle">
        LOCATION · PHOTO · TRACK · EVIDENCE
    </div>

    <div class="status" id="status">
        위치 및 방향 센서 준비 중...
    </div>

   <div class="section-title">지금 이 순간</div>

   <div class="menu-grid">

       <!-- 1. 현장 사진 촬영 -->
       <button class="menu active-green"
               onclick="takeEvidencePhoto()">
           <span>
               <svg class="menu-icon" viewBox="0 0 24 24">
                   <path class="icon-fill"
                         d="M4 7h4l1.5-2h5L16 7h4v12H4z"/>
                   <circle cx="12" cy="13" r="4"/>
                   <circle cx="18" cy="9" r="0.8"/>
               </svg>
           </span>
           현장 사진 촬영
       </button>

       <!-- 2. 현재 위치 -->
       <button class="menu active-blue"
               onclick="openCurrentLocation()">
           <span>
               <svg class="menu-icon" viewBox="0 0 24 24">
                   <path class="icon-fill"
                         d="M12 21s6-5.4 6-11a6 6 0 1 0-12 0c0 5.6 6 11 6 11z"/>
                   <circle cx="12" cy="10" r="2.2"/>
               </svg>
           </span>
           현재 위치
       </button>

   </div>


   <div class="section-title">사진 기록</div>

   <div class="menu-grid">

       <!-- 3. 촬영 사진 기록 -->
       <button class="menu ready"
               onclick="openPhotoRecords()">
           <span>
               <svg class="menu-icon" viewBox="0 0 24 24">
                   <rect class="icon-fill"
                         x="3" y="4"
                         width="18" height="16"
                         rx="2"/>
                   <circle cx="9" cy="9" r="2"/>
                   <path d="M4 17l5-5 4 4 2-2 5 5"/>
               </svg>
           </span>
           촬영 사진 기록
       </button>

       <!-- 4. 사진 촬영 위치 -->
       <button class="menu ready"
               onclick="openPhotoMap()">
           <span>
               <svg class="menu-icon" viewBox="0 0 24 24">
                   <rect class="icon-fill"
                         x="3" y="6"
                         width="12" height="11"
                         rx="2"/>
                   <circle cx="9" cy="11.5" r="3"/>
                   <path d="M18 21s3-2.8 3-5.5a3 3 0 1 0-6 0c0 2.7 3 5.5 3 5.5z"/>
                   <circle cx="18" cy="15.5" r="1"/>
               </svg>
           </span>
           사진 촬영 위치
       </button>

       <!-- 5. 사진 촬영 방향 -->
       <button class="menu ready"
               onclick="openPhotoDirectionMap()">
           <span>
               <svg class="menu-icon" viewBox="0 0 24 24">
                   <circle class="icon-fill"
                           cx="12" cy="12" r="9"/>
                   <path d="M15.8 8.2l-2.4 5.2-5.2 2.4 2.4-5.2z"/>
                   <circle cx="12" cy="12" r="1"/>
               </svg>
           </span>
           사진 촬영 방향
       </button>

       <!-- 6. 이동 경로 -->
       <button class="menu active-blue"
               onclick="openMap()">
           <span>
               <svg class="menu-icon" viewBox="0 0 24 24">
                   <circle class="icon-fill"
                           cx="5" cy="18" r="2"/>
                   <circle class="icon-fill"
                           cx="19" cy="6" r="2"/>
                   <path d="M7 18c5 0 2-7 7-7h1c2 0 2-3 2-3"/>
               </svg>
           </span>
           이동 경로
       </button>

   </div>


   <div class="section-title">통합 기록</div>

   <div class="menu-grid">

       <!-- 7. 통합 기록 지도 -->
       <button class="menu ready"
               onclick="openIntegratedMap()">
           <span>
               <svg class="menu-icon" viewBox="0 0 24 24">
                   <path class="icon-fill"
                         d="M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3z"/>
                   <path d="M9 3v15M15 6v15"/>
                   <path d="M18 14s2-1.8 2-3.5a2 2 0 1 0-4 0c0 1.7 2 3.5 2 3.5z"/>
               </svg>
           </span>
           통합 기록 지도
       </button>

       <!-- 8. 시간순 기록 -->
       <button class="menu ready"
               onclick="openTimeline()">
           <span>
               <svg class="menu-icon" viewBox="0 0 24 24">
                   <circle class="icon-fill"
                           cx="12" cy="12" r="9"/>
                   <path d="M12 7v5l3.5 2"/>
                   <path d="M12 3v2M21 12h-2M12 21v-2M3 12h2"/>
               </svg>
           </span>
           시간순 기록
       </button>

       <!-- 9. 기록 보고서 / PDF -->
       <button class="menu ready"
               onclick="openReport()">
           <span>
               <svg class="menu-icon" viewBox="0 0 24 24">
                   <path class="icon-fill"
                         d="M6 2h8l4 4v16H6z"/>
                   <path d="M14 2v5h4M9 11h6M9 15h6M9 19h4"/>
               </svg>
           </span>
           기록 보고서 / PDF
       </button>

       <!-- 10. 저장 기록 관리 -->
       <button class="menu ready"
               onclick="openRecordManagement()">
           <span>
               <svg class="menu-icon" viewBox="0 0 24 24">
                   <path class="icon-fill"
                         d="M3 7h7l2 2h9v11H3z"/>
                   <path d="M3 7V5h7l2 2h7"/>
                   <path d="M8 14h8M8 17h6"/>
               </svg>
           </span>
           저장 기록 관리
       </button>

   </div>

    <div class="footer">
        REAL PLACES · REAL EVIDENCE<br>
        오늘의 현장을 더 확실한 내일의 기록으로.
    </div>

</div>

<script>
    function takeEvidencePhoto() {
        Android.takeEvidencePhoto();
    }
    function openCurrentLocation() {
        Android.openCurrentLocation();
    }

    function openMap() {
        Android.openMapActivity();
    }
    function openPhotoRecords() {
        Android.openPhotoRecords();
    }
    function openPhotoMap() {
        Android.openPhotoMap();
    }
    function openPhotoDirectionMap() {
        Android.openPhotoDirectionMap();
    }
    function openIntegratedMap() {
        Android.openIntegratedMap();
    }
    function openTimeline() {
        Android.openTimeline();
    }
    function openReport() {
        Android.openReport();
    }
    function openRecordManagement() {
        Android.openRecordManagement();
    }

    function comingSoon(name) {
        updateStatus('🛠️ ' + name + ' 기능을 연결하는 중입니다.');
    }

    function updateStatus(message) {
        document.getElementById('status').innerText = message;
    }

    window.onload = function() {
        updateStatus('🛰️ 현재 위치 확인 중...');
    };
</script>

</body>
</html>
""".trimIndent()

        webView.loadDataWithBaseURL(
            "https://nowhere.app",
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )
    }

    inner class WebAppInterface {

        @JavascriptInterface
        fun openMapActivity() {
            runOnUiThread {
                startActivity(
                    Intent(this@MainActivity, MapActivity::class.java)
                )
            }
        }
        @JavascriptInterface
        fun openPhotoRecords() {
            runOnUiThread {
                startActivity(
                    Intent(this@MainActivity, PhotoRecordsActivity::class.java)
                )
            }
        }

        @JavascriptInterface
        fun openPhotoMap() {
            runOnUiThread {
                startActivity(
                    Intent(this@MainActivity, PhotoMapActivity::class.java)
                )
            }
        }
        @JavascriptInterface
        fun openPhotoDirectionMap() {
            runOnUiThread {
                startActivity(
                    Intent(this@MainActivity, PhotoDirectionMapActivity::class.java)
                )
            }
        }
        @JavascriptInterface
        fun openIntegratedMap() {
            runOnUiThread {
                startActivity(
                    Intent(this@MainActivity, IntegratedMapActivity::class.java)
                )
            }
        }

        @JavascriptInterface
        fun openTimeline() {
            runOnUiThread {
                startActivity(
                    Intent(this@MainActivity, TimelineActivity::class.java)
                )
            }
        }

        @JavascriptInterface
        fun openReport() {
            runOnUiThread {
                startActivity(
                    Intent(
                        this@MainActivity,
                        ReportActivity::class.java
                    )
                )
            }
        }

        @JavascriptInterface
        fun openRecordManagement() {
            runOnUiThread {
                startActivity(
                    Intent(
                        this@MainActivity,
                        RecordManagementActivity::class.java
                    )
                )
            }
        }
        @JavascriptInterface
        fun openCurrentLocation() {
            runOnUiThread {

                val location = latestLocation

                if (location == null) {
                    showStatus(
                        "📍 현재 위치를 확인하고 있습니다..."
                    )
                    return@runOnUiThread
                }

                val intent =
                    Intent(
                        this@MainActivity,
                        CurrentLocationActivity::class.java
                    ).apply {

                        putExtra("latitude", location.latitude)
                        putExtra("longitude", location.longitude)
                        putExtra("altitude", location.altitude)
                        putExtra("accuracy", location.accuracy)
                        putExtra("azimuth", directionSensor.azimuth)

                        putExtra(
                            "movementMode",
                            movementDetector.currentMode.name
                        )
                    }

                startActivity(intent)
            }
        }
        @JavascriptInterface
        fun takeEvidencePhoto() {
            runOnUiThread {
                beginPhotoCapture()
            }
        }
    }

    private fun beginPhotoCapture() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
            return
        }

        if (latestLocation == null) {
            Toast.makeText(
                this,
                "정확한 위치를 수신한 후 다시 촬영해주세요.",
                Toast.LENGTH_LONG
            ).show()
            showStatus("GPS 위치 수신 대기 중...")
            return
        }

        if (!directionSensor.hasReading) {
            Toast.makeText(
                this,
                "방향 센서 값을 준비하고 있습니다. 잠시 후 다시 시도해주세요.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val photoDirectory = File(filesDir, "photos")
        if (!photoDirectory.exists()) {
            photoDirectory.mkdirs()
        }

        val photoFile = File(
            photoDirectory,
            "NOWHERE_${System.currentTimeMillis()}.jpg"
        )

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )

        // 위치와 방향은 여기(버튼을 누른 순간)가 아니라
        // 사진을 찍고 돌아온 순간에 saveCapturedPhoto()에서 저장한다.
        pendingPhotoFile = photoFile
        pendingPhotoUri = uri


        cameraLauncher.launch(uri)
    }

    private fun saveCapturedPhoto() {
        val file = pendingPhotoFile
        val uri = pendingPhotoUri

        pendingPhotoFile = null
        pendingPhotoUri = null

        // 사진을 찍고 돌아온 지금 이 순간의 위치와 방향
        val captureLocation = latestLocation

        if (file == null || uri == null || captureLocation == null) {
            Toast.makeText(
                this,
                "사진 기록 정보를 완성할 수 없습니다.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val record = PhotoRecord(
            id = UUID.randomUUID().toString(),
            photoUri = uri.toString(),

            timestamp = System.currentTimeMillis(),

            latitude = captureLocation.latitude,
            longitude = captureLocation.longitude,
            altitude = captureLocation.altitude,

            locationAccuracy = captureLocation.accuracy,

            azimuth = directionSensor.azimuth,
            directionAccuracy = directionSensor.accuracy,

            transportMode = movementDetector.currentMode.name
        )

        photoDatabase.add(record)

        val directionText =
            String.format("%.1f° %s", record.azimuth, directionSensor.getDirectionName())

        Toast.makeText(
            this,
            "사진 기록 완료\n방향: $directionText\nGPS 정확도: ${record.locationAccuracy.toInt()}m",
            Toast.LENGTH_LONG
        ).show()

        showStatus(
            "📷 사진 ${photoDatabase.getTotalRecords()}장 저장 · 방향 $directionText"
        )

        Log.d(
            "NOWHERE",
            "사진 기록: ${record.latitude}, ${record.longitude}, " +
                    "방향=${record.azimuth}, 위치정확도=${record.locationAccuracy}, " +
                    "방향정확도=${record.directionAccuracy}"
        )

    }

    private fun showStatus(message: String) {
        val escaped = message
            .replace("\\", "\\\\")
            .replace("'", "\\'")

        webView.evaluateJavascript(
            "if(typeof updateStatus === 'function') updateStatus('$escaped');",
            null
        )
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            startTracking()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                ) {
                    startTracking()
                } else {
                    Toast.makeText(
                        this,
                        "위치 및 활동 권한이 필요합니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    beginPhotoCapture()
                } else {
                    Toast.makeText(
                        this,
                        "사진 촬영을 위해 카메라 권한이 필요합니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startTracking() {
        movementDetector.startDetection { mode ->
            Log.d("NOWHERE", "이동 수단 감지: $mode")
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showStatus("⚠️ 위치 권한이 필요합니다.")
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 먼저 최근에 확보된 위치가 있으면 즉시 사용
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                latestLocation = location

                showStatus(
                    "📍 위치 확보됨 · 정확도 ${location.accuracy.toInt()}m"
                )

                Log.d(
                    "NOWHERE",
                    "초기 위치 확보: ${location.latitude}, ${location.longitude}, " +
                            "정확도=${location.accuracy}m"
                )
            } else {
                showStatus("🛰️ 현재 위치 확인 중...")
            }
        }

        val locationRequest =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000L
            )
                .setMinUpdateDistanceMeters(5f)
                .setMinUpdateIntervalMillis(2000L)
                .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                latestLocation = location

                movementDetector.updateSpeed(location)

                movementDatabase.addRecord(
                    location,
                    movementDetector.currentMode
                )

                val accuracy = location.accuracy.toInt()

                showStatus(
                    "📍 위치 확보됨 · 정확도 ${accuracy}m · " +
                            "이동상태 ${movementDetector.currentMode}"
                )

                Log.d(
                    "NOWHERE",
                    "위치 기록: ${location.latitude}, ${location.longitude}, " +
                            "정확도=${location.accuracy}m, " +
                            "이동수단=${movementDetector.currentMode}, " +
                            "속도=${location.speed * 3.6f} km/h"
                )
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )

        Log.d("NOWHERE", "Fused Location 위치 추적 시작됨")
    }


override fun onDestroy() {
    captureHandler.removeCallbacksAndMessages(null)
    directionSensor.stop()
    movementDetector.stopDetection()

    if (::fusedLocationClient.isInitialized &&
        ::locationCallback.isInitialized
    ) {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    Log.d(
        "NOWHERE",
        "이동 기록=${movementDatabase.getTotalRecords()}, " +
                "사진 기록=${photoDatabase.getTotalRecords()}"
    )

    super.onDestroy()
}

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 102
    }
}