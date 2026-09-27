package com.linweiyun.genshin.core.character.polearm.shenhe;
import com.linweiyun.genshin.core.system.combat.action.data.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 申鹤的动作数据表。
 *
 * <h2>为什么她必须有一份自己的表</h2>
 * 没有 {@code getActionData()} 覆写的角色会走
 * {@link CharacterActionData#fallback(int) 通用兜底表}，而兜底表给<b>每一段</b>
 * 都塞了一个 {@code Move(0, 1.0)} 的服务端冲量。对申鹤来说这是错的：
 * <b>她的位移完全由自己的 {@code DashSystem} 负责</b>
 * （{@code RushesForward} 算方向 → 客户端 {@code startDash} 每刻 {@code move}
 * → 服务端 {@code startDamageDash} 沿途扫伤害）。
 *
 * <p>两者同帧打架的结果就是「E 不放突刺了、只剩一下小幅前移」——
 * 服务端的冲量盖掉了客户端的突刺。所以这里的战技两段 {@code moves} 必须是空的。
 *
 * <h2>长按为什么也要在这里写</h2>
 * {@code SkillBase.buildDefaultActionSet} 只在 {@code skill.hold() != null} 时才注册
 * {@code ELEMENTAL_SKILL_HOLD}；兜底表的 {@code SkillData} 是 {@code (step, null)}，
 * 于是客户端的短/长按判定（短 CD ≠ 长 CD 才等长按）通过之后，
 * {@code set.getElementalSkillHold()} 是 {@code null} → 长按什么都不发生，
 * 而且松手时也不会补一个短按（长按已经触发过了）。申鹤的短 CD 200 / 长 CD 300
 * 本来就是两套，所以这里补上 hold 那一段就能直接把她的长按效果接回来。
 *
 * <p>普攻连段也在这里写了 —— 三段分别播 {@link #COMBO_ANIMATIONS}
 * （{@code sword_idle_attack_01/02/03}，就是带 {@code Root} 与双腿、会迈步转身的那一版），
 * 第三段挂收尾 {@link #COMBO_END_ANIMATION}。
 * 原来这里沿用兜底表给的 {@code attack_1..3} 名字，而动画文件里<b>根本没有这三个 key</b>
 * （YSM 搬过来的是 {@code sword_attack_0x} / {@code sword_idle_attack_0x} 两套），
 * {@code AnimationAvailability} 把不存在的名字拦下之后就是玩家说的
 * 「伤害数字在跳、人站着不动」。
 *
 * <p>普攻连段数固定成 {@value #MAX_COMBO} 段 —— 必须和
 * {@link ShenheSkill#getMaxCombo()} 一致（{@code SkillBase.buildDefaultActionSet} 取两者较小值）。
 * 段数从 5 改成 3 是动画决定的：木偶这套普攻只有三段。
 */
public final class ShenheResources {

    private ShenheResources() {
    }

    public static final String ID = Shenhe.ID;

    /**
     * 普攻连段数 —— 必须和 {@link ShenheSkill#getMaxCombo()} 一致
     * （{@code SkillBase.buildDefaultActionSet} 取两者的较小值）。
     */
    public static final int MAX_COMBO = 3;

    // ==================== 普攻三段 ====================

    /**
     * 三段普攻各自的动画名。
     *
     * <p><b>为什么选 {@code sword_idle_attack_*} 而不是 {@code sword_attack_*}</b>：
     * 动画文件里这两套是同一套上半身动作的两个版本 ——
     * <ul>
     *   <li>{@code sword_attack_01/02/03}：只写 8~12 根骨骼，<b>没有 {@code Root}、没有任何腿</b>，
     *       上半身在挥、下半身钉死在地上 —— 就是「身子不怎么动」的那一版；</li>
     *   <li>{@code sword_idle_attack_01/02/03}：写全 16~17 根骨骼，含 {@code Root} 的转身/位移
     *       与两条腿的弓步、落地 —— 参照视频里她是<b>边转身边迈步</b>的。</li>
     * </ul>
     * 两套的上半身数值几乎一样，差的正好就是「引擎里最显眼的那半边」，
     * 所以引擎这一侧要的点名就是带腿的这版。
     *
     * <p><b>这三段之间不是「姿势接姿势」，是「各自独立、靠引擎硬切相连」</b>
     * （{@code tools/verify-shenhe-anim.py} 的 ⑤ 会把每次切换的实际差值打出来）：
     * 三段动画的<b>上半身收尾都回到站姿</b>（胳膊、腿、躯干的 delta 全 0，
     * 只有剑/挂点/根骨留一点偏移），而下一段的起手是各自的姿势 ——
     * {@code _01} 与 {@code _02} 共用同一套胳膊起手，{@code _03} 是转身横扫那一套。
     * 所以段间切换读起来是「收刀 → 立刻起下一刀」。
     *
     * <p>引擎对动作动画一律硬切（名字在 {@code ShenheAnimations.SPECIAL_ANIMS} 里，
     * 过渡 0 刻），本来就该是这个观感：按顺序点就是三刀连打。
     * 想让三段之间也平滑混形，得改素材（把每段的末帧改成下一段的起手帧），
     * 或者在配置里把这三段从 {@code specialAnims} 里拿掉 —— 后者会让姿势被 5 刻插值混形，
     * 出现「两臂两腿各走各的」，不要那么干。
     */
    private static final String[] COMBO_ANIMATIONS = {
            "sword_idle_attack_01",
            "sword_idle_attack_02",
            "sword_idle_attack_03",
    };

    /**
     * 三段普攻各自的时长（刻）。
     *
     * <p>对着动画文件的 {@code animation_length} 写：1.0 / 1.0 / 0.875 秒
     * （GeckoLib 是 1 刻 = 0.05 秒，也就是 20 / 20 / 17.5 刻）。
     * 第三段进一位到 18 刻，让 0.875 秒那最后半刻不被砍掉。
     *
     * <p><b>时长必须 ≥ 动画长度</b>：动作动画用 {@code thenPlayAndHold} 播，
     * 状态机到 {@code duration} 刻就切走 —— 写短了就是「最后几帧看不到」。
     */
    private static final int[] COMBO_DURATIONS = {20, 20, 18};

    /**
     * 三段普攻的伤害点（刻）—— 卡在刀刃真正扫过身前的那一帧，见下面每段的注释。
     *
     * <p>{@code ShenheSkill.attack} 的伤害是<b>每个伤害点结算一次</b>
     * （{@code ActionState.fireDamagePoint} → {@code onActiveStart}），
     * 而第三段那个「收招段打两下」是写在 {@code ShenheSkill.attack} 里的
     * （{@code stage == getMaxCombo()} 时补一次 {@code hurtServer}）。
     * 所以这里<b>每段只能列一条 {@code Hit}</b>，第三段给两条会变成四下。
     */
    private static final int[] COMBO_HIT_DELAYS = {4, 3, 10};

    /** 三段普攻各自的伤害范围（格）—— 和 {@code ShenheSkill.attack} 那把 2.5 格的横扫同量级。 */
    private static final int[] COMBO_HIT_SCOPES = {3, 3, 4};

    /**
     * 收尾动画名 —— 在 {@link #PUPPET_ANIMATION_FILE} 里，由
     * {@code tools/gen-shenhe-recovery.ps1} 生成。
     *
     * <h2>为什么必须有它</h2>
     * 第三段停在半路上：身体转到 -250°、手里的剑 scale 归零，动画就结束了。
     * 引擎对动作动画用 {@code thenPlayAndHold}（播完停在最后一帧等状态机接手），
     * 所以不接收尾的表现就是「转了一半定在那儿」——玩家说的「没有攻击后的收尾」。
     *
     * <p>这段收尾把这一转走完（-250° → -360°，同方向转完而不是倒着甩回来），
     * 顺便把第三段留给身体的那些静态姿势（{@code UpBody} / {@code Skirt} / {@code Root} / 弓步）
     * 放回站姿、把剑接回手里，最后一帧正好等于 {@code idle} 的姿势 —— 硬切回常态不会跳。
     *
     * <p>接法是 {@code ActionStep.withComboEnd}：最后一段打完，
     * {@code ResourceDrivenActionHandler} 会把它排成后续状态（{@code queueFollowUpState}），
     * 而不是直接回常态。
     */
    public static final String COMBO_END_ANIMATION = "sword_idle_attack_end";

    /** 收尾动画播多少刻 —— 和上面那段动画的 0.9 秒对齐。 */
    public static final int COMBO_END_TICKS = 18;

    /** 短按 E：突刺 + 一次冰伤（伤害本身在 {@code ShenheSkill.elementalSkill} 里算）。 */
    private static final int TAP_DURATION = 25;
    /** 长按 E：站桩，给全队冰翎与普攻/重击/下落加成。 */
    private static final int HOLD_DURATION = 30;
    /** 伤害点（刻）—— 和 {@code DashSystem} 的突刺刻数（10 刻）对齐，冲出去之后结算。 */
    private static final int SKILL_HIT_DELAY = 8;

    /**
     * 需要用<b>半透明管线</b>渲染的骨骼。
     *
     * <p>{@code ysmGlow_texiao} 是 YSM 转过来时留下的「胸口屏幕」整块：
     * 一根骨骼里两个方块 —— 外框花纹（29×16×0.55，贴图 UV (344,36)，像素全是
     * α=0 或 α=255，是纯镂空）和内屏（24×12×0.05，UV (287,40)，实测 RGBA 131,178,240,33，
     * <b>α≈0.13</b>）。GeckoLib 默认的 cutout 管线把 α 当阈值用，这一块就被画成了实心蓝；
     * 声明进来之后它会在主趟被藏掉、由 {@code entityTranslucent} 重画，恢复编辑器里那种半透明。
     *
     * <p>{@code ysmGlow_texiao2}（齿轮）<b>不要</b>加进来：它和花纹一样是纯镂空，
     * 走哪条管线看起来都一样，没必要动。
     */
    public static final String[] GLOW_SCREEN_BONES = {"ysmGlow_texiao"};

    /**
     * 木偶（桑多涅）那套<b>手写动画</b>的文件 —— 木偶角色本体还没做，这一套先<b>套在申鹤身上</b>验收。
     *
     * <p>和 {@code shenhe.animation.json} 分开放：那个文件是 YSM 搬过来的 101 段原动画（只读素材），
     * 这个文件里是还在改的手写件 —— 持续重击 {@value #CHARGED_ATTACK_ANIMATION}
     * （后面还有 FJO 子弹特效那一段）和普攻收尾 {@value #COMBO_END_ANIMATION}
     * （由 {@code tools/gen-shenhe-recovery.ps1} 生成）。不混进主文件就能单独替换、单独 diff。
     * 主文件里没有这两个名字，GeckoLib 会顺着回退表找到这里（{@code getAnimationResourceFallbacks}）。
     */
    private static final String PUPPET_ANIMATION_FILE =
            "character/shenhe/shenhe_puppet.animation.json";

    /**
     * 持续重击播的动画名 —— 见 {@link ShenheSkill#getChargedAttackAnimation}。
     *
     * <p>内容 = 「未过载」那一段：FJO 从不可见出现在木偶左侧、本体坐上飞行坐骑 MFly、
     * 屏幕在前方亮起、齿轮滑到屏幕右上角、一只手按屏幕、FJO 双指向前。
     */
    public static final String CHARGED_ATTACK_ANIMATION = "decoding_mode";

    /**
     * 模型作者 —— 现在这套模型（含胸口那块浮游屏）是 <b>White_clams白蛤蜊</b> 做的，
     * 配置页预览下方那行署名就是从这里取的。
     *
     * <p>写在渲染定义上而不是写死在页面里：模型换人 = 这儿改一行，
     * 页面（{@code ShenheConfigUI}）不认识任何具体名字。
     */
    private static final String MODEL_AUTHOR = "White_clams白蛤蜊";
    private static final String MODEL_AUTHOR_URL = "https://space.bilibili.com/168185637";

    /**
     * 战技的交战形态：<b>通用突进与吸附都关掉</b>，只保留「转向目标」。
     *
     * <p>她的位移是自己的 {@code DashSystem}（服务端推速度 + 沿途扫伤害），
     * 通用那套（{@code AttackApproach} 的突进 / 出手瞬间的吸附小步）会和她抢位移：
     * <ul>
     *   <li>{@code withDash(false)}：不要框架的近战突进；</li>
     *   <li>{@code withAdhesion(0, 0)}：不要「出手瞬间朝目标推一小步」
     *       —— 就是玩家说的「通用的小幅度移动」。</li>
     * </ul>
     *
     * <p>转向保留（默认）：{@code RushesForward} 是按<b>视线方向</b>算位移的，
     * 先转向目标才是「朝目标突刺」。
     */
    private static final Engagement NO_GENERIC_MOVE =
            Engagement.melee().withDash(false).withAdhesion(0, 0);

    /**
     * <b>屏幕 / 齿轮允许出现的动画状态</b> —— 三段普攻 + 收尾 + 重击。
     */
    public static final Set<String> SCREEN_ANIMATIONS = Set.of(
            COMBO_ANIMATIONS[0],
            COMBO_ANIMATIONS[1],
            COMBO_ANIMATIONS[2],
            COMBO_END_ANIMATION,
            CHARGED_ATTACK_ANIMATION);

    /**
     * <b>飞行三态</b>的动画状态名 —— 水平巡航 / 上升 / 下降。

     */
    public static final Set<String> FLIGHT_ANIMATIONS = Set.of("fly", "fly_up", "fly_down");

    /**
     * <b>手上的红茶允许出现的动画状态</b> —— 只有 YSM 那两条「喝茶」姿势。
     */
    public static final Set<String> TEA_ANIMATIONS = Set.of("gui", "extra48");

    /**
     * <b>闭眼姿势</b>的动画状态名 —— 这些状态里换成备用脸的闭眼档 {@code biyang1}。
     */
    public static final Set<String> CLOSED_EYE_ANIMATIONS = Set.of("extra_equip", "sleep");

    public static final CharacterRenderData RENDER_DATA = CharacterRenderData.character(
                    ID,
                    CharacterRenderData.defaultAnimMapping(),
                    1.0f
            ).withTranslucentBones(GLOW_SCREEN_BONES)
            .withAnimationFile(PUPPET_ANIMATION_FILE)
            .withModelAuthor(MODEL_AUTHOR, MODEL_AUTHOR_URL);

    /** 同上：靠 {@link #build()} 现算，必须留在常量声明之后。 */
    public static final CharacterActionData ACTION_DATA = build();

    private static CharacterActionData build() {
        CharacterActionData base = CharacterActionData.fallback(MAX_COMBO);

        // 普攻三段：动画名/时长/伤害点见上面那几个常量数组。
        //
        // protectDuration 一律取「最后一个伤害点之后一点」（伤害点 + 2 刻）：
        // 执行期必须盖住伤害点，否则玩家按着 WASD 就能在刀刃扫到之前把这一刀取消掉
        // （第三段的伤害点在 10 刻，比 duration/4 推出来的兜底硬直长得多，
        //  不显式给执行期就一定会被走位打断）。
        // 代价是这几刻人会定住 —— 第三段那个「转身横扫 + 剑砸地」本来就该是站桩的。
        Map<Integer, ActionStep> comboSteps = new LinkedHashMap<>();
        for (int stage = 1; stage <= MAX_COMBO; stage++) {
            int index = stage - 1;
            int hitDelay = COMBO_HIT_DELAYS[index];

            ActionStep step = new ActionStep(
                    COMBO_ANIMATIONS[index],
                    COMBO_DURATIONS[index],
                    hitDelay + 2,
                    2,
                    List.of(),                                                        // ← 位移归 DashSystem，普攻不带冲量
                    List.of(new Hit(hitDelay, 0.0, 1.5, COMBO_HIT_SCOPES[index])),
                    List.of(),
                    0, 0, 0, 8
            );

            if (stage == MAX_COMBO) {
                // 只有最后一段接收尾：前面两段打完直接进下一段（连招手感），
                // 第三段打完才需要「把这一转走完、站稳」。
                step.withComboEnd(COMBO_END_ANIMATION, COMBO_END_TICKS);
            }

            comboSteps.put(stage, step);
        }
        ComboData combo = new ComboData(MAX_COMBO, comboSteps);

        // 短按：没有准备阶段（按下去就是突刺，位移本身就是执行期），执行期盖住伤害点
        ActionStep tap = new ActionStep(
                "skill", TAP_DURATION, SKILL_HIT_DELAY + 6, 3,
                List.of(),                                   // ← 位移交给 DashSystem，这里必须为空
                List.of(new Hit(SKILL_HIT_DELAY, 0, 1.5, 3.0)),
                List.of(),
                0, 0, 0, 0
        ).withEngagement(NO_GENERIC_MOVE);

        // 长按：站桩不位移
        ActionStep hold = new ActionStep(
                "skill_hold", HOLD_DURATION, SKILL_HIT_DELAY + 6, 3,
                List.of(),
                List.of(new Hit(SKILL_HIT_DELAY, 0, 1.5, 3.0)),
                List.of(),
                0, 0, 0, 0
        ).withEngagement(NO_GENERIC_MOVE);

        return new CharacterActionData(
                combo,
                new SkillData(tap, hold),
                base.burst(),
                base.dodge());
    }
}
