import Foundation
import CoreLocation

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
final class DirectionSensor: NSObject, CLLocationManagerDelegate {

    private let manager = CLLocationManager()

    private(set) var azimuth: Double = 0
    private(set) var accuracy: Int = DirectionAccuracy.unreliable
    private(set) var hasReading = false

    override init() {
        super.init()
        manager.delegate = self
        manager.headingFilter = 1
        manager.headingOrientation = .portrait
    }

    func start() {
        guard CLLocationManager.headingAvailable() else { return }
        manager.startUpdatingHeading()
    }

    func stop() {
        manager.stopUpdatingHeading()
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
