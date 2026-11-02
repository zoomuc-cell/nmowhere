package com.auratechnologies.nowhere

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

data class PhotoRecord(
    val id: String,
    val photoUri: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val locationAccuracy: Float,
    val azimuth: Float,
    val directionAccuracy: Int,
    val transportMode: String
)

class PhotoDatabase(private val context: Context) {

    private val records = mutableListOf<PhotoRecord>()

    init {
        load()
    }

    private fun load() {
        records.clear()

        val prefs = context.getSharedPreferences(
            "photo_records",
            Context.MODE_PRIVATE
        )

        for ((key, value) in prefs.all) {
            if (!key.startsWith("photo_") || value !is String) continue

            try {
                val json = JSONObject(value)

                records.add(
                    PhotoRecord(
                        id = json.getString("id"),
                        photoUri = json.getString("photoUri"),
                        timestamp = json.getLong("timestamp"),
                        latitude = json.getDouble("latitude"),
                        longitude = json.getDouble("longitude"),
                        altitude = json.getDouble("altitude"),
                        locationAccuracy = json.getDouble("locationAccuracy").toFloat(),
                        azimuth = json.getDouble("azimuth").toFloat(),
                        directionAccuracy = json.optInt("directionAccuracy", 0),
                        transportMode = json.optString("transportMode", "UNKNOWN")
                    )
                )
            } catch (_: Exception) {
                // 손상된 개별 기록은 건너뛰고 나머지 기록은 유지한다.
            }
        }

        records.sortBy { it.timestamp }
    }

    fun add(record: PhotoRecord) {

        records.add(record)
        records.sortBy { it.timestamp }

        val json = JSONObject().apply {
            put("id", record.id)
            put("photoUri", record.photoUri)
            put("timestamp", record.timestamp)
            put("latitude", record.latitude)
            put("longitude", record.longitude)
            put("altitude", record.altitude)
            put("locationAccuracy", record.locationAccuracy)
            put("azimuth", record.azimuth)
            put("directionAccuracy", record.directionAccuracy)
            put("transportMode", record.transportMode)
        }

        context.getSharedPreferences(
            "photo_records",
            Context.MODE_PRIVATE
        ).edit()
            .putString("photo_${record.id}", json.toString())
            .apply()
    }

    fun getAll(): List<PhotoRecord> =
        records.sortedBy { it.timestamp }

    fun getInRange(
        startTime: Long,
        endTime: Long
    ): List<PhotoRecord> =
        records
            .filter { it.timestamp in startTime..endTime }
            .sortedBy { it.timestamp }

    fun getTotalRecords(): Int = records.size

    // 지정한 사진 기록과 사진 파일을 모두 지운다 (저장 기록 관리의 날짜별 삭제)
    fun delete(toDelete: List<PhotoRecord>) {
        if (toDelete.isEmpty()) return

        val editor = context.getSharedPreferences(
            "photo_records",
            Context.MODE_PRIVATE
        ).edit()

        val photoDirectory = File(context.filesDir, "photos")

        toDelete.forEach { record ->
            editor.remove("photo_${record.id}")

            // photoUri는 FileProvider 주소(content://.../nowhere_photos/파일명)이므로
            // 마지막 부분인 파일명으로 앱 내부 photos 폴더의 실제 파일을 찾는다.
            val fileName = Uri.parse(record.photoUri).lastPathSegment
            if (!fileName.isNullOrBlank()) {
                val file = File(photoDirectory, fileName)
                if (file.parentFile == photoDirectory && file.exists()) {
                    file.delete()
                }
            }
        }

        editor.apply()

        val ids = toDelete.map { it.id }.toSet()
        records.removeAll { it.id in ids }
    }
}
