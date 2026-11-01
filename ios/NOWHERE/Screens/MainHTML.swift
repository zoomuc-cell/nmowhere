// Android MainActivity.kt 의 홈 화면 HTML을 그대로 옮긴 것.
// 화면 모양을 바꿀 때는 안드로이드 쪽과 함께 바꿔 주세요.
// iOS WebView는 viewport 지정이 없으면 화면을 980px 폭으로 축소해 보여주므로
// 안드로이드 WebView와 같은 크기로 보이도록 viewport 한 줄만 추가했다.

enum MainHTML {
    static let html = #"""
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
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

        padding: 18px 14px;

        text-align: left;

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
"""#
}
