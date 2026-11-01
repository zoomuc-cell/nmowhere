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
///
/// 정지/도보/차량은 iOS의 CMMotionActivityManager가 판단한다
/// (Android는 Google Activity Recognition API).
/// 예전에는 가속도·자이로 값과 GPS 속도로 직접 추정해서, 가만히 있어도
/// GPS 위치가 흔들리면 '도보'로 나오는 문제가 있었다.
///
/// 엘리베이터/낙하는 활동 인식에 없으므로 기존처럼 기압계와 가속도 센서로
/// 감지하고, 잠시(specialModeHold) 동안만 우선 표시한다.
final class MovementDetector {

    private let motionManager = CMMotionManager()
    private let altimeter = CMAltimeter()
    private let activityManager = CMMotionActivityManager()
    private var activityUpdatesRunning = false

    // 센서 데이터 저장 (엘리베이터/낙하 감지용)
    private var gyroData: [Double] = [0, 0, 0]   // x, y, z 회전 (rad/s)
    private var accelData: [Double] = [0, 0, 0]  // x, y, z 가속도 (m/s²)
    private var currentPressure: Double = 0      // hPa
    private var lastPressure: Double = 0

    private(set) var currentMode: TransportMode = .STATIONARY

    // 활동 인식이 판단한 기본 상태 (정지/도보/차량)
    private var activityMode: TransportMode = .STATIONARY

    // 활동 인식 결과를 한 번이라도 받았는지.
    // 받은 적이 없을 때만(권한 거부, 미지원 기기 등) GPS 속도로 대신 판단한다.
    private var hasActivityResult = false

    // 엘리베이터/낙하 감지 시 잠시 우선 표시
    private var specialMode: TransportMode?
    private var specialModeUntil = Date.distantPast

    private var onModeChanged: ((TransportMode) -> Void)?

    // Android SENSOR_DELAY_NORMAL ≈ 200ms
    private let updateInterval: TimeInterval = 0.2
    private let standardGravity = 9.80665
    private let specialModeHold: TimeInterval = 5
    private let fallbackMaxAccuracy: CLLocationAccuracy = 20

    func startDetection(_ callback: @escaping (TransportMode) -> Void) {
        onModeChanged = callback

        if motionManager.isGyroAvailable {
            motionManager.gyroUpdateInterval = updateInterval
            motionManager.startGyroUpdates(to: .main) { [weak self] data, _ in
                guard let self = self, let rate = data?.rotationRate else { return }
                self.gyroData = [rate.x, rate.y, rate.z]
                self.analyzeSpecialMovement()
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
                self.analyzeSpecialMovement()
            }
        }

        if CMAltimeter.isRelativeAltitudeAvailable() {
            altimeter.startRelativeAltitudeUpdates(to: .main) { [weak self] data, _ in
                guard let self = self, let data = data else { return }
                let pressureHPa = data.pressure.doubleValue * 10 // kPa → hPa
                // 첫 값은 비교 대상이 없으므로 변화량 0으로 시작
                self.lastPressure = self.currentPressure == 0 ? pressureHPa : self.currentPressure
                self.currentPressure = pressureHPa
                self.analyzeSpecialMovement()
            }
        }

        startActivityRecognition()
    }

    func stopDetection() {
        motionManager.stopGyroUpdates()
        motionManager.stopAccelerometerUpdates()
        altimeter.stopRelativeAltitudeUpdates()
        if activityUpdatesRunning {
            activityManager.stopActivityUpdates()
            activityUpdatesRunning = false
        }
    }

    // MARK: - CMMotionActivityManager

    private func startActivityRecognition() {
        guard !activityUpdatesRunning else { return }
        guard CMMotionActivityManager.isActivityAvailable() else {
            NSLog("NOWHERE 이 기기는 활동 인식을 지원하지 않아 GPS 속도로만 이동 상태를 판단합니다.")
            return
        }
        switch CMMotionActivityManager.authorizationStatus() {
        case .denied, .restricted:
            NSLog("NOWHERE 동작 및 피트니스 권한이 없어 GPS 속도로만 이동 상태를 판단합니다.")
            return
        default:
            break // 아직 묻지 않았으면 처음 시작할 때 iOS가 권한을 묻는다
        }

        activityUpdatesRunning = true
        activityManager.startActivityUpdates(to: .main) { [weak self] activity in
            guard let self = self, let activity = activity else { return }
            self.handleActivity(activity)
        }
    }

    private func handleActivity(_ activity: CMMotionActivity) {
        // 확신이 낮은 결과는 무시하고 이전 상태를 유지한다
        guard activity.confidence != .low else { return }

        let mode: TransportMode
        if activity.walking || activity.running {
            mode = .WALKING
        } else if activity.automotive || activity.cycling {
            mode = .VEHICLE
        } else if activity.stationary {
            mode = .STATIONARY
        } else {
            return // unknown: 이전 상태 유지
        }

        hasActivityResult = true
        activityMode = mode
        NSLog("NOWHERE 활동 인식: %@ (confidence=%ld)", mode.rawValue, activity.confidence.rawValue)
        publishMode()
    }

    // MARK: - 엘리베이터/낙하 (센서)

    private func analyzeSpecialMovement() {
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

        var detected: TransportMode?
        if altitudeChange < -3 && accelMagnitude > 15 {
            // 낙하 감지 (급격한 하강 + 높은 가속도)
            detected = .FALLING
        } else if abs(altitudeChange) > 2 && gyroMagnitude < 0.5 {
            // 엘리베이터 감지 (급격한 고도 변화 + 낮은 자이로 회전)
            detected = .ELEVATOR
        }

        if let detected = detected {
            specialMode = detected
            specialModeUntil = Date().addingTimeInterval(specialModeHold)
        }
        publishMode()
    }

    // 기압 → 고도 변화 계산 (간략 공식: 1 hPa ≈ 8.3m)
    private func calculateAltitudeChange(_ pressureChange: Double) -> Double {
        return -pressureChange * 8.3
    }

    // MARK: - GPS 속도 (활동 인식을 못 쓸 때만)

    func updateSpeed(_ location: CLLocation) {
        // 활동 인식 결과가 있으면 그 판단을 그대로 쓴다.
        // (GPS 속도는 가만히 있어도 위치가 튀면서 몇 km/h로 나올 수 있다)
        if hasActivityResult { return }

        let speedKmh = location.speed * 3.6 // m/s → km/h (속도를 모르면 음수)
        let reliable = location.horizontalAccuracy >= 0 && location.horizontalAccuracy <= fallbackMaxAccuracy

        if location.speed < 0 || speedKmh < 2 {
            activityMode = .STATIONARY
        } else if !reliable {
            // 정확도가 나쁘면 판단을 바꾸지 않는다
        } else if speedKmh <= 8 {
            activityMode = .WALKING
        } else if speedKmh > 20 {
            activityMode = .VEHICLE
        }
        publishMode()
    }

    // MARK: - 최종 상태 결정

    private func publishMode() {
        let mode: TransportMode
        if let special = specialMode, Date() < specialModeUntil {
            mode = special
        } else {
            specialMode = nil
            mode = activityMode
        }

        if mode != currentMode {
            currentMode = mode
            onModeChanged?(currentMode)
        }
    }
}
