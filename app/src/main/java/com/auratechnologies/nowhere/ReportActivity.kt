package com.auratechnologies.nowhere

import com.auratechnologies.nowhere.billing.ProGate
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream

class ReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProGate.redirectIfLocked(this)) return

        val movementDatabase = MovementDatabase(this)
        val photoDatabase = PhotoDatabase(this)

        val isDateFiltered =
            intent.hasExtra("startTime") &&
                    intent.hasExtra("endTime")

        val requestedStartTime =
            if (isDateFiltered) {
                intent.getLongExtra("startTime", 0L)
            } else {
                0L
            }

        val requestedEndTime =
            if (isDateFiltered) {
                intent.getLongExtra("endTime", 0L)
            } else {
                0L
            }

        val selectedDate =
            intent.getStringExtra("selectedDate")

        val movements =
            if (isDateFiltered) {
                movementDatabase
                    .getRecordsInRange(
                        requestedStartTime,
                        requestedEndTime
                    )
                    .sortedBy { it.timestamp }
            } else {
                movementDatabase
                    .getAllRecords()
                    .sortedBy { it.timestamp }
            }

        val photos =
            if (isDateFiltered) {
                photoDatabase
                    .getInRange(
                        requestedStartTime,
                        requestedEndTime
                    )
                    .sortedBy { it.timestamp }
            } else {
                photoDatabase
                    .getAll()
                    .sortedBy { it.timestamp }
            }

        if (movements.isEmpty() && photos.isEmpty()) {
            Toast.makeText(
                this,
                "작성할 기록이 없습니다.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        val allTimes =
            movements.map { it.timestamp } +
                    photos.map { it.timestamp }

        val startTime = allTimes.minOrNull() ?: 0L
        val endTime = allTimes.maxOrNull() ?: 0L

        val dateFormat =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.KOREA
            )

        val startText =
            dateFormat.format(Date(startTime))

        val endText =
            dateFormat.format(Date(endTime))
        val movementData = JSONArray()

        movements.forEach { record ->
            movementData.put(
                JSONObject().apply {
                    put("lat", record.latitude)
                    put("lng", record.longitude)
                    put("mode", record.transportMode)
                    put("time", record.timestamp)
                }
            )
        }

        val photoMapData = JSONArray()

        photos.forEachIndexed { index, photo ->
            val endPoint = calculateEndPoint(
                photo.latitude,
                photo.longitude,
                photo.azimuth,
                45.0
            )

            photoMapData.put(
                JSONObject().apply {
                    put("number", index + 1)
                    put("lat", photo.latitude)
                    put("lng", photo.longitude)
                    put("endLat", endPoint.first)
                    put("endLng", endPoint.second)
                    put("azimuth", photo.azimuth)
                    put("direction", directionName(photo.azimuth))
                }
            )
        }
        val timelineEvents = mutableListOf<JSONObject>()

        var previousMode: String? = null

        movements
            .sortedBy { it.timestamp }
            .forEach { record ->

                if (
                    previousMode == null ||
                    previousMode != record.transportMode
                ) {
                    timelineEvents.add(
                        JSONObject().apply {
                            put("type", "movement")
                            put("timestamp", record.timestamp)
                            put(
                                "time",
                                dateFormat.format(
                                    Date(record.timestamp)
                                )
                            )
                            put("lat", record.latitude)
                            put("lng", record.longitude)
                            put("mode", record.transportMode)
                            put(
                                "accuracy",
                                record.accuracy.toInt()
                            )
                        }
                    )
                }

                previousMode = record.transportMode
            }

        photos.forEachIndexed { index, photo ->
            timelineEvents.add(
                JSONObject().apply {
                    put("type", "photo")
                    put("number", index + 1)
                    put("timestamp", photo.timestamp)
                    put(
                        "time",
                        dateFormat.format(
                            Date(photo.timestamp)
                        )
                    )
                    put("lat", photo.latitude)
                    put("lng", photo.longitude)
                    put(
                        "accuracy",
                        photo.locationAccuracy.toInt()
                    )
                    put("azimuth", photo.azimuth)
                    put(
                        "direction",
                        directionName(photo.azimuth)
                    )
                    put("mode", photo.transportMode)
                }
            )
        }

        timelineEvents.sortBy {
            it.getLong("timestamp")
        }

        val timelineData = JSONArray()

        timelineEvents.forEach {
            timelineData.put(it)
        }

        val photoHtml = StringBuilder()

        photos.forEachIndexed { index, photo ->
            val photoDataUrl = photoUriToBase64(photo.photoUri)

            photoHtml.append(
                """
        <div class="photo-card">

            <img
                src="${photoDataUrl ?: ""}"
                class="report-photo"
            />

            <div class="photo-title">
                📷 사진 ${index + 1}
            </div>

            <div class="details">
                촬영 시각:
                ${dateFormat.format(Date(photo.timestamp))}
                <br>

                위치:
                ${String.format(Locale.US, "%.6f", photo.latitude)},
                ${String.format(Locale.US, "%.6f", photo.longitude)}
                <br>

                GPS 정확도:
                ±${photo.locationAccuracy.toInt()}m
                <br>

                촬영 방향:
                ${String.format(Locale.US, "%.1f", photo.azimuth)}°
                · ${directionName(photo.azimuth)}
                <br>

                이동 상태:
                ${photo.transportMode}
            </div>
        </div>
        """
            )
        }

        val html =
            """
    <!DOCTYPE html>
    <html>

            <head>

                <meta charset="utf-8">

                <meta name="viewport"
                      content="width=device-width,
                      initial-scale=1.0">

                <style>

                    * {
                        box-sizing:border-box;
                    }

                    body {
                        margin:0;
                        padding:0;
                        background:#f4f5f8;
                        font-family:
                            -apple-system,
                            BlinkMacSystemFont,
                            "Segoe UI",
                            sans-serif;
                        color:#202124;
                    }

                    .header {
                        background:#ffffff;
                        padding:25px 20px 20px;
                        box-shadow:
                            0 2px 8px rgba(0,0,0,0.10);
                    }

                    .title {
                        font-size:26px;
                        font-weight:800;
                    }

                    .subtitle {
                        margin-top:7px;
                        color:#666;
                        font-size:14px;
                    }

                    .content {
                        padding:18px;
                    }

                    .section {
                        background:white;
                        border-radius:16px;
                        padding:18px;
                        margin-bottom:16px;
                        box-shadow:
                            0 2px 8px rgba(0,0,0,0.07);
                    }

                    .section-title {
                        font-size:19px;
                        font-weight:800;
                        margin-bottom:12px;
                    }

                    .details {
                        font-size:15px;
                        line-height:1.75;
                        color:#4d4d4d;
                    }

                    .summary-grid {
                        display:grid;
                        grid-template-columns:1fr 1fr;
                        gap:10px;
                        margin-top:12px;
                    }

                    .summary-box {
                        background:#f3f6ff;
                        border-radius:12px;
                        padding:14px;
                        text-align:center;
                    }

                    .number {
                        font-size:25px;
                        font-weight:800;
                        color:#3949ab;
                    }

                    .label {
                        margin-top:4px;
                        color:#666;
                        font-size:13px;
                    }
                    .report-photo {
                        width:100%;
                        max-height:420px;
                        object-fit:contain;
                        background:#eeeeee;
                        border-radius:12px;
                        margin-bottom:12px;
                    }

                    .photo-card {
                        border-top:
                            1px solid #e5e5e5;
                        padding:15px 0;
                    }

                    .photo-card:first-child {
                        border-top:none;
                    }

                    .photo-title {
                        font-size:17px;
                        font-weight:800;
                        margin-bottom:7px;
                    }

                    .notice {
                        background:#fff8e1;
                        border-radius:12px;
                        padding:13px;
                        font-size:13px;
                        line-height:1.6;
                        color:#665500;
                    }
                    #report-map {
                        width:100%;
                        height:420px;
                        border-radius:14px;
                        overflow:hidden;
                        margin-top:12px;
                        background:#eeeeee;
                    }

                    .map-legend {
                        display:flex;
                        flex-wrap:wrap;
                        gap:12px;
                        margin-top:10px;
                        font-size:13px;
                        color:#555555;
                    }
                    .timeline-title {
                        margin-top:28px;
                        margin-bottom:14px;
                        font-size:20px;
                        font-weight:700;
                    }

                    #report-timeline {
                        position:relative;
                        padding-left:24px;
                        border-left:3px solid #d7dbe7;
                    }

                    .timeline-event {
                        position:relative;
                        background:#ffffff;
                        border-radius:12px;
                        padding:12px 14px;
                        margin-bottom:14px;
                        box-shadow:0 2px 8px rgba(0,0,0,0.08);
                    }

                    .timeline-dot {
                        position:absolute;
                        left:-32px;
                        top:16px;
                        width:13px;
                        height:13px;
                        border-radius:50%;
                        border:3px solid white;
                        box-shadow:0 1px 4px rgba(0,0,0,0.30);
                    }

                    .timeline-movement-dot {
                        background:#1976d2;
                    }

                    .timeline-photo-dot {
                        background:#e53935;
                    }

                    .timeline-time {
                        font-weight:700;
                        margin-bottom:5px;
                    }

                    .timeline-type {
                        font-weight:700;
                        margin-bottom:5px;
                    }

                    .timeline-details {
                        font-size:13px;
                        line-height:1.6;
                        color:#555555;
                    }

                </style>

<link rel="stylesheet"
      href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>

<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>

            </head>

            <body>

           <div class="header">

    <div class="title">
        📋 NOWHERE 기록 보고서
    </div>

    <div class="subtitle">
        위치 · 이동 · 사진 · 방향 통합 기록
    </div>

</div>

<div class="content">

    <div class="section">

        <div class="section-title">
            기록 개요
        </div>

        <div class="details">
            시작:
            $startText
            <br>

            종료:
            $endText
        </div>

        <div class="summary-grid">

            <div class="summary-box">
                <div class="number">
                   ${movements.size}
                </div>
                <div class="label">
                    이동 위치 기록
                </div>
            </div>

            <div class="summary-box">
                <div class="number">
                   ${photos.size}
                </div>
                <div class="label">
                    촬영 사진
                </div>
            </div>

        </div>

    </div>

    <div class="section">

        <div class="section-title">
            📍 현장 기록 요약
        </div>

        <div class="details">
            이동 위치와 현장 사진을
            기록 시각을 기준으로 연결하여
            관리합니다.
        </div>

    </div>

    <div class="section">

        <div class="section-title">
            📷 촬영 사진 기록
        </div>

        ${photoHtml.toString()}

    </div>

    <div class="section">

        <div class="section-title">
            🗺 지도 및 시간순 기록
        </div>

        <div id="report-map"></div>

        <div class="map-legend">
            <span>🔵 이동 경로</span>
            <span>🔷 사진 위치</span>
            <span>🔴 촬영 방향</span>
        </div>
        <div class="timeline-title">
            ⏱ 시간순 기록
        </div>

        <div id="report-timeline"></div>

    </div>

    <div class="notice">
        현재 화면은 NOWHERE 기록 보고서의
        미리보기입니다.
        다음 출력 Gate에서 사진 이미지,
        지도 및 PDF/Word 문서 생성을
        연결합니다.
    </div>

                </div>
    <script>
    const movements = ${movementData.toString()};
    const mapPhotos = ${photoMapData.toString()};
    const timelineEvents = ${timelineData.toString()};

    const map = L.map('report-map');

    L.tileLayer(
        'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
        {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap contributors'
        }
    ).addTo(map);

    const bounds = [];

    if (movements.length > 0) {
        const routePoints = movements.map(function(record) {
            bounds.push([record.lat, record.lng]);
            return [record.lat, record.lng];
        });

        L.polyline(
            routePoints,
            {
                color: '#1565c0',
                weight: 5,
                opacity: 0.75
            }
        ).addTo(map);
    }

    mapPhotos.forEach(function(photo) {
        const photoIcon = L.divIcon({
            className: '',
            html:
                '<div style="' +
                'background:#3949ab;' +
                'color:white;' +
                'border-radius:50%;' +
                'width:30px;' +
                'height:30px;' +
                'line-height:30px;' +
                'text-align:center;' +
                'font-weight:bold;' +
                'border:3px solid white;' +
                'box-shadow:0 1px 5px rgba(0,0,0,0.4);">' +
                photo.number +
                '</div>',
            iconSize: [36, 36],
            iconAnchor: [18, 18]
        });

        L.marker(
            [photo.lat, photo.lng],
            { icon: photoIcon }
        ).addTo(map);

        L.polyline(
            [
                [photo.lat, photo.lng],
                [photo.endLat, photo.endLng]
            ],
            {
                color: '#e53935',
                weight: 5,
                opacity: 0.85
            }
        ).addTo(map);

        const arrowIcon = L.divIcon({
            className: '',
            html:
                '<div style="' +
                'width:0;' +
                'height:0;' +
                'border-left:9px solid transparent;' +
                'border-right:9px solid transparent;' +
                'border-bottom:20px solid #e53935;' +
                'transform:rotate(' + photo.azimuth + 'deg);' +
                'transform-origin:center center;' +
                '"></div>',
            iconSize: [18, 20],
            iconAnchor: [9, 10]
        });

        L.marker(
            [photo.endLat, photo.endLng],
            {
                icon: arrowIcon,
                interactive: false
            }
        ).addTo(map);

        bounds.push([photo.lat, photo.lng]);
        bounds.push([photo.endLat, photo.endLng]);
    });

    if (bounds.length > 1) {
        map.fitBounds(
            bounds,
            { padding: [35, 35] }
        );
    } else if (bounds.length === 1) {
        map.setView(bounds[0], 17);
    } else {
        map.setView([37.5665, 126.9780], 12);
    }

    setTimeout(function() {
        map.invalidateSize();
    }, 300);
    
    const timelineContainer =
        document.getElementById('report-timeline');

    timelineEvents.forEach(function(event) {
        const card = document.createElement('div');
        card.className = 'timeline-event';

        if (event.type === 'photo') {
            card.innerHTML =
                '<div class="timeline-dot timeline-photo-dot"></div>' +
                '<div class="timeline-time">' +
                event.time +
                '</div>' +
                '<div class="timeline-type">📷 사진 촬영 · 사진 ' +
                event.number +
                '</div>' +
                '<div class="timeline-details">' +
                '위치: ' +
                Number(event.lat).toFixed(6) + ', ' +
                Number(event.lng).toFixed(6) +
                '<br>GPS 정확도: ±' +
                event.accuracy + 'm' +
                '<br>촬영 방향: ' +
                Number(event.azimuth).toFixed(1) + '° · ' +
                event.direction +
                '<br>이동 상태: ' +
                event.mode +
                '</div>';
        } else {
            card.innerHTML =
                '<div class="timeline-dot timeline-movement-dot"></div>' +
                '<div class="timeline-time">' +
                event.time +
                '</div>' +
                '<div class="timeline-type">🚶 이동 상태 기록</div>' +
                '<div class="timeline-details">' +
                '상태: ' +
                event.mode +
                '<br>위치: ' +
                Number(event.lat).toFixed(6) + ', ' +
                Number(event.lng).toFixed(6) +
                '<br>GPS 정확도: ±' +
                event.accuracy + 'm' +
                '</div>';
        }

        timelineContainer.appendChild(card);
    });
</script>            

            </body>

            </html>
            """.trimIndent()

        val webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        setContentView(webView)

        webView.loadDataWithBaseURL(
            "https://localhost/",
            html,
            "text/html",
            "UTF-8",
            null
        )
        webView.postDelayed({
            webView.setOnLongClickListener {
                createPdfFromWebView(webView)
                true
            }
        }, 1000)
    }

    private fun calculateEndPoint(
        latitude: Double,
        longitude: Double,
        azimuth: Float,
        distanceMeters: Double
    ): Pair<Double, Double> {

        val earthRadius = 6378137.0
        val bearing = Math.toRadians(azimuth.toDouble())

        val deltaNorth =
            cos(bearing) * distanceMeters

        val deltaEast =
            sin(bearing) * distanceMeters

        val deltaLat =
            deltaNorth / earthRadius

        val deltaLng =
            deltaEast /
                    (earthRadius * cos(Math.toRadians(latitude)))

        return Pair(
            latitude + Math.toDegrees(deltaLat),
            longitude + Math.toDegrees(deltaLng)
        )
    }

    private fun createPdfFromWebView(webView: WebView) {
        val printManager =
            getSystemService(Context.PRINT_SERVICE) as PrintManager

        val printAdapter =
            webView.createPrintDocumentAdapter(
                "NOWHERE_Record_Report"
            )

        printManager.print(
            "NOWHERE 기록 보고서",
            printAdapter,
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()
        )
    }
    private fun directionName(
        azimuth: Float
    ): String {
        return when {
            azimuth >= 337.5f || azimuth < 22.5f -> "북"
            azimuth < 67.5f -> "북동"
            azimuth < 112.5f -> "동"
            azimuth < 157.5f -> "남동"
            azimuth < 202.5f -> "남"
            azimuth < 247.5f -> "남서"
            azimuth < 292.5f -> "서"
            else -> "북서"
        }
    }
    /**
     * 보고서에 사진을 넣을 때 원본 대신 긴 변이 1280px인 사진으로 줄여서 넣는다.
     * 원본을 그대로 넣으면 사진이 많을 때 메모리가 부족해 앱이 꺼진다.
     */
    private fun photoUriToBase64(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)

            // 1) 크기만 먼저 읽는다 (사진 전체를 메모리에 올리지 않음).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = contentResolver.openInputStream(uri) ?: return null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // 2) 필요한 만큼만 줄여서 읽는다.
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= REPORT_PHOTO_MAX_SIDE) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            // 3) 긴 변을 정확히 1280px로 맞추고, 사진 방향(EXIF)대로 돌린다.
            val longSide = maxOf(bitmap.width, bitmap.height)
            val scale = if (longSide > REPORT_PHOTO_MAX_SIDE) {
                REPORT_PHOTO_MAX_SIDE.toFloat() / longSide
            } else {
                1f
            }
            val matrix = Matrix()
            matrix.postScale(scale, scale)
            val rotation = readPhotoRotation(uri)
            if (rotation != 0) matrix.postRotate(rotation.toFloat())

            if (scale != 1f || rotation != 0) {
                val transformed = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (transformed != bitmap) bitmap.recycle()
                bitmap = transformed
            }

            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, REPORT_PHOTO_JPEG_QUALITY, output)
            bitmap.recycle()

            val encoded = Base64.encodeToString(
                output.toByteArray(),
                Base64.NO_WRAP
            )

            "data:image/jpeg;base64,$encoded"
        } catch (e: Throwable) {
            // OutOfMemoryError도 잡아서 사진 한 장 때문에 보고서 전체가 꺼지지 않게 한다.
            null
        }
    }

    /** 사진에 저장된 방향 정보를 읽어 몇 도 돌려야 하는지 알려 준다. */
    private fun readPhotoRotation(uri: Uri): Int {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    companion object {
        /** 보고서에 넣는 사진의 긴 변 최대 크기(px). */
        private const val REPORT_PHOTO_MAX_SIDE = 1280
        private const val REPORT_PHOTO_JPEG_QUALITY = 85
    }
}
