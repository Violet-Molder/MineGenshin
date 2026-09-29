// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.allweapon.linweiyun;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.allweapon.linweiyun.attack.LinweiyunElementalBurst;
import com.linweiyun.genshin.core.character.allweapon.linweiyun.attack.LinweiyunElementalSkill;
import com.linweiyun.genshin.core.character.allweapon.linweiyun.attack.LinweiyunNormalAttack;
import com.linweiyun.genshin.core.character.allweapon.linweiyun.attack.LinweiyunPlungeAttack;
import com.linweiyun.genshin.core.character.util.appearance.WeaponAppearance;
import com.linweiyun.genshin.core.character.claymore.ClaymoreSkill;
import net.minecraft.world.entity.player.Player;

/**
 * 林薇云的<b>大剑形态技能</b>。
 *
 * <p>大剑的重击由 {@link ClaymoreSkill} 的持续型重击覆盖，
 * 普攻 / 战技 / 爆发委托给独立的攻击类。
 */
public class LinweiyunClaymoreSkill extends ClaymoreSkill {

   /** 起飞前摇：大剑形态那一档。 */
   @Override
   public int flyStartTicks() {
      return LinweiyunSkillLogic.flyStartTicks(WeaponAppearance.CLAYMORE);
   }

   /** 起飞前摇动画：{@code fly_start_claymore}。 */
   @Override
   public String flyStartAnimation() {
      return "fly_start_claymore";
   }

   @Override
   public int getMaxCombo() {
      return LinweiyunSkillLogic.maxCombo();
   }

   @Override
   public void attack(Player player, PGCharacter character, int comboStage) {
      LinweiyunNormalAttack.execute(player, character, comboStage);
   }

   @Override
   public void plungingAttack(Player player, PGCharacter character) {
      LinweiyunPlungeAttack.execute(player, character);
   }

   @Override
   public void elementalSkill(Player player, PGCharacter character, int skillType) {
      LinweiyunElementalSkill.execute(player, character, skillType);
   }

   @Override
   public void elementalBurst(Player player, PGCharacter character) {
      LinweiyunElementalBurst.execute(player, character);
   }
}