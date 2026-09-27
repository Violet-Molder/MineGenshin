// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.allweapon.linweiyun;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.claymore.ClaymoreSkill;
import net.minecraft.world.entity.player.Player;

/**
 * 林薇云的<b>大剑形态技能</b>。
 *
 * <p>多出来的只是重击：{@link ClaymoreSkill} 的重击是「持续型」（按住进入状态、每 0.5 秒结算一圈），
 * 这一点和申鹤当前的做法一致（申鹤也是临时挂在大剑类上）。普攻 / 战技 / 爆发照抄、
 * 抽在 {@link LinweiyunSkillLogic} 里，这里只转发。
 */
public class LinweiyunClaymoreSkill extends ClaymoreSkill {

   @Override
   public int getMaxCombo() {
      return LinweiyunSkillLogic.maxCombo();
   }

   @Override
   public void attack(Player player, PGCharacter character, int comboStage) {
      LinweiyunSkillLogic.attack(player, character, comboStage);
   }

   @Override
   public void elementalSkill(Player player, PGCharacter character, int skillType) {
      LinweiyunSkillLogic.elementalSkill(player, character, skillType);
   }

   @Override
   public void elementalBurst(Player player, PGCharacter character) {
      LinweiyunSkillLogic.elementalBurst(player, character);
   }
}
