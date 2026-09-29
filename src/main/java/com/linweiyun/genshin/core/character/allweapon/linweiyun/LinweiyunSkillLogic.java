package com.linweiyun.genshin.core.character.allweapon.linweiyun;

import com.linweiyun.genshin.core.character.util.appearance.WeaponAppearance;

import java.util.Map;

/**
 * 林薇云的技能<b>工具与常量</b> —— 普攻 / 战技 / 爆发的实现已分别移至
 * {@code attack/} 子包下各自的独立类，此处只保留双 Skill 类共用的：
 *
 * <ul>
 *   <li>{@link #flyStartTicks(WeaponAppearance)}</li>
 *   <li>{@link #maxCombo()}</li>
 * </ul>
 *
 * <p>此包级别为 {@code package-private}，只对本包中的 Skill 类开放。
 */
final class LinweiyunSkillLogic {

    // ==================== 起飞前摇 ====================

    /**
     * <b>二连跳起飞前摇的刻数</b> —— 六种武器形态各一档。
     *
     * <p>用户口径：「这个时间每个角色不一样，林薇云有六种，也就是每种武器一样」。
     * 具体数值还没给，所以六档先<b>同值占位</b>；拿到正式数值只改这一张表。
     */
    /** 六档同值的那份占位（等用户给逐形态数值）。 */
    public static final int DEFAULT_FLY_START_TICKS = 8;

    private static final Map<WeaponAppearance, Integer> FLY_START_TICKS = Map.of(
            WeaponAppearance.FIST, DEFAULT_FLY_START_TICKS,
            WeaponAppearance.SWORD, DEFAULT_FLY_START_TICKS,
            // 长柄：前摇动画 `fly_start_polearm` 是用户自己做的上扫帚那一段，素材长 3.125 秒，
            // 所以这一档取 63 刻（62.5 刻，往上取整）——必须 ≥ 素材长度，
            // 不然动画没播完就被飞行动画接走了。
            // 其余五档还是占位，等各自的起手动做出好了再逐个改。
            WeaponAppearance.POLEARM, 63,
            WeaponAppearance.CLAYMORE, DEFAULT_FLY_START_TICKS,
            WeaponAppearance.CATALYST, DEFAULT_FLY_START_TICKS,
            WeaponAppearance.BOW, DEFAULT_FLY_START_TICKS);

    /** 这个形态的起飞前摇多少刻。 */
    static int flyStartTicks(WeaponAppearance form) {
        return FLY_START_TICKS.getOrDefault(form, DEFAULT_FLY_START_TICKS);
    }

    // ==================== 普攻段数 ====================

    /**
     * 普攻段数 —— <b>3</b> 段。
     *
     * <p>⚠️ 林薇云现在的动画文件里只有移动那几条（idle / walk / run / jump / fly…），
     * <b>还没有普攻 / 战技 / 爆发的动画</b>，所以这几段暂时只有伤害数字、人是站着不动的。
     * 补上动画后如果要改段数，这里和 {@link com.linweiyun.genshin.config.character.LinweiyunTalentConfig} 的倍率表一起改。
     */
    static int maxCombo() {
        return 3;
    }

    private LinweiyunSkillLogic() {
    }
}