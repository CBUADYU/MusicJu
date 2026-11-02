package com.musicapp.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 荣耀 MagicOS UIKit 设计 tokens
 * 参考: https://hnr.yun.hihonor.com/uikit/#/zh-CN/component/introduction
 *
 * 所有数值均源自官方规范的 --hnr-* CSS 变量。
 */
object HnrRadius {
    // 通用圆角
    val XS: Dp = 4.dp    // --hnr-default-corner-radius-xs
    val S:  Dp = 8.dp    // --hnr-default-corner-radius-s
    val M:  Dp = 12.dp   // --hnr-default-corner-radius-m (卡片通用)
    val L:  Dp = 16.dp   // --hnr-default-corner-radius-l
    val XL: Dp = 22.dp   // --hnr-default-corner-radius-xl

    // 控件专用
    val ButtonLarge: Dp = 18.dp   // --hnr-button-corner-radius (大按钮)
    val ButtonSmall: Dp = 14.dp   // --hnr-small-button-corner-radius
    val Toggle:      Dp = 14.dp   // --hnr-toggle-corner-radius
    val Chips:       Dp = 14.dp   // --hnr-chips-corner-radius
    val SwitchBar:   Dp = 12.dp   // --hnr-switch-bar-corner-radius
    val InputBox:    Dp = 18.dp   // --hnr-input-box-corner-radius
    val Dialog:      Dp = 22.dp   // --hnr-dialog-corner-radius
    val PopWindow:   Dp = 16.dp   // --hnr-pop-window-corner-radius
    val Toast:       Dp = 18.dp   // --hnr-corner_radius_toast
    val Tooltips:    Dp = 10.dp   // --hnr-tooltips-corner-radius
    val Badge:       Dp = 14.dp   // --hnr-corner_radius_badge

    // 卡片专用
    val Card: Dp = 12.dp        // --hnr-card-corner-radius / Banner / Notification
    val ListCard: Dp = 12.dp    // --hnr-list-card-corner-radius
}

object HnrSpacing {
    // 通用间距
    val XS: Dp = 2.dp    // --hnr-elements-margin-vertical-XS
    val S:  Dp = 4.dp    // --hnr-elements-margin-vertical-S
    val M:  Dp = 8.dp    // --hnr-elements-margin-vertical-M
    val M2: Dp = 12.dp   // --hnr-elements-margin-vertical-M2
    val L:  Dp = 16.dp   // --hnr-elements-margin-vertical-L
    val L2: Dp = 24.dp   // --hnr-elements-margin-vertical-L2
    val XL: Dp = 32.dp   // --hnr-element-margin-vertical-XL
    val XXL: Dp = 48.dp  // --hnr-elements-margin-vertical-XXL

    // 屏幕边距（MagicOS 推荐热区）
    val ScreenStart: Dp = 20.dp   // --hnr-magic-dimens-default-start
    val ScreenEnd:   Dp = 20.dp   // --hnr-magic-dimens-default-end
    val ScreenTop:   Dp = 24.dp   // --hnr-default-padding-top
    val ScreenBottomFixed: Dp = 24.dp // --hnr-default-padding-bottom-fixed

    // 卡片间距（MagicOS）
    val CardStart: Dp = 18.dp    // --hnr-magic-dimens-card-start
    val CardEnd:   Dp = 18.dp    // --hnr-magic-dimens-card-end
    val CardMiddle: Dp = 12.dp   // --hnr-magic-dimens-listcard-middle-2
    val ListCardPaddingStart: Dp = 12.dp  // --hnr-list-card-padding-start
    val ListCardPaddingTop:   Dp = 2.dp   // --hnr-list-card-padding-top
}

object HnrElevation {
    // 荣耀规范通过 color-control-normal 系列背板实现层级，
    // 这里提供标准阴影值作为补充
    val None: Dp = 0.dp
    val Low:  Dp = 1.dp
    val Mid:  Dp = 2.dp
    val High: Dp = 4.dp
}
