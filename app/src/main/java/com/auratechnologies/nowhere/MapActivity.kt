package com.auratechnologies.nowhere

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject

class MapActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var database: MovementDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // 데이터베이스 초기화
            database = MovementDatabase(this)

            // 데이터 로드
            val records = database.getAllRecords()

            // 데이터가 없으면 안내 메시지
            if (records.isEmpty()) {
                Toast.makeText(this, "아직 이동 기록이 없습니다.\n잠시 이동한 후 다시 시도하세요.", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            Toast.makeText(this, "${records.size}개의 위치 기록을 불러왔습니다", Toast.LENGTH_SHORT).show()

            // WebView 생성 및 설정
            webView = WebView(this)
            setContentView(webView)

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
            }

            webView.webViewClient = WebViewClient()
            webView.addJavascriptInterface(MapBridge(), "Android")

            // OpenStreetMap 로드
            loadOpenStreetMap()

        } catch (e: Exception) {
            Log.e("MapActivity", "Error: ${e.message}", e)
            Toast.makeText(this, "오류 발생: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun loadOpenStreetMap() {
        val html = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    ${LeafletAssets.head(this@MapActivity)}
    <style>
        body { margin: 0; padding: 0; }
        #map { width: 100%; height: 100vh; }
        .legend { 
            background: white; 
            padding: 10px; 
            border-radius: 5px;
            box-shadow: 0 2px 5px rgba(0,0,0,0.3);
        }
        .legend-item { margin: 5px 0; }
        .legend-color { 
            display: inline-block; 
            width: 20px; 
            height: 3px; 
            margin-right: 5px; 
        }
    </style>
</head>
<body>
    <div id="map"></div>
    <script>
        var map = L.map('map').setView([37.5665, 126.9780], 13);
        
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© OpenStreetMap',
            maxZoom: 19
        }).addTo(map);

        var pathColors = {
            'WALKING': '#4285F4',
            'VEHICLE': '#EA4335',
            'ELEVATOR': '#34A853',
            'FALLING': '#FBBC04',
            'STATIONARY': '#9E9E9E'
        };

        var legend = L.control({position: 'bottomright'});
        legend.onAdd = function(map) {
            var div = L.DomUtil.create('div', 'legend');
            div.innerHTML = '<h4>이동 수단</h4>';
            for (var mode in pathColors) {
                div.innerHTML += '<div class="legend-item">' +
                    '<span class="legend-color" style="background:' + pathColors[mode] + '"></span>' +
                    mode + '</div>';
            }
            return div;
        };
        legend.addTo(map);

        function drawPath(records) {
            var data = JSON.parse(records);
            
            if (data.length === 0) {
                alert('기록된 경로가 없습니다.');
                return;
            }

            var bounds = [];
            var currentMode = data[0].mode;
            var currentSegment = [];

            for (var i = 0; i < data.length; i++) {
                var point = [data[i].lat, data[i].lng];
                bounds.push(point);
                
                if (data[i].mode === currentMode) {
                    currentSegment.push(point);
                } else {
                    if (currentSegment.length > 1) {
                        L.polyline(currentSegment, {
                            color: pathColors[currentMode] || '#000000',
                            weight: 4,
                            opacity: 0.7
                        }).addTo(map);
                    }
                    
                    currentMode = data[i].mode;
                    currentSegment = [point];
                }

                if (i === 0) {
                    L.marker(point).addTo(map)
                        .bindPopup('시작: ' + new Date(data[i].timestamp).toLocaleString());
                } else if (i === data.length - 1) {
                    L.marker(point).addTo(map)
                        .bindPopup('종료: ' + new Date(data[i].timestamp).toLocaleString());
                }
            }

            if (currentSegment.length > 1) {
                L.polyline(currentSegment, {
                    color: pathColors[currentMode] || '#000000',
                    weight: 4,
                    opacity: 0.7
                }).addTo(map);
            }

            if (bounds.length > 0) {
                map.fitBounds(bounds);
            }
        }

        window.loadPathData = drawPath;
    </script>
</body>
</html>
        """

        webView.loadDataWithBaseURL("https://nowhere.app", html, "text/html", "UTF-8", null)

        webView.postDelayed({
            loadPathData()
        }, 1000)
    }

    private fun loadPathData() {
        val records = database.getRecentRecords(1000)

        val jsonArray = JSONArray()
        for (record in records) {
            val obj = JSONObject().apply {
                put("lat", record.latitude)
                put("lng", record.longitude)
                put("mode", record.transportMode)
                put("timestamp", record.timestamp)
                put("speed", record.speed)
            }
            jsonArray.put(obj)
        }

        val jsonString = jsonArray.toString()
        webView.evaluateJavascript("window.loadPathData('$jsonString');", null)
    }

    inner class MapBridge {
        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MapActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
