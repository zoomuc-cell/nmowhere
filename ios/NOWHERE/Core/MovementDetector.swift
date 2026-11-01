import Foundation
import CoreMotion
import CoreLocation

// 이동 수단 판별 결과 (Android MovementDetector.TransportMode 대응)
enum TransportMode: String {
    case STATIONARY    // 정지
    case WALKING       // 도보
    case VEHICLE       // 차량
    case FALLING       // 낙하
    case ELEVATOR      // 엘리베이터
}

/// Android MovementDetector.kt 대응.
/// 자이로스코프, 가속도계, 기압계 값으로 이동 수단을 판별한다.
/// Android와 같은 단위(rad/s, m/s², hPa)로 맞춰 같은 임계값을 쓴다.
final class MovementDetector {

    private let motionManager = CMMotionManager()
    private let altimeter = CMAltimeter()

    // 센서 데이터 저장
    private var gyroData: [Double] = [0, 0, 0]   // x, y, z 회전 (rad/s)
    private var accelData: [Double] = [0, 0, 0]  // x, y, z 가속도 (m/s²)
    private var currentPressure: Double = 0      // hPa
    private var lastPressure: Double = 0

    private(set) var currentMode: TransportMode = .STATIONARY

    private var onModeChanged: ((TransportMode) -> Void)?

    // Android SENSOR_DELAY_NORMAL ≈ 200ms
    private let updateInterval: TimeInterval = 0.2
    private let standardGravity = 9.80665

    func startDetection(_ callback: @escaping (TransportMode) -> Void) {
        onModeChanged = callback

        if motionManager.isGyroAvailable {
            motionManager.gyroUpdateInterval = updateInterval
            motionManager.startGyroUpdates(to: .main) { [weak self] data, _ in
                guard let self = self, let rate = data?.rotationRate else { return }
                self.gyroData = [rate.x, rate.y, rate.z]
                self.analyzeMovement()
            }
        }

        if motionManager.isAccelerometerAvailable {
            motionManager.accelerometerUpdateInterval = updateInterval
            motionManager.startAccelerometerUpdates(to: .main) { [weak self] data, _ in
                guard let self = self, let a = data?.acceleration else { return }
                // iOS는 g 단위이므로 Android와 같은 m/s²로 변환 (중력 포함)
                self.accelData = [
                    a.x * self.standardGravity,
                    a.y * self.standardGravity,
                    a.z * self.standardGravity
                ]
                self.analyzeMovement()
            }
        }

        if CMAltimeter.isRelativeAltitudeAvailable() {
            altimeter.startRelativeAltitudeUpdates(to: .main) { [weak self] data, _ in
                guard let self = self, let data = data else { return }
                let pressureHPa = data.pressure.doubleValue * 10 // kPa → hPa
                // 첫 값은 비교 대상이 없으므로 변화량 0으로 시작
                self.lastPressure = self.currentPressure == 0 ? pressureHPa : self.currentPressure
                self.currentPressure = pressureHPa
                self.analyzeMovement()
            }
        }
    }

    func stopDetection() {
        motionManager.stopGyroUpdates()
        motionManager.stopAccelerometerUpdates()
        altimeter.stopRelativeAltitudeUpdates()
    }

    private func analyzeMovement() {
        // 자이로스코프 회전 강도 계산
        let gyroMagnitude = sqrt(
            gyroData[0] * gyroData[0] +
            gyroData[1] * gyroData[1] +
            gyroData[2] * gyroData[2]
        )

        // 가속도 크기 계산
        let accelMagnitude = sqrt(
            accelData[0] * accelData[0] +
            accelData[1] * accelData[1] +
            accelData[2] * accelData[2]
        )

        // 기압 변화량 (고도 변화)
        let pressureChange = currentPressure - lastPressure
        let altitudeChange = calculateAltitudeChange(pressureChange)

        // 이동 수단 판별
        let detectedMode: TransportMode
        if altitudeChange < -3 && accelMagnitude > 15 {
            // 낙하 감지 (급격한 하강 + 높은 가속도)
            detectedMode = .FALLING
        } else if abs(altitudeChange) > 2 && gyroMagnitude < 0.5 {
            // 엘리베이터 감지 (급격한 고도 변화 + 낮은 자이로 회전)
            detectedMode = .ELEVATOR
        } else if gyroMagnitude < 1.5 && (10.0...14.0).contains(accelMagnitude) {
            // 차량 이동 (중간 진동 + 낮은 자이로 회전)
            detectedMode = .VEHICLE
        } else if (0.5...3.0).contains(gyroMagnitude) && (8.0...12.0).contains(accelMagnitude) {
            // 도보 이동 (규칙적인 진동 + 중간 자이로 회전)
            detectedMode = .WALKING
        } else {
            // 정지
            detectedMode = .STATIONARY
        }

        // 모드 변경 시 콜백 실행
        if detectedMode != currentMode {
            currentMode = detectedMode
            onModeChanged?(currentMode)
        }
    }

    // 기압 → 고도 변화 계산 (간략 공식: 1 hPa ≈ 8.3m)
    private func calculateAltitudeChange(_ pressureChange: Double) -> Double {
        return -pressureChange * 8.3
    }

    // GPS 속도 기반 보조 판별
    func updateSpeed(_ location: CLLocation) {
        let speedKmh = max(location.speed, 0) * 3.6 // m/s → km/h

        if speedKmh < 1 {
            // 정지 상태 유지
        } else if speedKmh >= 1.0 && speedKmh <= 7.0 && currentMode != .ELEVATOR {
            currentMode = .WALKING
            onModeChanged?(currentMode)
        } else if speedKmh > 15 {
            currentMode = .VEHICLE
            onModeChanged?(currentMode)
        }
    }
}
