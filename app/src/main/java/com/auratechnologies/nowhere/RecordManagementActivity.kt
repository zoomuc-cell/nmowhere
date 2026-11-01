package com.auratechnologies.nowhere

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordManagementActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val movementDatabase = MovementDatabase(this)
        val photoDatabase = PhotoDatabase(this)

        val movements = movementDatabase.getAllRecords()
        val photos = photoDatabase.getAll()

        val scrollView = ScrollView(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 50, 40, 60)
        }

        val title = TextView(this).apply {
            text = "🗂 저장 기록 관리"
            textSize = 28f
            setPadding(0, 0, 0, 12)
        }

        val subtitle = TextView(this).apply {
            text = "날짜별로 저장된 현장 기록을 확인합니다."
            textSize = 16f
            setPadding(0, 0, 0, 30)
        }

        val summary = TextView(this).apply {
            text =
                "전체 저장 기록\n\n" +
                        "📍 이동 위치 기록: ${movements.size}건\n" +
                        "📷 촬영 사진 기록: ${photos.size}장"
            textSize = 18f
            setPadding(30, 30, 30, 30)
        }

        container.addView(title)
        container.addView(subtitle)
        container.addView(summary)

        val sectionTitle = TextView(this).apply {
            text = "날짜별 기록"
            textSize = 22f
            setPadding(0, 40, 0, 20)
        }

        container.addView(sectionTitle)

        val dayFormat =
            SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)

        val timeFormat =
            SimpleDateFormat("HH:mm:ss", Locale.KOREA)

        val allDates = mutableSetOf<String>()

        movements.forEach {
            allDates.add(
                dayFormat.format(Date(it.timestamp))
            )
        }

        photos.forEach {
            allDates.add(
                dayFormat.format(Date(it.timestamp))
            )
        }

        val sortedDates =
            allDates.sortedDescending()

        if (sortedDates.isEmpty()) {

            val emptyText = TextView(this).apply {
                text = "아직 저장된 기록이 없습니다."
                textSize = 17f
                setPadding(20, 30, 20, 30)
            }

            container.addView(emptyText)

        } else {

            sortedDates.forEach { dateText ->

                val dayMovements =
                    movements.filter {
                        dayFormat.format(
                            Date(it.timestamp)
                        ) == dateText
                    }

                val dayPhotos =
                    photos.filter {
                        dayFormat.format(
                            Date(it.timestamp)
                        ) == dateText
                    }

                val timestamps =
                    mutableListOf<Long>()

                timestamps.addAll(
                    dayMovements.map { it.timestamp }
                )

                timestamps.addAll(
                    dayPhotos.map { it.timestamp }
                )

                val startTime =
                    timestamps.minOrNull()

                val endTime =
                    timestamps.maxOrNull()

                val periodText =
                    if (
                        startTime != null &&
                        endTime != null
                    ) {
                        "${timeFormat.format(Date(startTime))}" +
                                " ~ " +
                                timeFormat.format(Date(endTime))
                    } else {
                        "-"
                    }

                val dayCard = TextView(this).apply {

                    text =
                        "📅 $dateText\n\n" +
                                "📍 이동 기록  ${dayMovements.size}건\n" +
                                "📷 사진 기록  ${dayPhotos.size}장\n" +
                                "⏱ $periodText"

                    textSize = 18f

                    setPadding(
                        35,
                        30,
                        35,
                        30
                    )

                    setBackgroundColor(
                        0xFFF1F3F6.toInt()
                    )
                    setOnClickListener {

                        if (startTime != null && endTime != null) {

                            val intent = Intent(
                                this@RecordManagementActivity,
                                ReportActivity::class.java
                            ).apply {
                                putExtra("startTime", startTime)
                                putExtra("endTime", endTime)
                                putExtra("selectedDate", dateText)
                            }

                            startActivity(intent)
                        }
                    }
                }

                container.addView(dayCard)

                val deleteButton = Button(this).apply {
                    text = "🗑 이 날짜 기록 삭제"
                    setTextColor(0xFFD32F2F.toInt())

                    setOnClickListener {
                        confirmDeleteDay(
                            dateText,
                            dayMovements,
                            dayPhotos,
                            movementDatabase,
                            photoDatabase
                        )
                    }
                }

                container.addView(deleteButton)

                val spacer = TextView(this).apply {
                    height = 20
                }

                container.addView(spacer)
            }
        }

        val quickTitle = TextView(this).apply {
            text = "전체 기록 바로가기"
            textSize = 22f
            setPadding(0, 35, 0, 15)
        }

        container.addView(quickTitle)

        val photoButton = Button(this).apply {
            text = "📷 촬영 사진 기록 보기"

            setOnClickListener {
                startActivity(
                    Intent(
                        this@RecordManagementActivity,
                        PhotoRecordsActivity::class.java
                    )
                )
            }
        }

        val mapButton = Button(this).apply {
            text = "🌐 통합 기록 지도 보기"

            setOnClickListener {
                startActivity(
                    Intent(
                        this@RecordManagementActivity,
                        IntegratedMapActivity::class.java
                    )
                )
            }
        }

        val timelineButton = Button(this).apply {
            text = "⏱ 시간순 기록 보기"

            setOnClickListener {
                startActivity(
                    Intent(
                        this@RecordManagementActivity,
                        TimelineActivity::class.java
                    )
                )
            }
        }

        val reportButton = Button(this).apply {
            text = "📄 기록 보고서 / PDF"

            setOnClickListener {
                startActivity(
                    Intent(
                        this@RecordManagementActivity,
                        ReportActivity::class.java
                    )
                )
            }
        }

        container.addView(photoButton)
        container.addView(mapButton)
        container.addView(timelineButton)
        container.addView(reportButton)

        scrollView.addView(container)

        setContentView(scrollView)
    }

    // 지우기 전에 확인 창을 띄우고, '삭제'를 누르면 그날의 이동 기록과 사진을 모두 지운다.
    private fun confirmDeleteDay(
        dateText: String,
        dayMovements: List<MovementRecord>,
        dayPhotos: List<PhotoRecord>,
        movementDatabase: MovementDatabase,
        photoDatabase: PhotoDatabase
    ) {
        AlertDialog.Builder(this)
            .setTitle("기록 삭제")
            .setMessage(
                "$dateText 기록을 삭제할까요?\n\n" +
                        "📍 이동 기록 ${dayMovements.size}건\n" +
                        "📷 사진 ${dayPhotos.size}장\n\n" +
                        "삭제한 기록과 사진은 되돌릴 수 없습니다."
            )
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                movementDatabase.deleteRecords(dayMovements)
                photoDatabase.delete(dayPhotos)

                Toast.makeText(
                    this,
                    "$dateText 기록을 삭제했습니다.",
                    Toast.LENGTH_SHORT
                ).show()

                // 화면을 다시 그려 요약과 날짜 목록을 새로 고친다.
                recreate()
            }
            .show()
    }
}