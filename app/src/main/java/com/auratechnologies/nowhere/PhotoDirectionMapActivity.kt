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

class PhotoDirectionMapActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var photoDatabase: PhotoDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        photoDatabase = PhotoDatabase(this)
        val records = photoDatabase.getAll()

        if (records.isEmpty()) {
            Toast.makeText(
                this,
                "저장된 촬영 사진이 없습니다.",
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

        val photoData = JSONArray()
        val dateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

        records.forEachIndexed { index, record ->

            val endPoint = calculateEndPoint(
                record.latitude,
                record.longitude,
                record.azimuth,
                45.0
            )

            val item = JSONObject().apply {
                put("number", index + 1)
                put("lat", record.latitude)
                put("lng", record.longitude)
                put("endLat", endPoint.first)
                put("endLng", endPoint.second)
                put(
                    "time",
                    dateFormat.format(Date(record.timestamp))
                )
                put(
                    "azimuth",
                    String.format(
                        Locale.US,
                        "%.1f",
                        record.azimuth
                    )
                )
                put(
                    "direction",
                    directionName(record.azimuth)
                )
                put(
                    "accuracy",
                    record.locationAccuracy.toInt()
                )
            }

            photoData.put(item)
        }

        val centerLat = records.map { it.latitude }.average()
        val centerLng = records.map { it.longitude }.average()

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">

                <meta name="viewport"
                      content="width=device-width,
                      initial-scale=1.0,
                      maximum-scale=1.0">

                ${LeafletAssets.head(this@PhotoDirectionMapActivity)}

                <style>
                    html, body {
                        width: 100%;
                        height: 100%;
                        margin: 0;
                        padding: 0;
                        font-family: sans-serif;
                    }

                    #map {
                        width: 100%;
                        height: 100%;
                    }

                    .title-box {
                        position: absolute;
                        top: 15px;
                        left: 50%;
                        transform: translateX(-50%);
                        z-index: 1000;
                        background: white;
                        padding: 10px 18px;
                        border-radius: 12px;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.25);
                        font-weight: bold;
                        font-size: 16px;
                        white-space: nowrap;
                    }

                    .photo-number {
                        background: #3949ab;
                        color: white;
                        border-radius: 50%;
                        width: 32px;
                        height: 32px;
                        line-height: 32px;
                        text-align: center;
                        font-weight: bold;
                        border: 3px solid white;
                        box-shadow: 0 1px 5px rgba(0,0,0,0.4);
                    }
                </style>
            </head>

            <body>

                <div class="title-box">
                    🧭 사진 촬영 방향 · ${records.size}장
                </div>

                <div id="map"></div>

                <script>

                    const photos = $photoData;

                    const map = L.map('map').setView(
                        [$centerLat, $centerLng],
                        16
                    );

                    L.tileLayer(
                        'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
                        {
                            maxZoom: 19,
                            attribution: '&copy; OpenStreetMap contributors'
                        }
                    ).addTo(map);

                    const bounds = [];

                    photos.forEach(function(photo) {

                        const icon = L.divIcon({
                            className: '',
                            html:
                                '<div class="photo-number">' +
                                photo.number +
                                '</div>',
                            iconSize: [38, 38],
                            iconAnchor: [19, 19]
                        });

                        const marker = L.marker(
                            [photo.lat, photo.lng],
                            {icon: icon}
                        ).addTo(map);

                        const directionLine = L.polyline(
                            [
                                [photo.lat, photo.lng],
                                [photo.endLat, photo.endLng]
                            ],
                            {
                                color: '#e53935',
                                weight: 6,
                                opacity: 0.85
                            }
                        ).addTo(map);

                        const arrowAngle = photo.azimuth;

                        const arrowIcon = L.divIcon({
                            className: '',
                            html:
                                '<div style="' +
                                'width:0;' +
                                'height:0;' +
                                'border-left:10px solid transparent;' +
                                'border-right:10px solid transparent;' +
                                'border-bottom:22px solid #e53935;' +
                                'transform:rotate(' + arrowAngle + 'deg);' +
                                'transform-origin:center center;' +
                                '"></div>',
                            iconSize: [20, 22],
                            iconAnchor: [10, 11]
                        });

                        L.marker(
                            [photo.endLat, photo.endLng],
                            {
                                icon: arrowIcon,
                                interactive: false
                            }
                        ).addTo(map);
                       
                        marker.bindPopup(
                            '<b>🧭 사진 ' + photo.number + '</b><br><br>' +
                            '촬영시각: ' + photo.time + '<br>' +
                            '촬영방향: ' + photo.azimuth +
                            '° · ' + photo.direction + '<br>' +
                            'GPS 정확도: ±' + photo.accuracy + 'm'
                        );

                        bounds.push([photo.lat, photo.lng]);
                        bounds.push([photo.endLat, photo.endLng]);
                    });

                    if (bounds.length > 1) {
                        map.fitBounds(bounds, {
                            padding: [70, 70]
                        });
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
        val bearing = Math.toRadians(azimuth.toDouble())

        val deltaNorth = cos(bearing) * distanceMeters
        val deltaEast = sin(bearing) * distanceMeters

        val deltaLat =
            deltaNorth / earthRadius

        val deltaLng =
            deltaEast /
                    (earthRadius * cos(Math.toRadians(latitude)))

        val endLat =
            latitude + Math.toDegrees(deltaLat)

        val endLng =
            longitude + Math.toDegrees(deltaLng)

        return Pair(endLat, endLng)
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