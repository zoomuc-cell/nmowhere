import UIKit
import CoreLocation
import AVFoundation

/// Android MainActivity.kt 대응: 홈 화면, 위치 추적, 방향 센서, 현장 사진 촬영.
final class MainViewController: WebScreenViewController,
                                CLLocationManagerDelegate,
                                UIImagePickerControllerDelegate,
                                UINavigationControllerDelegate {

    private let movementDetector = MovementDetector()
    private let movementDatabase = MovementDatabase.shared
    private let photoDatabase = PhotoDatabase.shared
    private let directionSensor = DirectionSensor()
    private let locationManager = CLLocationManager()

    private var latestLocation: CLLocation?
    private var isTracking = false

    // 촬영 버튼을 누른 순간의 위치/방향 정보 (Android pendingCapture* 대응)
    private struct PendingCapture {
        let fileName: String
        let timestamp: Int64
        let latitude: Double
        let longitude: Double
        let altitude: Double
        let locationAccuracy: Double
        let azimuth: Double
        let directionAccuracy: Int
        let transportMode: String
    }
    private var pendingCapture: PendingCapture?

    init() {
        super.init(title: "NOWHERE", html: MainHTML.html, baseURL: URL(string: "https://nowhere.app"))
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        // 홈 화면 배경색을 HTML 그라데이션 시작색과 맞춘다.
        view.backgroundColor = UIColor(red: 0x66 / 255, green: 0x72 / 255, blue: 0x7e / 255, alpha: 1)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear

        onBridgeCall = { [weak self] method, _ in
            self?.handleBridgeCall(method)
        }

        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = 5 // setMinUpdateDistanceMeters(5f)
        locationManager.activityType = .other

        directionSensor.start()
        checkAndRequestPermissions()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        navigationController?.setNavigationBarHidden(true, animated: animated)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        navigationController?.setNavigationBarHidden(false, animated: animated)
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        return .lightContent
    }

    // MARK: - HTML 버튼 (Android WebAppInterface 대응)

    private func handleBridgeCall(_ method: String) {
        switch method {
        case "openMapActivity":
            push(Screens.movementMap())
        case "openPhotoRecords":
            push(PhotoRecordsViewController())
        case "openPhotoMap":
            push(Screens.photoMap())
        case "openPhotoDirectionMap":
            push(Screens.photoDirectionMap())
        case "openIntegratedMap":
            push(Screens.integratedMap())
        case "openTimeline":
            push(Screens.timeline())
        case "openReport", "openRecordManagement":
            // Android와 동일하게 보고서 버튼도 저장 기록 관리 화면으로 연결된다.
            push(RecordManagementViewController())
        case "openCurrentLocation":
            openCurrentLocation()
        case "takeEvidencePhoto":
            beginPhotoCapture()
        default:
            break
        }
    }

    private func push(_ controller: UIViewController?) {
        guard let controller = controller else { return }
        navigationController?.pushViewController(controller, animated: true)
    }

    private func openCurrentLocation() {
        guard let location = latestLocation else {
            showStatus("📍 현재 위치를 확인하고 있습니다...")
            return
        }

        let html = CurrentLocationHTML.html(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            altitude: location.altitude,
            accuracy: location.horizontalAccuracy,
            azimuth: directionSensor.azimuth,
            movementMode: movementDetector.currentMode.rawValue
        )
        push(WebScreenViewController(title: "현재 위치", html: html))
    }

    // MARK: - 현장 사진 촬영

    private func beginPhotoCapture() {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            Toast.show("이 기기에서는 카메라를 사용할 수 없습니다.", .long)
            return
        }

        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            break
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted {
                        self?.beginPhotoCapture()
                    } else {
                        Toast.show("사진 촬영을 위해 카메라 권한이 필요합니다.", .long)
                    }
                }
            }
            return
        default:
            Toast.show("사진 촬영을 위해 카메라 권한이 필요합니다.", .long)
            return
        }

        guard let captureLocation = latestLocation else {
            Toast.show("정확한 위치를 수신한 후 다시 촬영해주세요.", .long)
            showStatus("GPS 위치 수신 대기 중...")
            return
        }

        guard directionSensor.hasReading else {
            Toast.show("방향 센서 값을 준비하고 있습니다. 잠시 후 다시 시도해주세요.", .long)
            return
        }

        let now = Storage.nowMillis()
        pendingCapture = PendingCapture(
            fileName: "NOWHERE_\(now).jpg",
            timestamp: now,
            latitude: captureLocation.coordinate.latitude,
            longitude: captureLocation.coordinate.longitude,
            altitude: captureLocation.altitude,
            locationAccuracy: captureLocation.horizontalAccuracy,
            azimuth: directionSensor.azimuth,
            directionAccuracy: directionSensor.accuracy,
            transportMode: movementDetector.currentMode.rawValue
        )

        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.cameraCaptureMode = .photo
        picker.delegate = self
        present(picker, animated: true)
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        pendingCapture = nil
        showStatus("사진 촬영이 취소되었습니다.")
    }

    func imagePickerController(_ picker: UIImagePickerController,
                               didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)

        guard let capture = pendingCapture,
              let image = info[.originalImage] as? UIImage,
              let data = image.jpegData(compressionQuality: 0.9) else {
            pendingCapture = nil
            Toast.show("사진 기록 정보를 완성할 수 없습니다.", .long)
            return
        }
        pendingCapture = nil

        let relativePath = "photos/\(capture.fileName)"
        do {
            try data.write(to: Storage.photoDirectory.appendingPathComponent(capture.fileName), options: .atomic)
        } catch {
            Toast.show("사진 기록 정보를 완성할 수 없습니다.", .long)
            return
        }

        let record = PhotoRecord(
            id: UUID().uuidString.lowercased(),
            photoUri: relativePath,
            timestamp: capture.timestamp,
            latitude: capture.latitude,
            longitude: capture.longitude,
            altitude: capture.altitude,
            locationAccuracy: capture.locationAccuracy,
            azimuth: capture.azimuth,
            directionAccuracy: capture.directionAccuracy,
            transportMode: capture.transportMode
        )
        photoDatabase.add(record)

        let directionText = "\(Format.fixed(record.azimuth, 1))° \(Direction.name(record.azimuth))"

        Toast.show("사진 기록 완료\n방향: \(directionText)\nGPS 정확도: \(Format.int(record.locationAccuracy))m", .long)
        showStatus("📷 사진 \(photoDatabase.getTotalRecords())장 저장 · 방향 \(directionText)")
    }

    // MARK: - 상태 표시

    private func showStatus(_ message: String) {
        let escaped = message
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'", with: "\\'")
            .replacingOccurrences(of: "\n", with: "\\n")
        evaluate("if(typeof updateStatus === 'function') updateStatus('\(escaped)');")
    }

    // MARK: - 위치 권한 및 추적

    private func checkAndRequestPermissions() {
        switch locationManager.authorizationStatus {
        case .notDetermined:
            locationManager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            startTracking()
        default:
            Toast.show("위치 및 활동 권한이 필요합니다.", .long)
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            startTracking()
        case .denied, .restricted:
            Toast.show("위치 및 활동 권한이 필요합니다.", .long)
            showStatus("⚠️ 위치 권한이 필요합니다.")
        default:
            break
        }
    }

    private func startTracking() {
        guard !isTracking else { return }
        isTracking = true

        movementDetector.startDetection { mode in
            NSLog("NOWHERE 이동 수단 감지: %@", mode.rawValue)
        }

        // 먼저 최근에 확보된 위치가 있으면 즉시 사용
        if let location = locationManager.location {
            latestLocation = location
            showStatus("📍 위치 확보됨 · 정확도 \(Format.int(location.horizontalAccuracy))m")
        } else {
            showStatus("🛰️ 현재 위치 확인 중...")
        }

        locationManager.startUpdatingLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last, location.horizontalAccuracy >= 0 else { return }

        latestLocation = location
        movementDetector.updateSpeed(location)
        movementDatabase.addRecord(location: location, mode: movementDetector.currentMode)

        showStatus("📍 위치 확보됨 · 정확도 \(Format.int(location.horizontalAccuracy))m · 이동상태 \(movementDetector.currentMode.rawValue)")
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        NSLog("NOWHERE 위치 오류: %@", error.localizedDescription)
    }
}
