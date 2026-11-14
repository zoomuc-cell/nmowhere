package com.auratechnologies.nowhere

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class IntegratedMapActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var movementDatabase: MovementDatabase
    private lateinit var photoDatabase: PhotoDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        movementDatabase = MovementDatabase(this)
        photoDatabase = PhotoDatabase(this)

        val movements = movementDatabase.getAllRecords()
        val photos = photoDatabase.getAll()

        if (movements.isEmpty() && photos.isEmpty()) {
            Toast.makeText(
                this,
                "표시할 이동 또는 사진 기록이 없습니다.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

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

        val photoData = JSONArray()
        val dateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

        photos.forEachIndexed { index, record ->

            val endPoint = calculateEndPoint(
                record.latitude,
                record.longitude,
                record.azimuth,
                45.0
            )

            photoData.put(
                JSONObject().apply {
                    put("number", index + 1)
                    put("lat", record.latitude)
                    put("lng", record.longitude)
                    put("endLat", endPoint.first)
                    put("endLng", endPoint.second)

                    put(
                        "time",
                        dateFormat.format(Date(record.timestamp))
                    )

                    put("azimuth", record.azimuth)
                    put("direction", directionName(record.azimuth))
                    put("accuracy", record.locationAccuracy.toInt())
                    put("movement", record.transportMode)
                }
            )
        }

        val allLatitudes =
            movements.map { it.latitude } +
                    photos.map { it.latitude }

        val allLongitudes =
            movements.map { it.longitude } +
                    photos.map { it.longitude }

        val centerLat = allLatitudes.average()
        val centerLng = allLongitudes.average()

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">

                <meta name="viewport"
                      content="width=device-width,
                      initial-scale=1.0,
                      maximum-scale=1.0">

                ${LeafletAssets.head(this@IntegratedMapActivity)}

                <style>
                    html, body {
                        width:100%;
                        height:100%;
                        margin:0;
                        padding:0;
                        font-family:sans-serif;
                    }

                    #map {
                        width:100%;
                        height:100%;
                    }

                    .title-box {
                        position:absolute;
                        top:15px;
                        left:50%;
                        transform:translateX(-50%);
                        z-index:1000;
                        background:white;
                        padding:10px 18px;
                        border-radius:12px;
                        box-shadow:0 2px 8px rgba(0,0,0,0.25);
                        font-weight:bold;
                        font-size:16px;
                        white-space:nowrap;
                    }

                    .photo-number {
                        background:#3949ab;
                        color:white;
                        border-radius:50%;
                        width:32px;
                        height:32px;
                        line-height:32px;
                        text-align:center;
                        font-weight:bold;
                        border:3px solid white;
                        box-shadow:0 1px 5px rgba(0,0,0,0.4);
                    }

                    .legend {
                        position:absolute;
                        left:15px;
                        bottom:25px;
                        z-index:1000;
                        background:white;
                        padding:9px 12px;
                        border-radius:10px;
                        box-shadow:0 2px 7px rgba(0,0,0,0.22);
                        font-size:13px;
                        line-height:1.7;
                    }
                </style>
            </head>

            <body>

                <div class="title-box">
                    🌐 통합 기록 지도
                </div>

                <div class="legend">
                    🛣️ 이동 경로<br>
                    🔵 사진 촬영 위치<br>
                    ➜ 촬영 방향
                </div>

                <div id="map"></div>

                <script>

                    const movements = $movementData;
                    const photos = $photoData;

                    const map = L.map('map').setView(
                        [$centerLat, $centerLng],
                        16
                    );

                    L.tileLayer(
                        'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
                        {
                            maxZoom:19,
                            attribution:'&copy; OpenStreetMap contributors'
                        }
                    ).addTo(map);

                    const bounds = [];

                    // 이동 경로
                    if (movements.length > 0) {

                        const routePoints =
                            movements.map(function(record) {
                                bounds.push([
                                    record.lat,
                                    record.lng
                                ]);

                                return [
                                    record.lat,
                                    record.lng
                                ];
                            });

                        L.polyline(
                            routePoints,
                            {
                                color:'#1565c0',
                                weight:5,
                                opacity:0.75
                            }
                        ).addTo(map);
                    }

                    // 사진 위치 + 촬영 방향
                    photos.forEach(function(photo) {

                        const photoIcon = L.divIcon({
                            className:'',
                            html:
                                '<div class="photo-number">' +
                                photo.number +
                                '</div>',
                            iconSize:[38,38],
                            iconAnchor:[19,19]
                        });

                        const marker = L.marker(
                            [photo.lat, photo.lng],
                            {icon:photoIcon}
                        ).addTo(map);

                        L.polyline(
                            [
                                [photo.lat, photo.lng],
                                [photo.endLat, photo.endLng]
                            ],
                            {
                                color:'#e53935',
                                weight:6,
                                opacity:0.85
                            }
                        ).addTo(map);

                        const arrowAngle = photo.azimuth;

                        const arrowIcon = L.divIcon({
                            className:'',
                            html:
                                '<div style="' +
                                'width:0;' +
                                'height:0;' +
                                'border-left:10px solid transparent;' +
                                'border-right:10px solid transparent;' +
                                'border-bottom:22px solid #e53935;' +
                                'transform:rotate(' +
                                arrowAngle +
                                'deg);' +
                                'transform-origin:center center;' +
                                '"></div>',
                            iconSize:[20,22],
                            iconAnchor:[10,11]
                        });

                        L.marker(
                            [photo.endLat, photo.endLng],
                            {
                                icon:arrowIcon,
                                interactive:false
                            }
                        ).addTo(map);

                        marker.bindPopup(
                            '<b>📷 사진 ' +
                            photo.number +
                            '</b><br><br>' +
                            '촬영시각: ' +
                            photo.time +
                            '<br>' +
                            'GPS 정확도: ±' +
                            photo.accuracy +
                            'm<br>' +
                            '촬영방향: ' +
                            Number(photo.azimuth).toFixed(1) +
                            '° · ' +
                            photo.direction +
                            '<br>' +
                            '이동상태: ' +
                            photo.movement
                        );

                        bounds.push([
                            photo.lat,
                            photo.lng
                        ]);

                        bounds.push([
                            photo.endLat,
                            photo.endLng
                        ]);
                    });

                    if (bounds.length > 1) {
                        map.fitBounds(
                            bounds,
                            {padding:[70,70]}
                        );
                    }

                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(
            "https://localhost/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun calculateEndPoint(
        latitude: Double,
        longitude: Double,
        azimuth: Float,
        distanceMeters: Double
    ): Pair<Double, Double> {

        val earthRadius = 6378137.0
        val bearing =
            Math.toRadians(azimuth.toDouble())

        val deltaNorth =
            cos(bearing) * distanceMeters

        val deltaEast =
            sin(bearing) * distanceMeters

        val deltaLat =
            deltaNorth / earthRadius

        val deltaLng =
            deltaEast /
                    (
                            earthRadius *
                                    cos(Math.toRadians(latitude))
                            )

        return Pair(
            latitude + Math.toDegrees(deltaLat),
            longitude + Math.toDegrees(deltaLng)
        )
    }

    private fun directionName(azimuth: Float): String {
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
}