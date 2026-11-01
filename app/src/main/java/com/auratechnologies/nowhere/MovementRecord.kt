package com.auratechnologies.nowhere

import android.content.Context
import android.location.Location
import org.json.JSONObject

data class MovementRecord(
    val timestamp: Long,           // 기록 시간
    val latitude: Double,          // 위도
    val longitude: Double,         // 경도
    val altitude: Double,          // 고도
    val transportMode: String,     // 이동 수단
    val speed: Float,              // 속도 (km/h)
    val accuracy: Float            // GPS 정확도 (m)
)



class MovementDatabase(private val context: Context) {
     private val records = mutableListOf<MovementRecord>()

    init {
        // 앱 시작 시 SharedPreferences에서 기존 데이터 로드
        loadFromPreferences()
    }

    private fun loadFromPreferences() {
        val prefs = context.getSharedPreferences("movement_logs", Context.MODE_PRIVATE)
        val allEntries = prefs.all
        
        for ((key, value) in allEntries) {
            if (key.startsWith("record_") && value is String) {
                try {
                    val json = org.json.JSONObject(value)
                    val record = MovementRecord(
                        timestamp = json.getLong("timestamp"),
                        latitude = json.getDouble("lat"),
                        longitude = json.getDouble("lng"),
                        altitude = json.getDouble("alt"),
                        transportMode = json.getString("mode"),
                        speed = json.getDouble("speed").toFloat(),
                        accuracy = json.getDouble("accuracy").toFloat()
                    )
                    records.add(record)
                } catch (e: Exception) {
                    // 손상된 데이터 무시
                }
            }
        }
        
        // 시간순 정렬
        records.sortBy { it.timestamp }
    }

     fun addRecord(
        location: Location,
        mode: MovementDetector.TransportMode
    ) {
        val record = MovementRecord(
            timestamp = System.currentTimeMillis(),
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            transportMode = mode.name,
            speed = location.speed * 3.6f, // m/s → km/h
            accuracy = location.accuracy
        )

        records.add(record)

        // SharedPreferences에 저장 (영구 보존)
        saveToPreferences(record)
    }

    private fun saveToPreferences(record: MovementRecord) {
        val prefs = context.getSharedPreferences("movement_logs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val json = JSONObject().apply {
            put("timestamp", record.timestamp)
            put("lat", record.latitude)
            put("lng", record.longitude)
            put("alt", record.altitude)
            put("mode", record.transportMode)
            put("speed", record.speed)
            put("accuracy", record.accuracy)
        }.toString()

        editor.putString("record_${record.timestamp}", json)
        editor.apply()
    }

    // 특정 시간대 기록 조회
    fun getRecordsInRange(startTime: Long, endTime: Long): List<MovementRecord> {
        return records.filter { it.timestamp in startTime..endTime }
    }

    // 최근 N개 기록 조회
        fun getRecentRecords(count: Int): List<MovementRecord> {
        return records.takeLast(count)
    }

    // 모든 기록 조회 
    fun getAllRecords(): List<MovementRecord> {
        return records.toList()
    }

    // 모든 기록 내보내기 (CSV 형식)
    fun exportToCSV(): String {
        val header = "Timestamp,DateTime,Latitude,Longitude,Altitude,TransportMode,Speed(km/h),Accuracy(m)\n"
        val rows = records.joinToString("\n") {
            val dateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(it.timestamp))
            "${it.timestamp},$dateTime,${it.latitude},${it.longitude},${it.altitude},${it.transportMode},${it.speed},${it.accuracy}"
        }
        return header + rows
    }

    // 총 기록 개수
    fun getTotalRecords(): Int = records.size

    // 지정한 이동 기록을 메모리와 저장소에서 모두 지운다 (저장 기록 관리의 날짜별 삭제)
    fun deleteRecords(toDelete: List<MovementRecord>) {
        if (toDelete.isEmpty()) return

        val editor = context
            .getSharedPreferences("movement_logs", Context.MODE_PRIVATE)
            .edit()

        toDelete.forEach { editor.remove("record_${it.timestamp}") }
        editor.apply()

        val timestamps = toDelete.map { it.timestamp }.toSet()
        records.removeAll { it.timestamp in timestamps }
    }

    // 이동 수단별 통계
    fun getStatsByMode(): Map<String, Int> {
        return records.groupingBy { it.transportMode }.eachCount()
    }
}
