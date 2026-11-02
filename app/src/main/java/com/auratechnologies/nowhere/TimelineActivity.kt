package com.auratechnologies.nowhere

import com.auratechnologies.nowhere.billing.ProGate
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

class TimelineActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProGate.redirectIfLocked(this)) return

        val movementDatabase = MovementDatabase(this)
        val photoDatabase = PhotoDatabase(this)

        val movements = movementDatabase.getAllRecords()
        val photos = photoDatabase.getAll()

        if (movements.isEmpty() && photos.isEmpty()) {
            Toast.makeText(
                this,
                "표시할 시간순 기록이 없습니다.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        val events = mutableListOf<JSONObject>()

        val dateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

        // 이동 기록:
        // 첫 기록 + 이동상태가 변경되는 지점을 Timeline Event로 사용
        var previousMode: String? = null

        movements
            .sortedBy { it.timestamp }
            .forEach { record ->

                if (previousMode == null ||
                    previousMode != record.transportMode
                ) {
                    events.add(
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

                    previousMode = record.transportMode
                }
            }

        // 사진 기록
        photos.forEachIndexed { index, record ->

            events.add(
                JSONObject().apply {
                    put("type", "photo")
                    put("number", index + 1)
                    put("timestamp", record.timestamp)

                    put(
                        "time",
                        dateFormat.format(
                            Date(record.timestamp)
                        )
                    )

                    put("lat", record.latitude)
                    put("lng", record.longitude)

                    put(
                        "accuracy",
                        record.locationAccuracy.toInt()
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
                        "mode",
                        record.transportMode
                    )
                }
            )
        }

        // 모든 Event를 timestamp 기준으로 통합 정렬
        events.sortBy {
            it.getLong("timestamp")
        }

        val eventArray = JSONArray()

        events.forEach {
            eventArray.put(it)
        }

        webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = WebViewClient()

        setContentView(webView)

        val html = """
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
                        background:#f5f6fa;
                        font-family:
                            -apple-system,
                            BlinkMacSystemFont,
                            "Segoe UI",
                            sans-serif;
                        color:#202124;
                    }

                    .header {
                        position:sticky;
                        top:0;
                        z-index:10;
                        background:#ffffff;
                        padding:20px 18px 16px;
                        box-shadow:
                            0 2px 8px rgba(0,0,0,0.10);
                    }

                    .title {
                        font-size:23px;
                        font-weight:800;
                    }

                    .summary {
                        margin-top:6px;
                        font-size:14px;
                        color:#666;
                    }

                    .timeline {
                        position:relative;
                        padding:
                            22px 16px
                            35px 54px;
                    }

                    .timeline::before {
                        content:"";
                        position:absolute;
                        left:28px;
                        top:0;
                        bottom:0;
                        width:3px;
                        background:#d7dbe7;
                    }

                    .event {
                        position:relative;
                        background:white;
                        border-radius:14px;
                        padding:14px 15px;
                        margin-bottom:18px;
                        box-shadow:
                            0 2px 8px
                            rgba(0,0,0,0.08);
                    }

                    .dot {
                        position:absolute;
                        left:-39px;
                        top:18px;
                        width:18px;
                        height:18px;
                        border-radius:50%;
                        border:3px solid white;
                        box-shadow:
                            0 1px 4px
                            rgba(0,0,0,0.30);
                    }

                    .movement-dot {
                        background:#1976d2;
                    }

                    .photo-dot {
                        background:#e53935;
                    }

                    .time {
                        font-weight:700;
                        font-size:15px;
                    }

                    .type {
                        margin-top:6px;
                        font-size:17px;
                        font-weight:800;
                    }

                    .details {
                        margin-top:8px;
                        font-size:14px;
                        line-height:1.6;
                        color:#555;
                    }

                    .photo-number {
                        display:inline-block;
                        background:#3949ab;
                        color:white;
                        border-radius:14px;
                        padding:2px 9px;
                        margin-left:5px;
                        font-size:13px;
                    }

                </style>

            </head>

            <body>

                <div class="header">

                    <div class="title">
                        ⏱ 시간순 기록
                    </div>

                    <div class="summary">
                        이동 기록과 사진 기록을
                        하나의 시간축으로 표시합니다.
                    </div>

                </div>

                <div
                    id="timeline"
                    class="timeline">
                </div>

                <script>

                    const events = $eventArray;

                    const timeline =
                        document.getElementById(
                            'timeline'
                        );

                    events.forEach(function(event) {

                        const card =
                            document.createElement(
                                'div'
                            );

                        card.className = 'event';

                        if (event.type === 'photo') {

                            card.innerHTML =
                                '<div class="dot photo-dot"></div>' +

                                '<div class="time">' +
                                event.time +
                                '</div>' +

                                '<div class="type">' +
                                '📷 사진 촬영' +
                                '<span class="photo-number">' +
                                '사진 ' +
                                event.number +
                                '</span>' +
                                '</div>' +

                                '<div class="details">' +

                                '위치: ' +
                                Number(event.lat).toFixed(6) +
                                ', ' +
                                Number(event.lng).toFixed(6) +
                                '<br>' +

                                'GPS 정확도: ±' +
                                event.accuracy +
                                'm<br>' +

                                '촬영방향: ' +
                                event.azimuth +
                                '° · ' +
                                event.direction +
                                '<br>' +

                                '이동상태: ' +
                                event.mode +

                                '</div>';

                        } else {

                            card.innerHTML =
                                '<div class="dot movement-dot"></div>' +

                                '<div class="time">' +
                                event.time +
                                '</div>' +

                                '<div class="type">' +
                                '🚶 이동 상태 기록' +
                                '</div>' +

                                '<div class="details">' +

                                '상태: ' +
                                event.mode +
                                '<br>' +

                                '위치: ' +
                                Number(event.lat).toFixed(6) +
                                ', ' +
                                Number(event.lng).toFixed(6) +
                                '<br>' +

                                'GPS 정확도: ±' +
                                event.accuracy +
                                'm' +

                                '</div>';
                        }

                        timeline.appendChild(card);
                    });

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

    private fun directionName(
        azimuth: Float
    ): String {

        return when {

            azimuth >= 337.5f ||
                    azimuth < 22.5f -> "북"

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