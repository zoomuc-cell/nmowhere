import Foundation
import ImageIO

/// Android PhotoExifWriter.kt 대응.
/// 찍은 사진 파일(JPEG) 자체에 촬영 위치, 시각, 방향을 EXIF 정보로 기록한다.
/// 앱 밖으로 사진을 보내거나 다른 앱에서 열어도 이 정보가 함께 남는다.
///
/// 먼저 사진을 다시 압축하지 않고 메타데이터만 바꾸는 방법을 쓰고,
/// 그게 안 될 때만 같은 화질(0.9)로 다시 저장한다.
enum PhotoExif {

    @discardableResult
    static func write(to url: URL, record: PhotoRecord) -> Bool {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let type = CGImageSourceGetType(source) else {
            return false
        }

        let properties = makeProperties(record)

        if let data = copyWithMetadata(source: source, type: type, properties: properties)
            ?? reencode(source: source, type: type, properties: properties) {
            do {
                try data.write(to: url, options: .atomic)
                return true
            } catch {
                return false
            }
        }
        return false
    }

    // MARK: - EXIF 값 만들기

    /// [사전 이름: [항목: 값]] 형태 (예: GPS 사전 안의 위도)
    private static func makeProperties(_ record: PhotoRecord) -> [CFString: [CFString: Any]] {
        let date = Date(timeIntervalSince1970: TimeInterval(record.timestamp) / 1000)

        // 촬영 시각 (휴대폰의 현지 시각 + 시간대)
        let localTime = format(date, "yyyy:MM:dd HH:mm:ss", TimeZone.current)
        let offset = utcOffset(date)
        let subSec = String(format: "%03d", Int(record.timestamp % 1000))

        let exif: [CFString: Any] = [
            kCGImagePropertyExifDateTimeOriginal: localTime,
            kCGImagePropertyExifDateTimeDigitized: localTime,
            kCGImagePropertyExifOffsetTime: offset,
            kCGImagePropertyExifOffsetTimeOriginal: offset,
            kCGImagePropertyExifOffsetTimeDigitized: offset,
            kCGImagePropertyExifSubsecTimeOriginal: subSec,
            kCGImagePropertyExifSubsecTimeDigitized: subSec
        ]

        let tiff: [CFString: Any] = [
            kCGImagePropertyTIFFDateTime: localTime,
            kCGImagePropertyTIFFSoftware: "NOWHERE"
        ]

        // 촬영 위치 (GPS). 날짜/시각은 규격상 세계 표준시(UTC)로 적는다.
        let utc = TimeZone(identifier: "UTC")!
        let azimuth = (record.azimuth.truncatingRemainder(dividingBy: 360) + 360)
            .truncatingRemainder(dividingBy: 360)

        var gps: [CFString: Any] = [
            kCGImagePropertyGPSLatitude: abs(record.latitude),
            kCGImagePropertyGPSLatitudeRef: record.latitude >= 0 ? "N" : "S",
            kCGImagePropertyGPSLongitude: abs(record.longitude),
            kCGImagePropertyGPSLongitudeRef: record.longitude >= 0 ? "E" : "W",
            kCGImagePropertyGPSAltitude: abs(record.altitude),
            kCGImagePropertyGPSAltitudeRef: record.altitude >= 0 ? 0 : 1,
            kCGImagePropertyGPSMapDatum: "WGS-84",
            kCGImagePropertyGPSDateStamp: format(date, "yyyy:MM:dd", utc),
            kCGImagePropertyGPSTimeStamp: format(date, "HH:mm:ss", utc),
            // 촬영 방향 (휴대폰 나침반, 자기 북쪽 기준이라 "M")
            kCGImagePropertyGPSImgDirection: azimuth,
            kCGImagePropertyGPSImgDirectionRef: "M"
        ]
        if record.locationAccuracy > 0 {
            gps[kCGImagePropertyGPSHPositioningError] = record.locationAccuracy
        }

        return [
            kCGImagePropertyExifDictionary: exif,
            kCGImagePropertyTIFFDictionary: tiff,
            kCGImagePropertyGPSDictionary: gps
        ]
    }

    // MARK: - 파일에 쓰기

    /// 사진은 다시 압축하지 않고 메타데이터만 바꿔 넣는다.
    private static func copyWithMetadata(source: CGImageSource,
                                         type: CFString,
                                         properties: [CFString: [CFString: Any]]) -> Data? {
        let metadata = CGImageMetadataCreateMutable()
        for (dictionaryName, values) in properties {
            for (key, value) in values {
                guard CGImageMetadataSetValueMatchingImageProperty(
                    metadata, dictionaryName, key, value as CFTypeRef) else {
                    return nil
                }
            }
        }

        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(output, type, 1, nil) else {
            return nil
        }
        let options: [CFString: Any] = [
            kCGImageDestinationMetadata: metadata,
            kCGImageDestinationMergeMetadata: true
        ]
        guard CGImageDestinationCopyImageSource(destination, source, options as CFDictionary, nil) else {
            return nil
        }
        return output as Data
    }

    /// 위 방법이 안 될 때: 기존 정보에 새 값을 합쳐 같은 화질로 다시 저장한다.
    private static func reencode(source: CGImageSource,
                                 type: CFString,
                                 properties: [CFString: [CFString: Any]]) -> Data? {
        var merged = (CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]) ?? [:]
        for (dictionaryName, values) in properties {
            var dictionary = (merged[dictionaryName] as? [CFString: Any]) ?? [:]
            dictionary.merge(values) { _, new in new }
            merged[dictionaryName] = dictionary
        }
        merged[kCGImageDestinationLossyCompressionQuality] = 0.9

        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(output, type, 1, nil) else {
            return nil
        }
        CGImageDestinationAddImageFromSource(destination, source, 0, merged as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { return nil }
        return output as Data
    }

    // MARK: - 도우미

    private static func format(_ date: Date, _ pattern: String, _ timeZone: TimeZone) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = timeZone
        formatter.dateFormat = pattern
        return formatter.string(from: date)
    }

    /// "+09:00" 형식의 시간대
    private static func utcOffset(_ date: Date) -> String {
        let seconds = TimeZone.current.secondsFromGMT(for: date)
        let minutes = abs(seconds) / 60
        return String(format: "%@%02d:%02d", seconds < 0 ? "-" : "+", minutes / 60, minutes % 60)
    }
}
