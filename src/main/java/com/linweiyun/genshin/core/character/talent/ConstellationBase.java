package com.linweiyun.genshin.core.character.talent;

import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.world.entity.player.Player;

/**
 * 角色<b>命之座</b>基类 —— C1..C6 的效果。
 *
 * <h2>为什么单独一个类</h2>
 * 命座是「叠在招式/天赋之上的一层」：招式里那些 {@code if (character.hasConstellation(n))}
 * 分支现在都改成<b>一行调用</b>，实现在这里。这样「这个角色满命到底改了什么」一眼能看完，
 * 而招式里只剩下「这一招本身怎么打」。
 *
 * <h2>写法约定</h2>
 * <ul>
 *   <li>门面一律走 {@link PGCharacter#hasConstellation(int)}（不要直接比数字）；</li>
 *   <li>命座要求「先解锁某个突破天赋」时，<b>在这里再判一次</b>（例：薇斯娜 C2 要
 *       {@code hasSpringRiteTalent()}）；</li>
 *   <li>分支如果紧密耦合在伤害算式里，就写成<b>一个小而明确的方法</b>返回那个值
 *       （例：薇斯娜 C4 返回 1f / 3f 的倍率），由招式在一处调用；</li>
 *   <li>需要每刻生效的（满层整肃加攻击力…）覆盖 {@link #tick}。</li>
 * </ul>
 *
 * <h2>状态放在哪</h2>
 * 和天赋一样：持久化字段留在角色主类上（键属于存档格式），命座只通过主类的访问器操作。
 *
 * <h2>实例在哪创建</h2>
 * 由角色<b>无参构造器</b>创建（客户端反序列化走 {@code getDeclaredConstructor().newInstance()}，
 * 会跑到子类的无参构造器），和 {@link TalentBase} 一样。
 */
public class ConstellationBase {

    /**
     * 每刻调用一次（<b>仅服务端</b>，由角色主类的 {@code tick} 转发）。
     *
     * <p>需要「按当前状态重算」的命座效果（例：薇斯娜 C2 满层整肃给攻击力）覆盖它。
     * 默认空实现。
     */
    public void tick(Player player, PGCharacter character) {}
}
