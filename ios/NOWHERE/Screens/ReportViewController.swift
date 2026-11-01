import UIKit
import WebKit

/// Android ReportActivity.kt 대응: 기록 보고서 화면과 PDF 저장.
final class ReportViewController: WebScreenViewController {

    /// 날짜 카드에서 들어오면 그 날의 시작/끝 시각, 전체 보고서면 nil.
    static func make(startTime: Int64? = nil, endTime: Int64? = nil) -> UIViewController? {
        let movementDatabase = MovementDatabase.shared
        let photoDatabase = PhotoDatabase.shared

        let movements: [MovementRecord]
        let photos: [PhotoRecord]
        if let start = startTime, let end = endTime {
            movements = movementDatabase.getRecordsInRange(startTime: start, endTime: end)
                .sorted { $0.timestamp < $1.timestamp }
            photos = photoDatabase.getInRange(startTime: start, endTime: end)
                .sorted { $0.timestamp < $1.timestamp }
        } else {
            movements = movementDatabase.getAllRecords().sorted { $0.timestamp < $1.timestamp }
            photos = photoDatabase.getAll().sorted { $0.timestamp < $1.timestamp }
        }

        if movements.isEmpty && photos.isEmpty {
            Toast.show("작성할 기록이 없습니다.", .long)
            return nil
        }

        let pattern = "yyyy-MM-dd HH:mm:ss"
        let allTimes = movements.map { $0.timestamp } + photos.map { $0.timestamp }
        let startText = Format.date(allTimes.min() ?? 0, pattern)
        let endText = Format.date(allTimes.max() ?? 0, pattern)

        let movementData: [[String: Any]] = movements.map { record in
            [
                "lat": record.latitude,
                "lng": record.longitude,
                "mode": record.transportMode,
                "time": record.timestamp
            ]
        }

        let photoMapData: [[String: Any]] = photos.enumerated().map { index, photo in
            let end = Direction.endPoint(latitude: photo.latitude, longitude: photo.longitude,
                                         azimuth: photo.azimuth, distanceMeters: 45.0)
            return [
                "number": index + 1,
                "lat": photo.latitude,
                "lng": photo.longitude,
                "endLat": end.0,
                "endLng": end.1,
                "azimuth": photo.azimuth,
                "direction": Direction.name(photo.azimuth)
            ]
        }

        let timelineData = Screens.timelineEvents(movements: movements, photos: photos, azimuthAsText: false)

        var photoHtml = ""
        for (index, photo) in photos.enumerated() {
            photoHtml += ReportHTML.photoCard(index: index, photo: photo,
                                              photoDataUrl: photoDataUrl(photo) ?? "")
        }

        let html = ReportHTML.html(
            startText: startText,
            endText: endText,
            movements: movements,
            photos: photos,
            photoHtml: photoHtml,
            movementData: Format.json(movementData),
            photoMapData: Format.json(photoMapData),
            timelineData: Format.json(timelineData)
        )
        return ReportViewController(title: "기록 보고서", html: html)
    }

    /// 보고서에 사진을 직접 넣기 위해 base64 data URL로 바꾼다 (Android photoUriToBase64 대응).
    private static func photoDataUrl(_ photo: PhotoRecord) -> String? {
        guard let data = try? Data(contentsOf: photo.photoFileURL) else { return nil }
        return "data:image/jpeg;base64," + data.base64EncodedString()
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        // Android는 화면을 길게 누르면 PDF 인쇄 창이 열린다. iOS도 같게 하고,
        // 찾기 쉽도록 오른쪽 위에 PDF 버튼도 둔다.
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            title: "PDF", style: .plain, target: self, action: #selector(createPdf)
        )

        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
        longPress.minimumPressDuration = 0.8
        webView.addGestureRecognizer(longPress)
    }

    @objc private func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        if gesture.state == .began {
            createPdf()
        }
    }

    /// Android createPdfFromWebView 대응: A4 크기로 나눈 PDF를 만들고 저장·공유·인쇄 창을 연다.
    @objc private func createPdf() {
        guard presentedViewController == nil else { return }

        let renderer = UIPrintPageRenderer()
        renderer.addPrintFormatter(webView.viewPrintFormatter(), startingAtPageAt: 0)

        // A4: 595.2 x 841.8 pt, 여백 약 10mm
        let paper = CGRect(x: 0, y: 0, width: 595.2, height: 841.8)
        let printable = paper.insetBy(dx: 28, dy: 28)
        renderer.setValue(NSValue(cgRect: paper), forKey: "paperRect")
        renderer.setValue(NSValue(cgRect: printable), forKey: "printableRect")

        let pdfData = NSMutableData()
        UIGraphicsBeginPDFContextToData(pdfData, paper, nil)
        renderer.prepare(forDrawingPages: NSRange(location: 0, length: renderer.numberOfPages))
        for page in 0..<renderer.numberOfPages {
            UIGraphicsBeginPDFPage()
            renderer.drawPage(at: page, in: UIGraphicsGetPDFContextBounds())
        }
        UIGraphicsEndPDFContext()

        let fileName = "NOWHERE_Record_Report_\(Format.date(Storage.nowMillis(), "yyyyMMdd_HHmmss")).pdf"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        do {
            try pdfData.write(to: url, options: .atomic)
        } catch {
            Toast.show("PDF를 만들 수 없습니다.", .long)
            return
        }

        let activity = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        activity.popoverPresentationController?.barButtonItem = navigationItem.rightBarButtonItem
        present(activity, animated: true)
    }
}
