package com.linweiyun.genshin.content.entities.teyvat.monster;

import com.linweiyun.genshin.content.entities.teyvat.ElementalCreature;
import com.linweiyun.genshin.content.entities.teyvat.TeyvatHostile;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.elementlib.core.system.about.AttachmentProfile;
import com.linweiyun.elementlib.core.system.about.AttachmentSource;
import com.linweiyun.elementlib.core.system.about.ElementalAttachable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public abstract class TeyvatMonster extends Monster implements TeyvatHostile, ElementalAttachable {
    protected TeyvatMonster(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    /**
     * 元素生物的统一规则入口（放在基类里，任何实现 {@link ElementalCreature} 的怪物都自动获得）。
     *
     * <p>注意这里是<b>两条互不相干的轴</b>：
     * <ul>
     *   <li>{@link #onAttachElement} = 收不收这个元素的附着（宿主第一段筛查）；</li>
     *   <li>{@link #isImmuneToElementDamage} = 吃不吃这个元素的伤害（伤害侧）。</li>
     * </ul>
     *
     * <p>以前这两件事都挤在 {@code hurtServer} 的提前 {@code return false} 里 —— 而附着是在伤害
     * 管线内部做的，于是「免疫伤害」被扩大成「连附着都不发生」。现在免疫不再拦附着：
     * 打上去照样挂元素、照样反应，只是伤害按 0 结算。
     */
    @Override
    public boolean onAttachElement(GenshinElement element, AttachmentSource source,
                                   AttachmentProfile profile) {
        return !(this instanceof ElementalCreature creature)
                || creature.acceptsElementAttachment(element);
    }

    /** 元素生物对同（主）元素伤害免疫；非元素生物不免疫。 */
    @Override
    public boolean isImmuneToElementDamage(GenshinElement element) {
        return this instanceof ElementalCreature creature && creature.isImmuneTo(element);
    }
}
