package com.linweiyun.genshin.content.entities.teyvat;

import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;

/**
 * <b>元素生物</b> —— 史莱姆、无相之冰这类「本身就是某个元素」的敌人。
 *
 * <h2>统一规则</h2>
 * <ul>
 *   <li>生物记录自己的元素（{@link #getCreatureElement()}）；</li>
 *   <li><b>永久免疫同元素伤害</b>：大型冰史莱姆吃冰元素伤害永远是 0，
 *       不管这个伤害来自角色技能、反应还是环境；
 *       免疫走 {@code ElementalAttachable.isImmuneToElementDamage}（由 {@code TeyvatMonster} 统一实现），
 *       <b>只把伤害归零，不拦附着</b>；</li>
 *   <li><b>照样接受附着</b>：冰史莱姆要给自己挂冰、也要能被火/水挂上正常反应，
 *       所以「收不收某个元素的附着」是另一条轴（见 {@link #acceptsElementAttachment}），
 *       默认全收，<b>唯一例外是寒</b>（附加效果载体）—— 免疫冰的元素生物不收寒，
 *       而「不受冰影响」正是由「没有寒」表达的。</li>
 * </ul>
 *
 * <p>免疫判定走 {@link #isImmuneTo(GenshinElement)}，用<b>主元素</b>比较 ——
 * 所以冰史莱姆同时也免疫「冻」元素（{@code FROZEN} 的主元素是 {@code CYRO}）。
 *
 * <p>这个接口只负责「记录 + 免疫」。它不负责套盾、不负责移动，
 * 那些是 {@link TeyvatLiving} 和实体自己的事。
 */
public interface ElementalCreature extends TeyvatLiving {

    /** 这个生物的元素。 */
    GenshinElement getCreatureElement();

    /**
     * 这个生物收不收某个元素的<b>附着</b>（第一段筛查）。
     *
     * <p>默认全收。元素生物只是免疫同元素伤害，<b>不该顺带拒绝附着</b> —— 冰史莱姆的
     * 自挂冰正是靠这条通路进来的，火/水也该能挂上去正常触发反应。
     *
     * <p>要表达「不被某种元素沾上」的怪物在这里重写（例：大型冰史莱姆拒绝水）。
     * 注意被拒绝的附着不会带来任何反应 —— 拒收 = 元素没上去 = 不反应。
     */
    default boolean acceptsElementAttachment(GenshinElement element) {
        // 寒（附加效果载体）单独一条：免疫冰的元素生物不收寒 —— 这就是
        // 「冰史莱姆给自己挂冰、却完全不受冰影响」的全部实现（挂不上寒 → 没有减速、没有禁 AI、没有位移锁）。
        // 不能靠 isImmuneTo(COLD) 来判：它比的是主元素，而寒是独立元素、主元素就是它自己。
        if (element == ModElements.COLD.get()) {
            return !isImmuneTo(ModElements.CYRO.get());
        }
        return true;
    }

    /**
     * 是不是免疫这个元素。
     *
     * <p>默认实现：同主元素即免疫。子类可以放宽（比如某个精英怪只免疫 50%，
     * 那就别用这个接口，自己在 {@code hurtServer} 里削伤害）。
     */
    default boolean isImmuneTo(@Nullable GenshinElement element) {
        if (element == null) {
            return false;
        }
        GenshinElement own = getCreatureElement();
        return own != null && element.getMainElement() == own.getMainElement();
    }

    /** 从伤害源上取元素再判断；取不到元素的（原版物理伤害）一律不免疫。 */
    default boolean isImmuneToSource(DamageSource source) {
        if (source instanceof ModDamageSource modSource && modSource.getSpec() != null) {
            return isImmuneTo(modSource.getSpec().getElement());
        }
        return false;
    }
}
