// 안드로이드 각 Activity 안에 있던 HTML을 그대로 옮긴 것.
// 값이 들어가는 자리(Kotlin의 ${...})만 Swift의 \#(...)로 바꿨다.
// 화면 모양을 바꿀 때는 안드로이드 쪽과 함께 바꿔 주세요.

import Foundation

/// MapActivity.kt
enum MapHTML {
    static let html = #"""
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
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
"""#
}

/// CurrentLocationActivity.kt
enum CurrentLocationHTML {
    static func html(latitude: Double, longitude: Double, altitude: Double,
                     accuracy: Double, azimuth: Double, movementMode: String) -> String {
        return #"""
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport"
          content="width=device-width, initial-scale=1.0">

    <link
        rel="stylesheet"
        href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
    />

    <script
        src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js">
    </script>

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
        \#(Format.fixed(latitude, 6))
        <br>

        📍 경도:
        \#(Format.fixed(longitude, 6))
        <br>

        고도:
        \#(Format.fixed(altitude, 1)) m
        <br>

        <span class="accuracy">
            GPS 정확도: ±\#(Format.int(accuracy)) m
        </span>
        <br>

        방향:
        \#(Format.fixed(azimuth, 1))°
        <br>

        이동 상태:
        \#(movementMode)
    </div>

    <script>
        const lat = \#(latitude);
        const lng = \#(longitude);
        const accuracy = \#(accuracy);

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
"""#
    }
}

/// PhotoMapActivity.kt
enum PhotoMapHTML {
    static func html(count: Int, photoData: String, centerLat: Double, centerLng: Double) -> String {
        return #"""
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport"
          content="width=device-width,
          initial-scale=1.0,
          maximum-scale=1.0">

    <link rel="stylesheet"
          href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>

    <script
        src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js">
    </script>

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
            background: #e53935;
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
        📍 사진 촬영 위치 · \#(count)장
    </div>

    <div id="map"></div>

    <script>

        const photos = \#(photoData);

        const map = L.map('map').setView(
            [\#(centerLat), \#(centerLng)],
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
                { icon: icon }
            ).addTo(map);

            marker.bindPopup(
                '<b>📷 사진 ' + photo.number + '</b><br><br>' +
                '촬영시각: ' + photo.time + '<br>' +
                '위도: ' + photo.lat.toFixed(6) + '<br>' +
                '경도: ' + photo.lng.toFixed(6) + '<br>' +
                'GPS 정확도: ±' + photo.accuracy + 'm<br>' +
                '촬영방향: ' + photo.azimuth +
                '° · ' + photo.direction + '<br>' +
                '이동상태: ' + photo.movement
            );

            bounds.push([photo.lat, photo.lng]);
        });

        if (bounds.length > 1) {
            map.fitBounds(bounds, {
                padding: [60, 60]
            });
        }

    </script>
</body>
</html>
"""#
    }
}

/// PhotoDirectionMapActivity.kt
enum PhotoDirectionMapHTML {
    static func html(count: Int, photoData: String, centerLat: Double, centerLng: Double) -> String {
        return #"""
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">

    <meta name="viewport"
          content="width=device-width,
          initial-scale=1.0,
          maximum-scale=1.0">

    <link rel="stylesheet"
          href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>

    <script
        src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js">
    </script>

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
        🧭 사진 촬영 방향 · \#(count)장
    </div>

    <div id="map"></div>

    <script>

        const photos = \#(photoData);

        const map = L.map('map').setView(
            [\#(centerLat), \#(centerLng)],
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
"""#
    }
}

/// IntegratedMapActivity.kt
enum IntegratedMapHTML {
    static func html(movementData: String, photoData: String, centerLat: Double, centerLng: Double) -> String {
        return #"""
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">

    <meta name="viewport"
          content="width=device-width,
          initial-scale=1.0,
          maximum-scale=1.0">

    <link rel="stylesheet"
          href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>

    <script
        src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js">
    </script>

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

        const movements = \#(movementData);
        const photos = \#(photoData);

        const map = L.map('map').setView(
            [\#(centerLat), \#(centerLng)],
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
"""#
    }
}

/// TimelineActivity.kt
enum TimelineHTML {
    static func html(eventArray: String) -> String {
        return #"""
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

        const events = \#(eventArray);

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
"""#
    }
}

/// ReportActivity.kt
enum ReportHTML {
    static func photoCard(index: Int, photo: PhotoRecord, photoDataUrl: String) -> String {
        return #"""
<div class="photo-card">

    <img
        src="\#(photoDataUrl)"
        class="report-photo"
    />

    <div class="photo-title">
        📷 사진 \#(index + 1)
    </div>

    <div class="details">
        촬영 시각:
        \#(Format.date(photo.timestamp, "yyyy-MM-dd HH:mm:ss"))
        <br>

        위치:
        \#(Format.fixed(photo.latitude, 6)),
        \#(Format.fixed(photo.longitude, 6))
        <br>

        GPS 정확도:
        ±\#(Format.int(photo.locationAccuracy))m
        <br>

        촬영 방향:
        \#(Format.fixed(photo.azimuth, 1))°
        · \#(Direction.name(photo.azimuth))
        <br>

        이동 상태:
        \#(photo.transportMode)
    </div>
</div>
"""#
    }

    static func html(startText: String, endText: String,
                     movements: [MovementRecord], photos: [PhotoRecord],
                     photoHtml: String, movementData: String,
                     photoMapData: String, timelineData: String) -> String {
        return #"""
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
            \#(startText)
            <br>

            종료:
            \#(endText)
        </div>

        <div class="summary-grid">

            <div class="summary-box">
                <div class="number">
                   \#(movements.count)
                </div>
                <div class="label">
                    이동 위치 기록
                </div>
            </div>

            <div class="summary-box">
                <div class="number">
                   \#(photos.count)
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

        \#(photoHtml)

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
    const movements = \#(movementData);
    const mapPhotos = \#(photoMapData);
    const timelineEvents = \#(timelineData);

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
"""#
    }
}
