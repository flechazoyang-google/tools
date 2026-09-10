package com.flechazo.toolbox.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 形状尺度与语义（规范：`docs/UI_VISUAL_DIRECTION.md` §4.2）。
 *
 * 之前形状是散落在各处的魔法数（卡片 18、chip 10、输入 14、底栏 20），
 * 非标值会让组件看起来"各长各的"。这里收敛成一条尺度。
 *
 * 形状要表达状态，不只是圆角（Expressive 的核心思路）：
 * - 分段控件选中项 = [full]（胶囊），未选中 = 透明
 * - 可点击卡片按压时 [lg] → [xl] 微形变（配合 [ToolMotion.spatialFast]）
 * - FAB 展开菜单从 [full] 形变为 [xl]
 */
object ToolShape {
    /** 4dp · 微元素：进度条、色条、小徽章 */
    val xs = RoundedCornerShape(4.dp)

    /** 8dp · 小控件：图标底片、chip、键帽 */
    val sm = RoundedCornerShape(8.dp)

    /** 12dp · 中控件：按钮、下拉菜单项、Toast、列表行 */
    val md = RoundedCornerShape(12.dp)

    /** 16dp · 卡片：分组卡、结果卡、输入框 */
    val lg = RoundedCornerShape(16.dp)

    /** 24dp · 大容器：底部抽屉、Hero 卡、图片预览 */
    val xl = RoundedCornerShape(24.dp)

    /** 全圆：悬浮操作条、FAB、分段控件、搜索栏 */
    val full = RoundedCornerShape(percent = 50)
}

/**
 * 映射进 `MaterialTheme.shapes`，让第三方 M3 组件也吃到同一套尺度。
 * 取值与 M3 默认值基本一致（仅 extraLarge 28 → 24），所以是低风险替换。
 */
internal val ToolShapes = Shapes(
    extraSmall = ToolShape.xs,
    small = ToolShape.sm,
    medium = ToolShape.md,
    large = ToolShape.lg,
    extraLarge = ToolShape.xl,
)
