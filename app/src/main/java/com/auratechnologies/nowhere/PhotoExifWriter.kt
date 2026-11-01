package com.auratechnologies.nowhere

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 찍은 사진 파일(JPEG) 자체에 촬영 위치, 시각, 방향을 EXIF 정보로 기록한다.
 * 앱 밖으로 사진을 보내거나 다른 앱에서 열어도 이 정보가 함께 남는다.
 *
 * 사진을 다시 압축하지 않고 EXIF 부분만 바꾸므로 화질은 그대로다.
 * 카메라 앱이 넣어 둔 회전 정보 같은 다른 EXIF 값도 그대로 둔다.
 */
object PhotoExifWriter {

    fun write(file: File, record: PhotoRecord): Boolean {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val date = Date(record.timestamp)

            // 촬영 시각 (휴대폰의 현지 시각 + 시간대)
            val localTime = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(date)
            val offset = utcOffset(record.timestamp)
            val subSec = String.format(Locale.US, "%03d", record.timestamp % 1000)

            exif.setAttribute(ExifInterface.TAG_DATETIME, localTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, localTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, localTime)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME, subSec)
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, subSec)
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, subSec)

            // 촬영 위치 (GPS)
            exif.setLatLong(record.latitude, record.longitude)
            exif.setAltitude(record.altitude)
            exif.setAttribute(ExifInterface.TAG_GPS_MAP_DATUM, "WGS-84")
            if (record.locationAccuracy > 0f) {
                exif.setAttribute(
                    ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
                    rational(record.locationAccuracy.toDouble())
                )
            }

            // GPS 날짜/시각은 규격상 세계 표준시(UTC)로 적는다.
            val utcDate = SimpleDateFormat("yyyy:MM:dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(date)
            val utcTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(date)
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, utcDate)
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, utcTime)

            // 촬영 방향 (휴대폰 나침반, 자기 북쪽 기준이라 "M")
            val azimuth = ((record.azimuth.toDouble() % 360.0) + 360.0) % 360.0
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M")
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, rational(azimuth))

            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "NOWHERE")

            exif.saveAttributes()
            true
        } catch (e: Exception) {
            Log.e("NOWHERE", "사진 EXIF 기록 실패: ${e.message}", e)
            false
        }
    }

    /** 0.01 단위까지 남기는 EXIF 분수 값 (예: 123.45 -> "12345/100") */
    private fun rational(value: Double): String {
        return "${(abs(value) * 100).roundToLong()}/100"
    }

    /** "+09:00" 형식의 시간대 */
    private fun utcOffset(timestamp: Long): String {
        val offsetMinutes = TimeZone.getDefault().getOffset(timestamp) / 60000
        val sign = if (offsetMinutes < 0) "-" else "+"
        val minutes = abs(offsetMinutes)
        return String.format(Locale.US, "%s%02d:%02d", sign, minutes / 60, minutes % 60)
    }
}
