package com.flechazo.toolbox.feature.period

import com.google.gson.Gson
import java.time.LocalDate

/**
 * [PeriodData] 与磁盘 JSON 之间的编解码。
 *
 * 用 DTO + 字符串日期而不是直接序列化领域模型：Gson 无法反射构造 `LocalDate`，
 * 且字符串日期让磁盘格式与领域模型解耦（改模型不必动存储格式）。
 */
internal object PeriodCodec {

    private val gson = Gson()

    private data class RecordDto(
        val start: String = "",
        val end: String? = null,
        val flows: Map<String, String> = emptyMap(),
        val note: String = "",
    )

    private data class LogDto(
        val date: String = "",
        val symptoms: List<String> = emptyList(),
        val pain: String? = null,
        val note: String = "",
    )

    private data class SettingsDto(
        val cycleLengthOverride: Int? = null,
        val periodLengthOverride: Int? = null,
        val predictEnabled: Boolean = true,
        val showFertileWindow: Boolean = true,
        val showCycleDay: Boolean = true,
        val consentAccepted: Boolean = false,
    )

    private data class DataDto(
        val schemaVersion: Int = PeriodData.CURRENT_SCHEMA,
        val records: List<RecordDto> = emptyList(),
        val logs: List<LogDto> = emptyList(),
        val settings: SettingsDto = SettingsDto(),
    )

    fun encode(data: PeriodData): String = gson.toJson(
        DataDto(
            schemaVersion = PeriodData.CURRENT_SCHEMA,
            records = data.records.map { record ->
                RecordDto(
                    start = record.start.toString(),
                    end = record.end?.toString(),
                    flows = record.flows.mapKeys { it.key.toString() }.mapValues { it.value.name },
                    note = record.note,
                )
            },
            logs = data.logs.map { log ->
                LogDto(
                    date = log.date.toString(),
                    symptoms = log.symptoms.map { it.name },
                    pain = log.pain?.name,
                    note = log.note,
                )
            },
            settings = SettingsDto(
                cycleLengthOverride = data.settings.cycleLengthOverride,
                periodLengthOverride = data.settings.periodLengthOverride,
                predictEnabled = data.settings.predictEnabled,
                showFertileWindow = data.settings.showFertileWindow,
                showCycleDay = data.settings.showCycleDay,
                consentAccepted = data.settings.consentAccepted,
            ),
        ),
    )

    /** 解码失败时返回空文档（宁可空，不要崩）。导入路径请用 [decodeOrThrow]。 */
    fun decode(raw: String?): PeriodData =
        runCatching { decodeOrThrow(raw) }.getOrDefault(PeriodData())

    /** 严格解码：格式非法时抛异常，供导入校验使用。 */
    fun decodeOrThrow(raw: String?): PeriodData {
        if (raw.isNullOrBlank()) return PeriodData()
        val dto = gson.fromJson(raw, DataDto::class.java) ?: return PeriodData()
        return PeriodData(
            schemaVersion = dto.schemaVersion,
            records = dto.records.mapNotNull { it.toModel() }.sortedBy { it.start },
            logs = dto.logs.mapNotNull { it.toModel() }.sortedBy { it.date },
            settings = dto.settings.toModel(),
        )
    }

    private fun RecordDto.toModel(): PeriodRecord? {
        val start = parseDate(start) ?: return null
        val parsedEnd = parseDate(end)?.takeIf { !it.isBefore(start) }
        return PeriodRecord(
            start = start,
            end = parsedEnd,
            flows = flows.mapNotNull { (key, value) ->
                val date = parseDate(key) ?: return@mapNotNull null
                val level = enumOrNull<FlowLevel>(value) ?: return@mapNotNull null
                date to level
            }.toMap(),
            note = note,
        )
    }

    private fun LogDto.toModel(): DailyLog? {
        val date = parseDate(date) ?: return null
        return DailyLog(
            date = date,
            symptoms = symptoms.mapNotNull { enumOrNull<Symptom>(it) }.toSet(),
            pain = pain?.let { enumOrNull<PainLevel>(it) },
            note = note,
        )
    }

    private fun SettingsDto.toModel(): PeriodSettings = PeriodSettings(
        cycleLengthOverride = cycleLengthOverride?.takeIf { it in 21..45 },
        periodLengthOverride = periodLengthOverride?.takeIf { it in 2..10 },
        predictEnabled = predictEnabled,
        showFertileWindow = showFertileWindow,
        showCycleDay = showCycleDay,
        consentAccepted = consentAccepted,
    )

    /**
     * 旧版把用户点过的每一天都存成一次「经期开始」，格式是 ISO 日期字符串数组。
     *
     * 迁移规则：升序排列后，相邻间隔 ≤ 2 天归为同一段；段内首日 = start、末日 = end；
     * 只有 1 天的段 `end = null`（无法判断是"只记了开始"还是"只来了一天"）。
     */
    fun decodeLegacyStarts(raw: String?): List<PeriodRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        val dates = runCatching { gson.fromJson(raw, Array<String>::class.java) }
            .getOrNull()
            ?.mapNotNull { parseDate(it) }
            ?.distinct()
            ?.sorted()
            .orEmpty()
        if (dates.isEmpty()) return emptyList()

        val records = mutableListOf<PeriodRecord>()
        var segmentStart = dates.first()
        var segmentEnd = dates.first()
        for (date in dates.drop(1)) {
            if (date.toEpochDay() - segmentEnd.toEpochDay() <= 2) {
                segmentEnd = date
            } else {
                records += buildRecord(segmentStart, segmentEnd)
                segmentStart = date
                segmentEnd = date
            }
        }
        records += buildRecord(segmentStart, segmentEnd)
        return records
    }

    private fun buildRecord(start: LocalDate, end: LocalDate): PeriodRecord =
        PeriodRecord(start = start, end = if (end == start) null else end)

    private fun parseDate(value: String?): LocalDate? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        runCatching { enumValueOf<T>(name) }.getOrNull()
}
