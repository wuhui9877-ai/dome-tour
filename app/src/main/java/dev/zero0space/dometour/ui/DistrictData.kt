package dev.zero0space.dometour.ui

import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.math.Color4
import dev.zero0space.dometour.scene.rgb

/**
 * The four ring districts of the dome city. All names, copy, and palettes are
 * original to this project — invented for the tour, not taken from any other
 * work.
 */
data class District(
    /** Tour stop order; also the index used by the UI and the scene. */
    val index: Int,
    val name: String,
    val englishName: String,
    /** Position on the ring, degrees, 0 = +X, increasing toward +Z. */
    val angleDeg: Float,
    /** Accent color: glow material, highlight ring, UI chips. */
    val accent: Color4,
    /** Main building-body color. */
    val body: Color4,
    /** Darker building-body variant for variety. */
    val bodyDark: Color4,
    val flavor: String,
    val facts: List<String>
)

val DISTRICTS = listOf(
    District(
        index = 0,
        name = "雾港",
        englishName = "Fog Harbor",
        angleDeg = 45f,
        accent = rgb(0x2ED8C8),
        body = rgb(0x3E6E78),
        bodyDark = rgb(0x2A4A52),
        flavor = "雾气终年不散的空港码头，飞艇在这里停靠、补给，再钻进云里。",
        facts = listOf(
            "泊位 36 座，飞艇进出港全靠雾钟指引",
            "雾钟每日鸣响四次，整座环城都能听见",
            "潮汐电梯从码头直通环形轨道"
        )
    ),
    District(
        index = 1,
        name = "灯市",
        englishName = "Lantern Market",
        angleDeg = 135f,
        accent = rgb(0xFFB43A),
        body = rgb(0x7A5A38),
        bodyDark = rgb(0x54402A),
        flavor = "整座环城最亮的夜市，灯笼从黄昏亮到天明，摊位比星星还密。",
        facts = listOf(
            "摊位 218 间，灯笼 3000 盏",
            "夜市营业至凌晨三点，风雨无休",
            "最出名的是会发光的糖水铺招牌"
        )
    ),
    District(
        index = 2,
        name = "弦塔",
        englishName = "String Towers",
        angleDeg = 225f,
        accent = rgb(0xA678FF),
        body = rgb(0x584A7A),
        bodyDark = rgb(0x3C3355),
        flavor = "高塔之间拉满发光的弦，风过时整座城区都在低鸣，像一台竖琴。",
        facts = listOf(
            "主塔高 188 米，是穹顶下最高的建筑",
            "弦线共 49 根，全部由发光纤维织成",
            "每逢整点，诸塔合鸣一次"
        )
    ),
    District(
        index = 3,
        name = "静庭",
        englishName = "Quiet Court",
        angleDeg = 315f,
        accent = rgb(0x8FF2A0),
        body = rgb(0x5A7A64),
        bodyDark = rgb(0x3E5646),
        flavor = "穹顶下唯一听不见引擎声的地方，只有水声、风声和脚步声。",
        facts = listOf(
            "庭中有三眼活泉，泉水来自穹顶冷凝",
            "无人机禁入，信使改走地面通道",
            "晨雾时分最安静，是读信的好时候"
        )
    )
)

/** Day-night phases of the dome cycle. The loop runs ~60s on auto-cycle. */
data class DayPhase(
    val name: String,
    /** Dome shell tint. */
    val domeColor: Color4,
    /** Dome shell opacity. */
    val domeOpacity: Float,
    /** Window-light glow multiplier for non-selected districts. */
    val glow: Float,
    /** Compose background color behind the 3D view. */
    val skyColor: Color,
    val description: String
)

val DAY_PHASES = listOf(
    DayPhase(
        name = "晨",
        domeColor = rgb(0xFFB98A),
        domeOpacity = 0.16f,
        glow = 0.55f,
        skyColor = Color(0xFF2A2530),
        description = "晨光初上，穹顶泛起玫瑰金色。"
    ),
    DayPhase(
        name = "昼",
        domeColor = rgb(0xA8D8FF),
        domeOpacity = 0.10f,
        glow = 0.30f,
        skyColor = Color(0xFF232830),
        description = "正午的穹顶几乎透明，城市一览无余。"
    ),
    DayPhase(
        name = "暮",
        domeColor = rgb(0xF2995A),
        domeOpacity = 0.20f,
        glow = 0.75f,
        skyColor = Color(0xFF241E2C),
        description = "暮色把穹顶染成琥珀色，灯火次第亮起。"
    ),
    DayPhase(
        name = "夜",
        domeColor = rgb(0x5A73E6),
        domeOpacity = 0.30f,
        glow = 1.0f,
        skyColor = Color(0xFF14121E),
        description = "夜色里穹顶深蓝，霓虹成为主角。"
    )
)
