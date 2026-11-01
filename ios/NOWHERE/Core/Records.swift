import Foundation
import CoreLocation

// Android MovementRecord.kt / PhotoRecord.kt 대응.
// Android는 SharedPreferences에 기록 1건당 JSON 1개를 저장한다.
// iOS는 같은 JSON 필드 이름으로 Application Support 폴더의 파일에 저장한다.

struct MovementRecord: Codable {
    let timestamp: Int64        // 기록 시간 (ms)
    let latitude: Double        // 위도
    let longitude: Double       // 경도
    let altitude: Double        // 고도
    let transportMode: String   // 이동 수단
    let speed: Double           // 속도 (km/h)
    let accuracy: Double        // GPS 정확도 (m)

    enum CodingKeys: String, CodingKey {
        case timestamp
        case latitude = "lat"
        case longitude = "lng"
        case altitude = "alt"
        case transportMode = "mode"
        case speed
        case accuracy
    }
}

struct PhotoRecord: Codable {
    let id: String
    let photoUri: String        // Documents 기준 상대 경로 (예: photos/NOWHERE_123.jpg)
    let timestamp: Int64
    let latitude: Double
    let longitude: Double
    let altitude: Double
    let locationAccuracy: Double
    let azimuth: Double
    let directionAccuracy: Int
    let transportMode: String

    /// 사진 파일의 실제 위치. iOS는 앱 업데이트 때 컨테이너 경로가 바뀔 수 있어
    /// 상대 경로로 저장하고 읽을 때마다 절대 경로를 만든다.
    var photoFileURL: URL {
        return Storage.documentsDirectory.appendingPathComponent(photoUri)
    }
}

enum Storage {
    static var documentsDirectory: URL {
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    static var supportDirectory: URL {
        let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static var photoDirectory: URL {
        let url = documentsDirectory.appendingPathComponent("photos", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static func nowMillis() -> Int64 {
        return Int64(Date().timeIntervalSince1970 * 1000)
    }
}

/// JSON Lines 파일 하나에 기록을 한 줄씩 덧붙인다.
/// 손상된 줄은 Android와 마찬가지로 건너뛰고 나머지 기록은 유지한다.
final class JSONLinesStore<Record: Codable> {
    private let fileURL: URL
    private let queue = DispatchQueue(label: "nowhere.store")

    init(fileName: String) {
        fileURL = Storage.supportDirectory.appendingPathComponent(fileName)
    }

    func loadAll() -> [Record] {
        guard let data = try? Data(contentsOf: fileURL),
              let text = String(data: data, encoding: .utf8) else { return [] }
        let decoder = JSONDecoder()
        return text.split(separator: "\n").compactMap { line in
            try? decoder.decode(Record.self, from: Data(line.utf8))
        }
    }

    func append(_ record: Record) {
        guard var line = try? JSONEncoder().encode(record) else { return }
        line.append(0x0A)
        queue.sync {
            if let handle = try? FileHandle(forWritingTo: fileURL) {
                handle.seekToEndOfFile()
                handle.write(line)
                handle.closeFile()
            } else {
                try? line.write(to: fileURL, options: .atomic)
            }
        }
    }
}

final class MovementDatabase {
    static let shared = MovementDatabase()

    private let store = JSONLinesStore<MovementRecord>(fileName: "movement_logs.jsonl")
    private var records: [MovementRecord]

    private init() {
        // 앱 시작 시 저장된 기존 데이터 로드 후 시간순 정렬
        records = store.loadAll().sorted { $0.timestamp < $1.timestamp }
    }

    func addRecord(location: CLLocation, mode: TransportMode) {
        let record = MovementRecord(
            timestamp: Storage.nowMillis(),
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            altitude: location.altitude,
            transportMode: mode.rawValue,
            speed: max(location.speed, 0) * 3.6, // m/s → km/h
            accuracy: location.horizontalAccuracy
        )
        records.append(record)
        store.append(record)
    }

    func getRecordsInRange(startTime: Int64, endTime: Int64) -> [MovementRecord] {
        return records.filter { $0.timestamp >= startTime && $0.timestamp <= endTime }
    }

    func getRecentRecords(_ count: Int) -> [MovementRecord] {
        return Array(records.suffix(count))
    }

    func getAllRecords() -> [MovementRecord] {
        return records
    }

    func getTotalRecords() -> Int {
        return records.count
    }
}

final class PhotoDatabase {
    static let shared = PhotoDatabase()

    private let store = JSONLinesStore<PhotoRecord>(fileName: "photo_records.jsonl")
    private var records: [PhotoRecord]

    private init() {
        records = store.loadAll().sorted { $0.timestamp < $1.timestamp }
    }

    func add(_ record: PhotoRecord) {
        records.append(record)
        records.sort { $0.timestamp < $1.timestamp }
        store.append(record)
    }

    func getAll() -> [PhotoRecord] {
        return records
    }

    func getInRange(startTime: Int64, endTime: Int64) -> [PhotoRecord] {
        return records.filter { $0.timestamp >= startTime && $0.timestamp <= endTime }
    }

    func getTotalRecords() -> Int {
        return records.count
    }
}
