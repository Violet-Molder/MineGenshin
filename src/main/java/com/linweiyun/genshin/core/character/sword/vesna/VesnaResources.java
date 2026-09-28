package com.linweiyun.genshin.core.character.sword.vesna;
import com.linweiyun.genshin.core.system.combat.action.data.ComboData;
import com.linweiyun.genshin.core.system.combat.action.data.SkillData;
import com.linweiyun.genshin.core.system.combat.action.data.BurstData;
import com.linweiyun.genshin.core.system.combat.action.data.DodgeData;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.core.system.combat.action.data.Engagement;
import com.linweiyun.genshin.core.system.combat.action.data.Hit;
import com.linweiyun.genshin.core.system.combat.action.data.Move;
import com.linweiyun.genshin.core.system.combat.action.data.SoundRef;

import com.linweiyun.genshin.core.system.combat.action.data.BoneMountSource;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterBoneMount;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VesnaResources {

    public static final String ID = Vesna.ID;

    /**
     * 渲染数据 —— 模型和贴图走共用目录，动画是本角色独立的：
     * <pre>
     * character/default/default.geo.json      ← 所有角色共用
     * character/default/default.png           ← 所有角色共用
     * character/vesna/vesna.animation.json    ← 薇斯娜自己的
     * </pre>
     */
    public static final CharacterRenderData RENDER_DATA = CharacterRenderData.character(
            ID,
            CharacterRenderData.defaultAnimMapping(),
            1.0f,
            // 模型里剑鞘和右手剑身是分开的骨骼：
            // 剑身(blade_right)换成实际装备武器的整把模型；槽位为空时保持原样。
            // 想把「一剑两骨」拆开挂，把下面这行换成：
            //     CharacterBoneMount.of("blade_right", BoneMountSource.weaponSubBone("blade")),
            //     CharacterBoneMount.of("shealth",     BoneMountSource.weaponSubBone("sheath"))
            CharacterBoneMount.of("blade_right")
    )
            // 第一人称动画单独一个文件：主文件只查不到的名字才会来这里找
            .withAnimationFile("character/vesna/vesna_fp.animation.json")
            // 大招「跃起下坠刺击」的动画也单独一个文件（burst_dive），
            // 不去动主文件里的 "final" —— 见 buildActionData() 里大招那一段
            .withAnimationFile("character/vesna/vesna_burst.animation.json");

    public static final CharacterActionData ACTION_DATA = buildActionData();

    /**
     * 满命「翔风剑·变移」那一段的时序。
     */
    public static final int BIANYI_HIT_DELAY = 6;

    public static final ActionStep BIANYI_STEP = new ActionStep(
            "skill_bianyi", 20, BIANYI_HIT_DELAY + 2, 4,
            List.of(new Move(0, 0.8)),
            List.of(new Hit(BIANYI_HIT_DELAY, 0.0, 1.0, 2.5)),
            List.of(new SoundRef(BIANYI_HIT_DELAY, "vesna_skill", 1.0f, 1.0f)),
            0, 0, 0, 0
    );

    private static CharacterActionData buildActionData() {
        Map<Integer, ActionStep> comboSteps = new LinkedHashMap<>();
        comboSteps.put(1, new ActionStep(
                "attack_1", 40, 0, 2,
                List.of(),
                List.of(new Hit(3, 0.0, 1.5, 6.0)),
                List.of(new SoundRef(3, "vesna_attack_1", 1.0f, 1.0f)),
                0, 2, 0, 8
        ));
        comboSteps.put(2, new ActionStep(
                "attack_2", 48, 0, 2,
                List.of(),
                List.of(new Hit(3, 0.0, 1.5, 6.0)),
                List.of(new SoundRef(3, "vesna_attack_2", 1.0f, 1.0f)),
                0, 2, 0, 8
        ));
        comboSteps.put(3, new ActionStep(
                "attack_3", 25, 0, 2,
                List.of(),
                List.of(
                        new Hit(3, 0.0, 1.5, 8.0),
                        new Hit(6, 0.0, 1.5, 10.0)
                ),
                List.of(new SoundRef(3, "vesna_attack_3", 1.0f, 1.0f)),
                0, 3, 0, 8
        ));
        comboSteps.put(4, new ActionStep(
                "attack_4", 30, 0, 2,
                List.of(),
                List.of(
                        new Hit(10, 0.0, 1.5, 8.0),
                        new Hit(12, 0.0, 1.5, 8.0),
                        new Hit(14, 0.0, 1.5, 8.0),
                        new Hit(16, 0.0, 1.5, 8.0)
                ),
                List.of(new SoundRef(10, "vesna_attack_4", 1.0f, 1.0f)),
                0, 4, 0, 8
        ));
        comboSteps.put(5, new ActionStep(
                "attack_5", 50, 0, 2,
                List.of(),
                List.of(
                        new Hit(2, 0.0, 1.5, 10.0),
                        new Hit(4, 0.0, 1.5, 10.0),
                        new Hit(6, 0.0, 1.5, 10.0),
                        new Hit(8, 0.0, 1.5, 10.0)
                ),
                List.of(new SoundRef(2, "vesna_attack_5", 1.0f, 1.0f)),
                0, 5, 0, 10
        ));
        ActionStep attack6Step = new ActionStep(
                // 第 6 段直接复用第 2 段的完整动画。
                //
                // 原来这里写的是 air_attack_long + 收尾 air_attack_end ——
                // 那两个其实是「第 2 段被拆成两半」的产物：long 是攻击本体、end 是收尾。
                // 拆开用就得额外接一次收尾状态，中间还夹一段很长的保持（身体 0.5 秒就到位了，
                // 后面全靠 hold 撑满 7 秒），而第 6 段根本不需要那么长的恢复。
                // 直接播完整的第 2 段动画：动作连贯、时长和动画天然对齐，也不用收尾机制。
                "attack_2", 48, 0, 2,
                List.of(),
                List.of(
                        new Hit(5, 0.0, 1.5, 12.0),
                        new Hit(10, 0.0, 1.5, 12.0),
                        new Hit(15, 0.0, 1.5, 12.0),
                        new Hit(20, 0.0, 1.5, 14.0)
                ),
                List.of(new SoundRef(5, "vesna_attack_6", 1.0f, 1.0f)),
                0, 6, 0, 12
        );
        comboSteps.put(6, attack6Step);

        // ─── 吸附参数怎么定：默认全用全局值，只有「手感不对」的那一段才逐招覆盖 ───
        //
        // 判定链（写在 AttackApproach.stepToward 上方）：
        //     生效范围 = 这一招的 attackRange + Engagement.adhesionBand（默认 1.5 格）
        //     一步力度 = Engagement.adhesionStep（默认 0.22 冲量 ≈ 半个格）
        // 默认值是按「补上对手挪开的那半步」定的，薇斯娜这几段用默认就够 ——
        // 她的 moves 只有 1.2~2.0 的位移，攻击距离 3 格，节奏也快。
        //
        // 真要按段调，就这样（不改默认，只改那一段）：
        //     attack6Step.withEngagement(Engagement.melee().withAdhesion(2.0, 0.30));
        //     // 第 6 段是大收招，吸得宽一点、一步沉一点，打完不容易被溜掉
        //
        // 反例（什么时候要关掉）：如果以后给她加一段「原地蓄力炮」，
        // 那一段就该 withAdhesion(0, 0) —— 蓄力时被往前带会很难看。

        ComboData combo = new ComboData(6, comboSteps);

        // ─── 战技（E）：巡风列装入门 + 翔风剑，共用这一份时序 ───
        //
        // 三窗口（图见 ActionStep#protectDuration）：
        //     0            8                    20
        //     ├── 执行期 ──┼────── 后摇 ─────────┤
        //       不可打断        可取消
        //
        // ① prepareTicks = 0：翔风剑没有「准备阶段」。
        //    按下去就是位移 + 动画，那个位移本身就是技能在执行（不是前摇），
        //    所以触发即进入执行期。
        // ② protectDuration = 8：执行期盖住伤害点（hits[].delay = 6）+ 2 刻余量。
        //    这段时间内外都打不断它 —— 挨打、跳跃、走开都不行。
        //    之前这里是 0（整段可打断），表现就是「每一个都被打断、
        //    剑气不扣但技能也没打出来」，前摇被打断还会连模式都进不去。
        // ③ 8 刻之后是后摇（12 刻）：伤害已经结算完，随便取消 —— 连招手感靠它。
        //
        // 想改成「有吟唱」的角色：withPrepareTicks(n) 把 n 刻划成可打断的准备阶段即可。
        ActionStep skillStep = new ActionStep(
                "skill_no_energy", 20, 8, 3,
                List.of(),
                List.of(new Hit(6, 2.0, 1.0, 2.5)),
                List.of(new SoundRef(6, "vesna_skill", 1.0f, 1.0f)),
                0, 0, 360, 0
        );

        // ─── 大招 Q：跃起下坠刺击（60 能量）───
        //
        // 动画 "burst_dive" 在**独立文件** character/vesna/vesna_burst.animation.json 里
        // （原来的 "final" 留在主动画文件里不动）。它只负责「跳起来 + 转成下坠姿态」；
        // 飞出去的过程由客户端 BurstDive 驱动：跃起 8 刻 → 第 8 刻锁落点 → 下坠 10 刻。
        //
        // 伤害点写在 8 + 10 = 18 刻（就是 DiveBurst.landTick()，同一个数的两种写法），
        // 这样落地伤害和「人砸到地上」是同一帧。
        //
        // 索敌：大招锁得远一点（16 格），但**不要突进** ——
        // 接近这件事由下坠本身完成，再来一次贴脸突进就重复了。
        ActionStep burstStep = new ActionStep(
                "burst_dive", 40, 40, 4,
                List.of(),
                List.of(new Hit(18, 0.0, 0.0, 12.0)),
                List.of(new SoundRef(18, "vesna_burst", 1.0f, 1.0f)),
                0, 0, 400, 0
        )
                .withDiveBurst(8, 10, 2.5, 16.0)
                .withEngagement(Engagement.melee().withDash(false)
                        .withAcquireRange(16).withKeepRange(20));

        ActionStep dodgeStep = new ActionStep(
                // 基准名；实际播放时按输入方向换成 dodge_front / dodge_back / dodge_left / dodge_right
                "dodge_front", 12, 0, 3,
                List.of(new Move(0, 2.0)),
                List.of(),
                List.of(new SoundRef(0, "vesna_dodge", 1.0f, 1.0f)),
                0, 0, 0, 0
        );

        return new CharacterActionData(
                combo,
                new SkillData(skillStep, null),
                // 大招 60 能量（原来 80）
                new BurstData(burstStep, 60f),
                new DodgeData(dodgeStep)
        );
    }
}