package com.linweiyun.genshin.core.character.talent;

import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.world.entity.player.Player;

/**
 * 角色<b>天赋</b>基类 —— 突破天赋（突破 1 / 突破 4）与其它被动效果。
 *
 * <h2>三个协作者怎么分（本仓库的统一布局）</h2>
 * <pre>
 *   core/character/talent/SkillBase.java          XxxSkill          招式：动作数据 + 伤害结算
 *   core/character/talent/TalentBase.java         XxxTalent         天赋：突破天赋 / 被动
 *   core/character/talent/ConstellationBase.java  XxxConstellation  命座：C1..C6
 *   core/character/{weapon}/{name}/Xxx.java       主类              只做中转/调度
 * </pre>
 * 三个基类<b>同包</b>（都在 {@code core.character.talent} 下），角色的三个协作者文件则放在
 * 角色自己的包（{@code core.character.{weapon}.{name}}）里，和主类并排。
 *
 * <h2>和技能的分工</h2>
 * 招式本身（普攻 / 重击 / 战技 / 大招的动作数据与伤害）在 {@link SkillBase} 的子类里；
 * 这里只放「<b>不由某一招直接打出来</b>」的被动：
 * <ul>
 *   <li>突破门槛判定（{@code getAscensionPhase() >= n}）；</li>
 *   <li>被动给的队伍效果（申鹤的冰凌、沃雅妮莎的领唱/重唱、薇斯娜的整肃…）；</li>
 *   <li>需要每刻重算的属性（{@link #tick}）。</li>
 * </ul>
 *
 * <h2>为什么技能里会有「调用天赋」的一行</h2>
 * 有些被动是<b>施放某一招时发放</b>的（例如申鹤点按 E 发冰凌）。这时技能里只留一行
 * {@code character.getTalent().xxx(...)}，实现在这里 —— 技能不重复写被动的内容。
 *
 * <h2>状态放在哪</h2>
 * 持久化字段（{@code @Persisted} / {@code @DescSynced}）一律留在角色主类上：
 * 键属于存档格式，搬家会让老存档读不出来。天赋只通过主类的访问器/字段操作它们。
 *
 * <h2>实例在哪创建</h2>
 * 由角色<b>无参构造器</b>创建（客户端反序列化走
 * {@code ModSyncAccessors.deserializeFromTag} 里的
 * {@code clazz.getDeclaredConstructor().newInstance()}，<b>会</b>跑到子类的无参构造器），
 * 所以双端都有实例 —— 但判断仍然只依赖 {@code PGCharacterData} 这类同步数据，
 * 不要把「双端一致」寄希望于协作者内部的运行时状态。
 */
public class TalentBase {

    /**
     * <b>兜底天赋</b>：没有自己天赋的角色（含「缺数据就回退」的那一支）拿到的那一个。
     *
     * <p>它是空实现 —— 突破门槛、队伍效果、每刻重算一个都没有，所以 {@code getTalent().xxx()}
     * 这类调用永远安全。角色一旦有自己的天赋类，就在自己的无参构造器里换掉它。
     */
    public static final TalentBase DEFAULT = new TalentBase();

    /**
     * 每刻调用一次（<b>仅服务端</b>，由角色主类的 {@code tick} 转发）。
     *
     * <p>需要持续生效的被动（整肃的独立倒计时、按队伍元素构成重算属性…）覆盖它。
     * 默认空实现 —— 不是所有天赋都需要每刻跑。
     *
     * <p>为什么由主类转发而不是 {@code PGCharacter.tick} 统一调：
     * 每个角色的调用时机不一样（例：薇斯娜要先跑巡风列装倒计时、退场清层，
     * 再轮到天赋的每刻重算），写在主类的 {@code tick} 里顺序才和重构前一致。
     */
    public void tick(Player player, PGCharacter character) {}
}
