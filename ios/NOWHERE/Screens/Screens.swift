import UIKit

/// 지도·시간순 화면을 만든다. 기록이 없으면 Android처럼 안내 메시지만 띄우고 nil을 돌려준다.
enum Screens {

    private static let dateTimePattern = "yyyy-MM-dd HH:mm:ss"

    // MARK: - 이동 경로 (MapActivity.kt)

    static func movementMap() -> UIViewController? {
        let database = MovementDatabase.shared
        let records = database.getAllRecords()

        if records.isEmpty {
            Toast.show("아직 이동 기록이 없습니다.\n잠시 이동한 후 다시 시도하세요.", .long)
            return nil
        }

        Toast.show("\(records.count)개의 위치 기록을 불러왔습니다")

        let controller = WebScreenViewController(title: "경로 지도", html: MapHTML.html,
                                                 baseURL: URL(string: "https://nowhere.app"))

        let pathData: [[String: Any]] = database.getRecentRecords(1000).map { record in
            [
                "lat": record.latitude,
                "lng": record.longitude,
                "mode": record.transportMode,
                "timestamp": record.timestamp,
                "speed": record.speed
            ]
        }
        let jsonString = Format.json(pathData)

        controller.onPageLoaded = { [weak controller] in
            // Android와 같이 JSON 문자열을 넘기고 페이지에서 JSON.parse 한다.
            let literal = Format.json([jsonString]).dropFirst().dropLast()
            controller?.evaluate("window.loadPathData(\(literal));")
        }
        controller.onBridgeCall = { method, args in
            if method == "showToast", let message = args.first as? String {
                Toast.show(message)
            }
        }
        return controller
    }

    // MARK: - 사진 촬영 위치 (PhotoMapActivity.kt)

    static func photoMap() -> UIViewController? {
        let records = PhotoDatabase.shared.getAll()

        if records.isEmpty {
            Toast.show("저장된 촬영 사진이 없습니다.", .long)
            return nil
        }

        let photoData: [[String: Any]] = records.enumerated().map { index, record in
            [
                "number": index + 1,
                "lat": record.latitude,
                "lng": record.longitude,
                "time": Format.date(record.timestamp, dateTimePattern),
                "accuracy": Format.int(record.locationAccuracy),
                "azimuth": Format.fixed(record.azimuth, 1),
                "direction": Direction.name(record.azimuth),
                "movement": record.transportMode
            ]
        }

        let html = PhotoMapHTML.html(
            count: records.count,
            photoData: Format.json(photoData),
            centerLat: average(records.map { $0.latitude }),
            centerLng: average(records.map { $0.longitude })
        )
        return WebScreenViewController(title: "사진 촬영 위치", html: html)
    }

    // MARK: - 사진 촬영 방향 (PhotoDirectionMapActivity.kt)

    static func photoDirectionMap() -> UIViewController? {
        let records = PhotoDatabase.shared.getAll()

        if records.isEmpty {
            Toast.show("저장된 촬영 사진이 없습니다.", .long)
            return nil
        }

        let photoData: [[String: Any]] = records.enumerated().map { index, record in
            let end = Direction.endPoint(latitude: record.latitude, longitude: record.longitude,
                                         azimuth: record.azimuth, distanceMeters: 45.0)
            return [
                "number": index + 1,
                "lat": record.latitude,
                "lng": record.longitude,
                "endLat": end.0,
                "endLng": end.1,
                "time": Format.date(record.timestamp, dateTimePattern),
                "azimuth": Format.fixed(record.azimuth, 1),
                "direction": Direction.name(record.azimuth),
                "accuracy": Format.int(record.locationAccuracy)
            ]
        }

        let html = PhotoDirectionMapHTML.html(
            count: records.count,
            photoData: Format.json(photoData),
            centerLat: average(records.map { $0.latitude }),
            centerLng: average(records.map { $0.longitude })
        )
        return WebScreenViewController(title: "사진 촬영 방향", html: html)
    }

    // MARK: - 통합 기록 지도 (IntegratedMapActivity.kt)

    static func integratedMap() -> UIViewController? {
        let movements = MovementDatabase.shared.getAllRecords()
        let photos = PhotoDatabase.shared.getAll()

        if movements.isEmpty && photos.isEmpty {
            Toast.show("표시할 이동 또는 사진 기록이 없습니다.", .long)
            return nil
        }

        let movementData: [[String: Any]] = movements.map { record in
            [
                "lat": record.latitude,
                "lng": record.longitude,
                "mode": record.transportMode,
                "time": record.timestamp
            ]
        }

        let photoData: [[String: Any]] = photos.enumerated().map { index, record in
            let end = Direction.endPoint(latitude: record.latitude, longitude: record.longitude,
                                         azimuth: record.azimuth, distanceMeters: 45.0)
            return [
                "number": index + 1,
                "lat": record.latitude,
                "lng": record.longitude,
                "endLat": end.0,
                "endLng": end.1,
                "time": Format.date(record.timestamp, dateTimePattern),
                "azimuth": record.azimuth,
                "direction": Direction.name(record.azimuth),
                "accuracy": Format.int(record.locationAccuracy),
                "movement": record.transportMode
            ]
        }

        let html = IntegratedMapHTML.html(
            movementData: Format.json(movementData),
            photoData: Format.json(photoData),
            centerLat: average(movements.map { $0.latitude } + photos.map { $0.latitude }),
            centerLng: average(movements.map { $0.longitude } + photos.map { $0.longitude })
        )
        return WebScreenViewController(title: "통합 기록 지도", html: html)
    }

    // MARK: - 시간순 기록 (TimelineActivity.kt)

    static func timeline() -> UIViewController? {
        let movements = MovementDatabase.shared.getAllRecords()
        let photos = PhotoDatabase.shared.getAll()

        if movements.isEmpty && photos.isEmpty {
            Toast.show("표시할 시간순 기록이 없습니다.", .long)
            return nil
        }

        let events = timelineEvents(movements: movements, photos: photos, azimuthAsText: true)
        let html = TimelineHTML.html(eventArray: Format.json(events))
        return WebScreenViewController(title: "시간순 기록", html: html)
    }

    /// 이동 기록은 첫 기록과 이동상태가 바뀌는 지점만, 사진은 전부 넣고 시간순으로 정렬한다.
    /// azimuthAsText: 시간순 화면은 "123.4" 문자열, 보고서는 숫자로 넘긴다 (Android와 동일).
    static func timelineEvents(movements: [MovementRecord], photos: [PhotoRecord],
                               azimuthAsText: Bool) -> [[String: Any]] {
        var events: [[String: Any]] = []

        var previousMode: String?
        for record in movements.sorted(by: { $0.timestamp < $1.timestamp }) {
            if previousMode == nil || previousMode != record.transportMode {
                events.append([
                    "type": "movement",
                    "timestamp": record.timestamp,
                    "time": Format.date(record.timestamp, dateTimePattern),
                    "lat": record.latitude,
                    "lng": record.longitude,
                    "mode": record.transportMode,
                    "accuracy": Format.int(record.accuracy)
                ])
            }
            previousMode = record.transportMode
        }

        for (index, record) in photos.enumerated() {
            events.append([
                "type": "photo",
                "number": index + 1,
                "timestamp": record.timestamp,
                "time": Format.date(record.timestamp, dateTimePattern),
                "lat": record.latitude,
                "lng": record.longitude,
                "accuracy": Format.int(record.locationAccuracy),
                "azimuth": azimuthAsText ? Format.fixed(record.azimuth, 1) as Any : record.azimuth as Any,
                "direction": Direction.name(record.azimuth),
                "mode": record.transportMode
            ])
        }

        return events.sorted {
            (($0["timestamp"] as? Int64) ?? 0) < (($1["timestamp"] as? Int64) ?? 0)
        }
    }

    static func average(_ values: [Double]) -> Double {
        guard !values.isEmpty else { return 0 }
        return values.reduce(0, +) / Double(values.count)
    }
}
