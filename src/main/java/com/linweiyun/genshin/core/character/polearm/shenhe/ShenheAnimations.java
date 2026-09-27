package com.linweiyun.genshin.core.character.polearm.shenhe;

import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.system.combat.animation.config.LocomotionAnims;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * 申鹤的动画目录 —— 纯数据类：常态动画名、特殊动画名单、过渡刻数、状态→音效映射。
 *
 * <p>动画名与 {@code assets/minegenshin/character/shenhe/shenhe.animation.json}
 * （外加手写件 {@code shenhe_puppet.animation.json}）里的 key 一一对应；
 * 控制器逻辑在 {@code PlayerAnimationController}，动作编排在 {@code ResourceDrivenActionHandler}
 * （时序来自 {@link ShenheResources#ACTION_DATA}）。
 *
 * <h2>不登记这个类会怎样</h2>
 * 她原来走的是 {@code CharacterAnimationRegistry.registerPlaceholder} —— 那一条用的是
 * {@code DefaultCharacterAnimations}，它的 {@code specialAnims()} 是<b>空集合</b>。
 * 空集合意味着「分不出哪些是动作动画」，于是普攻第二刀会从第一刀的姿势
 * <b>用 5 刻插值平滑过去</b>（见 {@code PlayerAnimationController} 的智能顺切：
 * 只有名字在 specialAnims 里才硬切）。两条胳膊、两条腿在插值里会变成四不像，
 * 所以这张名单是「动作接得上」的一半，另一半才是动画名本身。
 *
 * <p><b>第一人称保持关闭</b>（不覆写 {@code firstPerson()}，用接口默认的 DISABLED）：
 * 动画文件里没有 {@code fp_*} 那几段，开了就只能走「复用第三人称动画 + 转机位」，
 * 那是一条会改第一人称机位的独立改动，留给她第一人称手感那一轮。
 *
 * <p>对应参考2 的 {@code ShenheAnimations}。
 */
public final class ShenheAnimations implements CharacterAnimations {

    public static final ShenheAnimations INSTANCE = new ShenheAnimations();

    /**
     * 常态动画名。
     *
     * <p>这套名字是照 {@code shenhe.animation.json} 里<b>真有的 key</b> 选的，
     * 不是照通用默认表抄的 —— YSM 那套命名和项目的默认表有两处不一样：
     * <ul>
     *   <li>蹲：文件里叫 {@code sneak}（蹲着不动，3 秒循环的深蹲姿势）/
     *       {@code sneaking}（蹲着走，{@code Root} 压低 7 格），没有 {@code crouch} / {@code crouch_walk}；</li>
     *   <li>水：文件里叫 {@code swim_stand}（水里站着，{@code Root} 只上浮 1.5）/
     *       {@code swim}（游泳，{@code Root} 转了 90° 变成横躺），没有 {@code water} 那三个名字。</li>
     * </ul>
     * 名字写错不会崩（{@code AnimationAvailability} 会拦住缺失的那个，
     * {@code PlayerAnimationController} 再退到 idle），但会「蹲下站着、下水站着」——
     * 所以这里全部点名到真实存在的名字上。
     *
     * <p>{@code waterWalk} 和 {@code waterWalkBack} 共用 {@code swim}：
     * 文件里没有分向前/向后两套划水，普通游泳动画本来就带两条腿的蹬水。
     *
     * <h2>跳、落、跑步收势这一组是这套素材里最容易接错的地方</h2>
     * 文件里跟「跳」相关的 key 有四条，形状完全不同，别按名字想当然：
     * <ul>
     *   <li>{@code jump} —— <b>起跳的上升循环</b>。原始素材是坏的（LeftArm 从 0° 线性涨到
     *       137.7°、末帧不回起手帧，却标着 loop），已按 {@code jump_down} 重写成
     *       0.5 秒的真循环：同族滞空姿势 + 多收一点腿。见 {@code tools/patch-shenhe-jump.py}。</li>
     *   <li>{@code jump_down} —— <b>下落的循环</b>。首末帧只差 1°，是这套素材里唯一
     *       本来就能循环的滞空姿势。</li>
     *   <li>{@code 跳跃缓冲} —— <b>落地的一次性收势</b>：首帧就是滞空姿势 → 深屈膝 → 站直。
     *       首帧与 {@code jump_down} 完全一致，所以进它那一下是 0 刻硬切也不跳形。</li>
     *   <li>{@code 落地小} / {@code 落地翻滚} —— 更短的蹭地 / 翻滚，只在特定玩法里才有意义，
     *       现在不接。</li>
     * </ul>
     * 跑步那一组：{@code run} 是循环（首末帧完全一致，质量没问题），
     * {@code 急停} 是<b>疾跑停下来的那一帧</b>播一次收势（39 根骨骼，身体整体后错 10 单位
     * 再滑回原位 + 屈膝撑地 + 躯干前压，末帧落在站姿）。
     */
    public static final LocomotionAnims LOCOMOTION = LocomotionAnims.of(
            "idle", "walk", "run", "walk_back",
            "sneak", "sneaking", "sleep", "climb",
            "swim_stand", "swim", "swim", "swim",
            "jump", "jump_down")
            // 刻数不是照搬素材时长：跳跃缓冲全长 1.0606s，但它 0.6s 就站直了，
            // 后面 0.4s 是「站着不动」的收势 —— 取 12 刻（0.6s）切回常态，
            // 落地才干脆；急停 1.25s 全长都要（刹车滑到 0.94s 才停住）。
            .withTransitions("跳跃缓冲", 12, "急停", 25);

    /**
     * 「动作动画」名单 —— <b>只用来决定过渡刻数</b>（名单内一律 0 刻硬切），不是存在性白名单。
     *
     * <p>动画是否存在由 {@code AnimationAvailability} 直接查 GeckoLib 的烘培缓存判断，
     * 所以这里漏写或多写都不会导致模型变成原始姿态，最多是过渡长短不理想。
     * 反过来，动作名<b>写在这里但动画文件里没有</b>（比如现在的 {@code skill} / {@code burst}）
     * 就是提醒：这几招还没动画，补动画时不用再回来改这张表。
     */
    public static final Set<String> SPECIAL_ANIMS = Set.of(
            // 普攻三段 + 第三段的收尾（收尾在 shenhe_puppet.animation.json 里，见 gen-shenhe-recovery.ps1）
            "sword_idle_attack_01", "sword_idle_attack_02", "sword_idle_attack_03",
            "sword_idle_attack_end",
            // 空中一刀：文件里有素材（sword_jump_attack），但动作表还没接它，先登记着
            "sword_jump_attack",
            // 战技短/长按、大招 —— 三个名字现在都还没有对应动画，留着等补
            "skill", "skill_hold",
            "burst",
            // 闪避四向（文件里也还没有，先登记着 —— resolveDodgeAnim 会按输入方向挑）
            "dodge_front", "dodge_back", "dodge_left", "dodge_right",
            // 持续重击（木偶那套，单独文件）
            "puppet_heavy");

    /** 常态动画互相切换的过渡刻数。 */
    public static final int EXIT_TRANSITION_TICKS = 5;

    /**
     * 状态 → 音效。切到这个状态时会自动播放，并跟着动画同步包一起广播给其他玩家。
     *
     * <p><b>这张表留空是故意的</b>：她目前<b>一个音效文件都没有</b>
     * （{@code character/shenhe/} 下只有贴图和动画，没有 {@code sounds/} 也没有 {@code sounds.json}）。
     * 写一个不存在的音效 id 只会在日志里刷找不到的声音。
     *
     * <p>要「一进状态就响」的音效（起手喊话之类）时往这里加：
     * <pre>
     * Map.entry("sword_idle_attack_01", "minegenshin:shenhe_attack_1")
     * </pre>
     * 声音文件放 {@code character/shenhe/sounds/attack_1.ogg}，
     * 事件定义写在 {@code character/shenhe/sounds.json}（见 {@code CharacterSounds}）。
     */
    public static final Map<String, String> STATE_SOUNDS = Map.ofEntries();

    private ShenheAnimations() {
    }

    @Override
    public LocomotionAnims locomotion() {
        return LOCOMOTION;
    }

    @Override
    public Set<String> specialAnims() {
        return SPECIAL_ANIMS;
    }

    @Override
    public int exitTransitionTicks() {
        return EXIT_TRANSITION_TICKS;
    }

    @Nullable
    @Override
    public String soundForState(String stateName) {
        return STATE_SOUNDS.get(stateName);
    }
}
