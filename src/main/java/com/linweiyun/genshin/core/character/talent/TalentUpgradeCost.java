package com.linweiyun.genshin.core.character.talent;

import com.linweiyun.genshin.core.character.PGCharacterData;

/**
 * 天赋升级消耗表。
 *
 * <h2>为什么单独一个类</h2>
 * 消耗<b>必须按「手动升级次数」查表，不能按「有效等级」</b>。
 * 有效等级里混着命座/天赋加成：手动升到 4 级（3 次）后抽到 3 命（战技 +3）会显示 7 级，
 * 但玩家真正手动升过的只有 3 次，下一次消耗的必须是「4→5」那一档，
 * 而不是「7→8」那一档。所以这里的入参统一是 {@code manualUpgrades}（0-based 的手动次数）。
 *
 * <h2>现在还是扁平表</h2>
 * 两张表的每一项都填成同一个值（原石 320 / 玩家经验等级 5），只是索引换成了手动次数。
 * 要做分档就直接改这两个数组：
 * <pre>
 *   下标 0 → 手动第 1 次（1 级 → 2 级）
 *   下标 1 → 手动第 2 次（2 级 → 3 级）
 *   …
 *   下标 {@link PGCharacterData#MAX_MANUAL_TALENT_UPGRADES} - 1 → 最后一次（9 级 → 10 级）
 * </pre>
 */
public final class TalentUpgradeCost {

    private TalentUpgradeCost() {
    }

    /** 每次手动升级要的原石（下标 = 手动次数，见类注释）。 */
    private static final int[] PRIMOGEM = {
            320, 320, 320, 320, 320, 320, 320, 320, 320
    };

    /** 每次手动升级要扣的「玩家经验等级」（原版经验等级，不是原石）。 */
    private static final int[] EXPERIENCE_LEVELS = {
            5, 5, 5, 5, 5, 5, 5, 5, 5
    };

    /** 这一次（第 {@code manualUpgrades + 1} 次手动升级）要的原石。 */
    public static int primogem(int manualUpgrades) {
        return at(PRIMOGEM, manualUpgrades);
    }

    /** 这一次要扣的玩家经验等级。 */
    public static int experienceLevels(int manualUpgrades) {
        return at(EXPERIENCE_LEVELS, manualUpgrades);
    }

    private static int at(int[] table, int manualUpgrades) {
        int index = Math.max(0, Math.min(table.length - 1, manualUpgrades));
        return table[index];
    }
}
