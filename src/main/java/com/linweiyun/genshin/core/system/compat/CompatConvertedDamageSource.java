package com.linweiyun.genshin.core.system.compat;

import com.linweiyun.elementlib.core.element.GenshinElement;
import net.minecraft.world.damagesource.DamageSource;

import javax.annotation.Nullable;

/**
 * 换算后的伤害源 —— 把其他 MOD 的伤害源「抄一份」再加上元素类型。
 *
 * <p>除了多带一个 {@link GenshinElement}，其余全部照抄原伤害源：
 * {@code typeHolder}（决定死亡信息 / 伤害类型标签）、直接实体、造成实体、命中位置。
 * 所以死亡信息、击退、音效、其他 MOD 对伤害源的判定都和原来一模一样 ——
 * 唯一的变化是伤害数值换成了角色口径，以及本 MOD 的盾能认出这是什么元素。
 *
 * <p>注意别把它当成 {@code ModDamageSource}：它走的是原版伤害管线（护甲、吸收、死亡流程都是原版的），
 * 只有元素类型这一个坑是本 MOD 自己认的。同时它也是「已经换算过」的标记，
 * 避免换算逻辑对自己递归。
 */
public class CompatConvertedDamageSource extends DamageSource {

    private final GenshinElement element;

    public CompatConvertedDamageSource(DamageSource original, @Nullable GenshinElement element) {
        super(original.typeHolder(), original.getDirectEntity(), original.getEntity(), original.sourcePositionRaw());
        this.element = element;
    }

    /** 这次伤害被赋予的元素类型，可能为 null（角色没元素时）。 */
    public @Nullable GenshinElement element() {
        return element;
    }

    /** 从伤害源里取换算时附上的元素；不是换算伤害源就是 null。 */
    public static @Nullable GenshinElement elementOf(@Nullable DamageSource source) {
        return source instanceof CompatConvertedDamageSource compat ? compat.element() : null;
    }
}
