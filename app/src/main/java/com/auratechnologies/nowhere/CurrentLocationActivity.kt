package com.auratechnologies.nowhere

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class CurrentLocationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        val altitude = intent.getDoubleExtra("altitude", 0.0)
        val accuracy = intent.getFloatExtra("accuracy", 0f)
        val azimuth = intent.getFloatExtra("azimuth", 0f)
        val movementMode =
            intent.getStringExtra("movementMode") ?: "UNKNOWN"

        val webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport"
                      content="width=device-width, initial-scale=1.0">

                ${LeafletAssets.head(this@CurrentLocationActivity)}

                <style>
                    * {
                        box-sizing: border-box;
                    }

                    body {
                        margin: 0;
                        padding: 18px;
                        font-family: Arial, sans-serif;
                        color: #ffffff;

                        background:
                            linear-gradient(
                                145deg,
                                #66727e,
                                #4d5965 40%,
                                #35414c
                            );

                        min-height: 100vh;
                    }

                    h1 {
                        margin: 10px 0 5px;
                        font-size: 28px;
                    }

                    .subtitle {
                        color: #b9d9ee;
                        font-size: 13px;
                        letter-spacing: 2px;
                        margin-bottom: 18px;
                    }

                    #map {
                        height: 52vh;
                        min-height: 360px;

                        border-radius: 22px;

                        overflow: hidden;

                        border:
                            1px solid rgba(200,225,245,0.45);

                        box-shadow:
                            0 12px 30px rgba(0,0,0,0.25);
                    }

                    .info {
                        margin-top: 16px;
                        padding: 18px;

                        border-radius: 20px;

                        background:
                            rgba(49,62,74,0.78);

                        border:
                            1px solid rgba(190,220,240,0.32);

                        line-height: 1.8;

                        font-size: 15px;
                    }

                    .accuracy {
                        color: #aee8ff;
                        font-weight: bold;
                    }

                    .current-marker {
                        width: 22px;
                        height: 22px;

                        border-radius: 50%;

                        background: #249cff;

                        border: 4px solid white;

                        box-shadow:
                            0 0 0 8px rgba(36,156,255,0.25),
                            0 0 18px rgba(36,156,255,0.85);
                    }
                </style>
            </head>

            <body>

                <h1>현재 위치</h1>

                <div class="subtitle">
                    LIVE LOCATION · NOWHERE
                </div>

                <div id="map"></div>

                <div class="info">
                    📍 위도:
                    ${String.format("%.6f", latitude)}
                    <br>

                    📍 경도:
                    ${String.format("%.6f", longitude)}
                    <br>

                    고도:
                    ${String.format("%.1f", altitude)} m
                    <br>

                    <span class="accuracy">
                        GPS 정확도: ±${accuracy.toInt()} m
                    </span>
                    <br>

                    방향:
                    ${String.format("%.1f", azimuth)}°
                    <br>

                    이동 상태:
                    $movementMode
                </div>

                <script>
                    const lat = $latitude;
                    const lng = $longitude;
                    const accuracy = $accuracy;

                    const map =
                        L.map('map').setView(
                            [lat, lng],
                            18
                        );

                    L.tileLayer(
                        'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
                        {
                            maxZoom: 20,
                            attribution: '© OpenStreetMap'
                        }
                    ).addTo(map);

                    L.circle(
                        [lat, lng],
                        {
                            radius: accuracy,
                            color: '#249cff',
                            weight: 2,
                            fillColor: '#249cff',
                            fillOpacity: 0.13
                        }
                    ).addTo(map);

                    const icon =
                        L.divIcon({
                            className: '',
                            html:
                                '<div class="current-marker"></div>',
                            iconSize: [22,22],
                            iconAnchor: [11,11]
                        });

                    L.marker(
                        [lat,lng],
                        { icon: icon }
                    )
                    .addTo(map)
                    .bindPopup('현재 위치')
                    .openPopup();

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

        setContentView(webView)
    }
}