package com.flechazo.toolbox.core.registry

import com.flechazo.toolbox.feature.base64.Base64Screen
import com.flechazo.toolbox.feature.bmi.BmiScreen
import com.flechazo.toolbox.feature.calculator.CalculatorScreen
import com.flechazo.toolbox.feature.color_picker.ColorPickerScreen
import com.flechazo.toolbox.feature.compass.CompassScreen
import com.flechazo.toolbox.feature.countdown.CountdownScreen
import com.flechazo.toolbox.feature.currency.CurrencyScreen
import com.flechazo.toolbox.feature.danmaku.DanmakuScreen
import com.flechazo.toolbox.feature.decision.DecisionScreen
import com.flechazo.toolbox.feature.device.DeviceScreen
import com.flechazo.toolbox.feature.image_compress.ImageCompressScreen
import com.flechazo.toolbox.feature.money.MoneyScreen
import com.flechazo.toolbox.feature.kinship.KinshipScreen
import com.flechazo.toolbox.feature.level.LevelScreen
import com.flechazo.toolbox.feature.ninegrid.NineGridScreen
import com.flechazo.toolbox.feature.password_gen.PasswordGenScreen
import com.flechazo.toolbox.feature.password_vault.PasswordVaultScreen
import com.flechazo.toolbox.feature.perler.PerlerScreen
import com.flechazo.toolbox.feature.period.PeriodScreen
import com.flechazo.toolbox.feature.pomodoro.PomodoroScreen
import com.flechazo.toolbox.feature.qr_code.QrCodeScreen
import com.flechazo.toolbox.feature.ruler.RulerScreen
import com.flechazo.toolbox.feature.stitch.StitchScreen
import com.flechazo.toolbox.feature.text_diff.TextDiffScreen
import com.flechazo.toolbox.feature.timestamp.TimestampScreen
import com.flechazo.toolbox.feature.unit_converter.UnitConverterScreen
import com.flechazo.toolbox.feature.watermark.WatermarkScreen

/**
 * All tools registered in one place. Search, home grid, category list and
 * navigation destinations are all derived from this list.
 */
object ToolCatalog {

    val all: List<ToolDef> = listOf(
        ToolDef(
            id = "calculator", title = "计算器", description = "四则运算与括号",
            icon = ToolIcons.calculator, category = ToolCategory.CALCULATE,
            keywords = listOf("计算", "加减乘除", "calc", "math"), priority = 0,
        ) { onBack -> CalculatorScreen(onBack) },

        ToolDef(
            id = "unit_converter", title = "单位换算", description = "长度/重量/温度/面积等",
            icon = ToolIcons.converter, category = ToolCategory.CALCULATE,
            keywords = listOf("单位", "换算", "长度", "重量", "温度", "unit"), priority = 0,
        ) { onBack -> UnitConverterScreen(onBack) },

        ToolDef(
            id = "currency", title = "汇率换算", description = "实时汇率 · 离线缓存",
            icon = ToolIcons.currency, category = ToolCategory.CALCULATE,
            keywords = listOf("汇率", "货币", "currency", "美元"), priority = 0,
        ) { onBack -> CurrencyScreen(onBack) },

        ToolDef(
            id = "ninegrid", title = "九宫格切图", description = "图片切九宫格并保存",
            icon = ToolIcons.nineGrid, category = ToolCategory.IMAGE,
            keywords = listOf("九宫格", "切图", "朋友圈", "grid"), priority = 0,
        ) { onBack -> NineGridScreen(onBack) },

        ToolDef(
            id = "qrcode", title = "二维码", description = "生成与识别二维码",
            icon = ToolIcons.qr, category = ToolCategory.IMAGE,
            keywords = listOf("二维码", "扫码", "qr"), priority = 0,
        ) { onBack -> QrCodeScreen(onBack) },

        ToolDef(
            id = "base64", title = "Base64 编解码", description = "文本编码解码",
            icon = ToolIcons.base64, category = ToolCategory.TEXT,
            keywords = listOf("base64", "编码", "解码", "encode"), priority = 0,
        ) { onBack -> Base64Screen(onBack) },

        ToolDef(
            id = "timestamp", title = "时间戳转换", description = "时间戳与日期互转",
            icon = ToolIcons.timestamp, category = ToolCategory.TEXT,
            keywords = listOf("时间戳", "unix", "timestamp", "日期"), priority = 0,
        ) { onBack -> TimestampScreen(onBack) },

        ToolDef(
            id = "countdown", title = "倒数日", description = "倒数日 · 纪念日 · 农历",
            icon = ToolIcons.countdown, category = ToolCategory.LIFE,
            keywords = listOf(
                "倒数", "纪念日", "countdown", "days", "生日", "农历", "周年",
                "倒计时", "大姨妈", "预产期", "考试", "截止", "birthday", "anniversary", "lunar",
                "days matter", "正数日", "节日",
            ),
            priority = 0,
        ) { onBack -> CountdownScreen(onBack) },

        ToolDef(
            id = "pomodoro", title = "番茄钟", description = "专注计时 25+5",
            icon = ToolIcons.pomodoro, category = ToolCategory.LIFE,
            keywords = listOf("番茄", "专注", "pomodoro", "计时"), priority = 0,
        ) { onBack -> PomodoroScreen(onBack) },

        // ---- P1 tools ----

        ToolDef(
            id = "bmi", title = "BMI 计算器", description = "身体质量指数与健康体重",
            icon = ToolIcons.bmi, category = ToolCategory.CALCULATE,
            keywords = listOf("bmi", "体重", "身高", "健康"), priority = 1,
        ) { onBack -> BmiScreen(onBack) },

        ToolDef(
            id = "color_picker", title = "取色器", description = "图片取色 RGB/HEX",
            icon = ToolIcons.color, category = ToolCategory.IMAGE,
            keywords = listOf("取色", "颜色", "color", "hex"), priority = 1,
        ) { onBack -> ColorPickerScreen(onBack) },

        ToolDef(
            id = "stitch", title = "图片拼接", description = "多图拼成长图",
            icon = ToolIcons.stitch, category = ToolCategory.IMAGE,
            keywords = listOf("拼接", "长图", "stitch", "合并"), priority = 1,
        ) { onBack -> StitchScreen(onBack) },

        ToolDef(
            id = "text_diff", title = "文本差异对比", description = "两段文本逐行 diff",
            icon = ToolIcons.diff, category = ToolCategory.TEXT,
            keywords = listOf("差异", "对比", "diff", "比较"), priority = 1,
        ) { onBack -> TextDiffScreen(onBack) },

        ToolDef(
            id = "password_gen", title = "密码生成器", description = "安全随机密码",
            icon = ToolIcons.passwordGen, category = ToolCategory.SECURITY,
            keywords = listOf("密码", "随机", "生成", "password"), priority = 1,
        ) { onBack -> PasswordGenScreen(onBack) },

        ToolDef(
            id = "ruler", title = "尺子", description = "屏幕直尺 cm/inch",
            icon = ToolIcons.ruler, category = ToolCategory.MEASURE,
            keywords = listOf("尺子", "测量", "ruler", "长度"), priority = 1,
        ) { onBack -> RulerScreen(onBack) },

        ToolDef(
            id = "level", title = "水平仪", description = "气泡水平仪",
            icon = ToolIcons.level, category = ToolCategory.MEASURE,
            keywords = listOf("水平", "倾角", "level", "气泡"), priority = 1,
        ) { onBack -> LevelScreen(onBack) },

        ToolDef(
            id = "compass", title = "指南针", description = "方位角与朝向",
            icon = ToolIcons.compass, category = ToolCategory.MEASURE,
            keywords = listOf("指南针", "方向", "compass", "方位"), priority = 1,
        ) { onBack -> CompassScreen(onBack) },

        // ---- 需要主密码 / 较重的能力 ----
        ToolDef(
            id = "password_vault", title = "密码箱", description = "主密码加密保管账号密码",
            icon = ToolIcons.lock, category = ToolCategory.SECURITY,
            keywords = listOf("密码", "密码箱", "vault", "保管"), priority = 1,
        ) { onBack -> PasswordVaultScreen(onBack) },

        ToolDef(
            id = "image_compress", title = "图片压缩", description = "质量/格式调节压缩图片",
            icon = ToolIcons.compress, category = ToolCategory.IMAGE,
            keywords = listOf("压缩", "图片", "compress", "体积"), priority = 1,
        ) { onBack -> ImageCompressScreen(onBack) },

        ToolDef(
            id = "watermark", title = "图片加水印", description = "文字水印 单枚/平铺",
            icon = ToolIcons.watermark, category = ToolCategory.IMAGE,
            keywords = listOf("水印", "watermark", "签名"), priority = 1,
        ) { onBack -> WatermarkScreen(onBack) },

        ToolDef(
            id = "perler", title = "拼豆图纸", description = "图片转拼豆图纸与耗材清单",
            icon = ToolIcons.perler, category = ToolCategory.IMAGE,
            keywords = listOf("拼豆", "图纸", "像素", "perler"), priority = 1,
        ) { onBack -> PerlerScreen(onBack) },

        ToolDef(
            id = "kinship", title = "亲戚称呼计算", description = "三姑六婆称呼一键算",
            icon = ToolIcons.kinship, category = ToolCategory.LIFE,
            keywords = listOf("亲戚", "称呼", "过年", "kinship"), priority = 1,
        ) { onBack -> KinshipScreen(onBack) },

        ToolDef(
            id = "period", title = "经期记录", description = "周期记录、日历预测与趋势",
            icon = ToolIcons.period, category = ToolCategory.LIFE,
            keywords = listOf(
                "经期", "生理期", "周期", "period", "月经", "例假", "大姨妈", "姨妈",
                "安全期", "排卵", "排卵期", "易孕期", "备孕",
            ),
            priority = 1,
        ) { onBack -> PeriodScreen(onBack) },

        // ---- P2 tools ----
        ToolDef(
            id = "money", title = "金额大写", description = "数字转中文大写金额",
            icon = ToolIcons.money, category = ToolCategory.CALCULATE,
            keywords = listOf("金额", "大写", "发票", "报销"), priority = 2,
        ) { onBack -> MoneyScreen(onBack) },

        ToolDef(
            id = "decision", title = "做个决定", description = "转盘抽签帮你选",
            icon = ToolIcons.decision, category = ToolCategory.LIFE,
            keywords = listOf("决定", "转盘", "抽签", "随机"), priority = 2,
        ) { onBack -> DecisionScreen(onBack) },

        ToolDef(
            id = "danmaku", title = "手持弹幕", description = "LED 全屏滚动文字",
            icon = ToolIcons.danmaku, category = ToolCategory.LIFE,
            keywords = listOf("弹幕", "LED", "滚动", "应援"), priority = 2,
        ) { onBack -> DanmakuScreen(onBack) },

        ToolDef(
            id = "device", title = "设备信息", description = "CPU/内存/屏幕/电池一览",
            icon = ToolIcons.device, category = ToolCategory.MEASURE,
            keywords = listOf("设备", "信息", "CPU", "内存", "电池"), priority = 2,
        ) { onBack -> DeviceScreen(onBack) },
    )

    /** 目前全部工具都已实现；保留过滤入口以便将来灰度隐藏未完成项。 */
    val visible: List<ToolDef> get() = all

    fun byId(id: String): ToolDef? = all.firstOrNull { it.id == id }

    fun byCategory(category: ToolCategory): List<ToolDef> =
        visible.filter { it.category == category }.sortedBy { it.title }

    fun search(query: String): List<ToolDef> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return visible.filter { tool ->
            tool.title.lowercase().contains(q) ||
                tool.description.lowercase().contains(q) ||
                tool.keywords.any { it.lowercase().contains(q) }
        }
    }
}
