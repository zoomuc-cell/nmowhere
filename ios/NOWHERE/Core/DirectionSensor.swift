import Foundation
import CoreLocation
import CoreMotion

// Android SensorManager.SENSOR_STATUS_* 값과 같은 숫자를 쓴다.
enum DirectionAccuracy {
    static let unreliable = 0
    static let low = 1
    static let medium = 2
    static let high = 3
}

/// Android DirectionSensor.kt 대응.
/// Android는 회전 벡터 센서(자기 북쪽 기준)로 방위각을 구한다.
/// iOS는 CoreLocation 나침반의 자기 북쪽 방위각(magneticHeading)을 쓴다.
///
/// CoreLocation 방위각은 headingOrientation 이 가리키는 쪽(화면 위쪽)을 기준으로 잰다.
/// 세로로 고정해 두면 가로로 들고 찍을 때 약 90° 틀어지므로,
/// 가속도 센서로 폰을 어떻게 들고 있는지 보고 headingOrientation 을 맞춰 준다.
/// (화면 회전 잠금을 켜 둬도 동작하도록 UIDevice 방향 대신 중력 방향을 직접 본다.)
final class DirectionSensor: NSObject, CLLocationManagerDelegate {

    private let manager = CLLocationManager()
    private let motionManager = CMMotionManager()

    private(set) var azimuth: Double = 0
    private(set) var accuracy: Int = DirectionAccuracy.unreliable
    private(set) var hasReading = false

    override init() {
        super.init()
        manager.delegate = self
        manager.headingFilter = 1
        manager.headingOrientation = .portrait // 시작값. 이후 기기 방향에 맞춰 바뀐다.
    }

    func start() {
        guard CLLocationManager.headingAvailable() else { return }
        manager.startUpdatingHeading()
        startOrientationTracking()
    }

    func stop() {
        manager.stopUpdatingHeading()
        motionManager.stopAccelerometerUpdates()
    }

    private func startOrientationTracking() {
        guard motionManager.isAccelerometerAvailable, !motionManager.isAccelerometerActive else { return }
        motionManager.accelerometerUpdateInterval = 0.2
        motionManager.startAccelerometerUpdates(to: .main) { [weak self] data, _ in
            guard let self = self, let a = data?.acceleration else { return }
            guard let orientation = DirectionSensor.deviceOrientation(gravityX: a.x, gravityY: a.y, gravityZ: a.z,
                                                                       current: self.manager.headingOrientation) else { return }
            if orientation != self.manager.headingOrientation {
                self.manager.headingOrientation = orientation
            }
        }
    }

    /// 중력 방향으로 폰을 세로/가로 중 어떻게 들고 있는지 판단한다.
    /// 바닥에 눕혀 놓은 것처럼 판단하기 어려우면 nil (이전 방향 유지).
    static func deviceOrientation(gravityX x: Double, gravityY y: Double, gravityZ z: Double,
                                  current: CLDeviceOrientation) -> CLDeviceOrientation? {
        let ax = abs(x), ay = abs(y)
        // 거의 평평하게 눕혀 있으면 세로/가로 구분이 의미 없다.
        if max(ax, ay) < 0.35 || abs(z) > 0.9 { return nil }

        let candidate: CLDeviceOrientation
        if ax > ay {
            // 기기 오른쪽(+x)이 위로 가면 landscapeLeft (홈 버튼/아래쪽이 오른쪽)
            candidate = x < 0 ? .landscapeLeft : .landscapeRight
        } else {
            candidate = y < 0 ? .portrait : .portraitUpsideDown
        }

        // 45° 근처에서 왔다갔다 하지 않도록, 방향을 바꿀 때는 확실히 기울었을 때만 바꾼다.
        if candidate != current && abs(ax - ay) < 0.2 { return nil }
        return candidate
    }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        guard newHeading.headingAccuracy >= 0 else {
            accuracy = DirectionAccuracy.unreliable
            return
        }

        var degrees = newHeading.magneticHeading
        if degrees < 0 { degrees += 360 }

        azimuth = degrees
        accuracy = DirectionSensor.accuracyStatus(forDegrees: newHeading.headingAccuracy)
        hasReading = true
    }

    func locationManagerShouldDisplayHeadingCalibration(_ manager: CLLocationManager) -> Bool {
        return true
    }

    /// iOS는 오차를 각도로 알려주므로 Android의 정확도 4단계로 바꾼다.
    static func accuracyStatus(forDegrees error: Double) -> Int {
        if error < 0 { return DirectionAccuracy.unreliable }
        if error <= 15 { return DirectionAccuracy.high }
        if error <= 30 { return DirectionAccuracy.medium }
        if error <= 45 { return DirectionAccuracy.low }
        return DirectionAccuracy.unreliable
    }

    func getDirectionName() -> String {
        return Direction.name(azimuth)
    }
}

enum Direction {
    static func name(_ azimuth: Double) -> String {
        if azimuth >= 337.5 || azimuth < 22.5 { return "북" }
        if azimuth < 67.5 { return "북동" }
        if azimuth < 112.5 { return "동" }
        if azimuth < 157.5 { return "남동" }
        if azimuth < 202.5 { return "남" }
        if azimuth < 247.5 { return "남서" }
        if azimuth < 292.5 { return "서" }
        return "북서"
    }

    static func accuracyText(_ accuracy: Int) -> String {
        switch accuracy {
        case DirectionAccuracy.high: return "높음"
        case DirectionAccuracy.medium: return "보통"
        case DirectionAccuracy.low: return "낮음"
        default: return "신뢰도 낮음"
        }
    }

    /// 촬영 방향 화살표 끝점 계산 (Android calculateEndPoint 대응)
    static func endPoint(latitude: Double, longitude: Double, azimuth: Double, distanceMeters: Double) -> (Double, Double) {
        let earthRadius = 6378137.0
        let bearing = azimuth * .pi / 180

        let deltaNorth = cos(bearing) * distanceMeters
        let deltaEast = sin(bearing) * distanceMeters

        let deltaLat = deltaNorth / earthRadius
        let deltaLng = deltaEast / (earthRadius * cos(latitude * .pi / 180))

        return (
            latitude + deltaLat * 180 / .pi,
            longitude + deltaLng * 180 / .pi
        )
    }
}
