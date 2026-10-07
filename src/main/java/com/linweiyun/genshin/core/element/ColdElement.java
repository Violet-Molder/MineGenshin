package com.linweiyun.genshin.core.element;

import com.linweiyun.elementlib.core.element.GenshinElement;
import net.minecraft.resources.ResourceLocation;
import com.linweiyun.genshin.core.system.control.ControlRequest;
import com.linweiyun.genshin.core.system.control.ControlService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * <b>寒元素</b> —— 冰/冻的<b>附加效果载体</b>。
 * {@code ElementalCreature#acceptsElementAttachment}）—— 寒没挂上，减速与冻结自然都不存在。
 * 这也是「效果挂在寒身上」带来的好处：豁免只需要表达一次，不用在每个效果里各判一遍。
 *
 * @see com.linweiyun.elementlib.core.system.about.ColdAura
 */
public class ColdElement extends GenshinElement {

    /**
     * 减速修饰符的 id。
     *
     * <p><b>沿用旧名字 {@code cryo_slow} 是刻意的</b>：属性修饰符会随实体一起存进存档，
     * 改名会让旧存档里的减速变成「没人认得、也撤不掉」的残留。
     */
    private static final ResourceLocation SLOW_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath("minegenshin", "cryo_slow");

    /** 减速幅度（-10% 移速）。 */
    private static final float SLOW_AMOUNT = -0.10f;

    protected ColdElement(String translationKey) {
        // 效果载体：不参与反应配对，也不在 HUD 上占一个图标
        super(false, false, true, translationKey);
    }

    /**
     * 按「容器里的三个事实」应用或撤销效果。
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
     */
    private static void applyFreeze(LivingEntity entity, boolean frozen) {
        ControlService.apply(entity, ControlRequest.freeze(frozen));
    }
}
