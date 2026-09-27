package com.linweiyun.genshin.core.element;

import net.minecraft.resources.Identifier;
import com.linweiyun.genshin.core.system.control.ControlRequest;
import com.linweiyun.genshin.core.system.control.ControlService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * <b>寒元素</b> —— 冰/冻的<b>附加效果载体</b>。
 *
 * <p>「冰元素的减速」和「冻元素的冻结」本质上都是寒在起作用，寒往往伴随冰/冻存在。
 * 所以这两段效果不再写在 {@code CryoElement} / {@code FrozenElement} 上，而是集中在本类：
 * 冰/冻只负责「附着」这件事，寒负责「附着带来的影响」。这样才谈得上
 * 「用子元素区分附加效果」—— 而不是每个元素本体各自挂一段硬编码。
 *
 * <h2>为什么寒在注册表里不是「mainElement 归并型类元素」</h2>
 * 这个代码库里 {@code mainElement} 的<b>唯一</b>含义是「参与反应配对时并入主元素」。
 * 若把寒归并到冰，它会同时被
 * {@code ElementalReactionManager.collectDefenders}、{@link #isEffectCarrier() 反应消耗}、
 * 融化自带的求和循环、扩散的传染四处当成冰消耗掉 —— 那就得在四处写例外。
 * 寒注册成独立元素后，注册表里没有任何反应以它为配对方，<b>一处例外都不用写</b>：
 * 它天然不参与任何反应、不会多算一次冰反应、也不会被扩散传染。
 * 父子关系由伴随机制（{@code ColdAura}）表达，而不是靠 mainElement。
 *
 * <h2>豁免：不收寒 = 不受影响</h2>
 * 冰史莱姆给自己挂冰、却完全不受冰影响，是因为它<b>不接受寒</b>（见
 * {@code ElementalCreature#acceptsElementAttachment}）—— 寒没挂上，减速与冻结自然都不存在。
 * 这也是「效果挂在寒身上」带来的好处：豁免只需要表达一次，不用在每个效果里各判一遍。
 *
 * @see com.linweiyun.genshin.core.system.about.ColdAura
 */
public class ColdElement extends GenshinElement {

    /**
     * 减速修饰符的 id。
     *
     * <p><b>沿用旧名字 {@code cryo_slow} 是刻意的</b>：属性修饰符会随实体一起存进存档，
     * 改名会让旧存档里的减速变成「没人认得、也撤不掉」的残留。
     */
    private static final Identifier SLOW_MODIFIER_ID =
            Identifier.fromNamespaceAndPath("minegenshin", "cryo_slow");

    /** 减速幅度（-10% 移速）。 */
    private static final float SLOW_AMOUNT = -0.10f;

    protected ColdElement(String translationKey) {
        // 效果载体：不参与反应配对，也不在 HUD 上占一个图标
        super(false, false, true, translationKey);
    }

    /**
     * 按「容器里的三个事实」应用或撤销效果。
     *
     * <p>由 {@code ColdAura} 每 tick 调一次，事实就是容器本身（不另存状态）：
     * <pre>
     *   有寒 且 有冰族（冰/冻）→ 减速
     *   有寒 且 有冻          → 禁 AI
     *   否则                  → 撤销
     * </pre>
     *
     * @param cold       此刻宿主身上有没有寒（被宿主拒收时永远为 false → 天然豁免）
     * @param cryoFamily 有没有冰或冻（有冻也算有冰族，冻结期间不该因为冰被吃光而丢减速判定）
     * @param frozen     有没有冻
     */
    public static void applyEffects(LivingEntity entity, boolean cold,
                                    boolean cryoFamily, boolean frozen) {
        if (entity == null || !isNonPlayerLiving(entity)) {
            return;
        }
        applyChill(entity, cold && cryoFamily);
        applyFreeze(entity, cold && frozen);
    }

    /** 寒冷：减速。判到「状态没变」就不动属性，避免每 tick 反复加减修饰符。 */
    private static void applyChill(LivingEntity entity, boolean chilled) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean has = speed.hasModifier(SLOW_MODIFIER_ID);
        if (chilled && !has) {
            speed.addPermanentModifier(new AttributeModifier(
                    SLOW_MODIFIER_ID, SLOW_AMOUNT,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!chilled && has) {
            speed.removeModifier(SLOW_MODIFIER_ID);
        }
    }

    /**
     * 冻结：禁 AI —— <b>走控制入口</b>。
     *
     * <p>用户口径（2026-09-25）：「所有控制类效果走一个入口，包括冻结的 NoAI。」
     * 所以这里不再自己 {@code setNoAi}，而是发一次 {@link ControlRequest#freeze}：
     * <ul>
     *   <li>判据由实体给（{@code Controllable#blocksControl}）—— 首领一类实体可以在这里免疫冻结；
     *       解冻请求永远放行，不然目标会被永久冻住；</li>
     *   <li>效果也在实体上（{@code Controllable#freezeAction}），要改「冻住长什么样」只改那一处。</li>
     * </ul>
     *
     * <p><b>覆盖范围是「所有非玩家 Mob」</b>，不区分是不是本模组的生物 —— 附着系统本身对任何
     * {@code LivingEntity} 都生效，原版僵尸被冻住也该被冻住。以前这条只认 {@code TeyvatLiving}，
     * 于是「原版怪被冻住」永远不成立；现在原版生物由 mixin 注入，
     * 拿到的是同一套默认实现，这条依然成立。
     */
    private static void applyFreeze(LivingEntity entity, boolean frozen) {
        ControlService.apply(entity, ControlRequest.freeze(frozen));
    }
}
