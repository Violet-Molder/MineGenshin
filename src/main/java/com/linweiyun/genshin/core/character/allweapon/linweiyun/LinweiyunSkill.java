// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.allweapon.linweiyun;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.appearance.WeaponAppearance;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import net.minecraft.world.entity.player.Player;

/**
 * 林薇云的<b>普通形态技能</b>（拳 / 剑 / 长柄 / 法器 / 弓 —— 大剑形态见
 * {@link LinweiyunClaymoreSkill}）。
 *
 * <p>普攻 / 战技 / 爆发的实现照抄申鹤那套，抽在 {@link LinweiyunSkillLogic} 里，这里只做转发。
 * 段数同样用 3 段（申鹤那套的段数）。
 */
public class LinweiyunSkill extends SkillBase {
   private final WeaponAppearance form;

   public LinweiyunSkill(WeaponAppearance form) {
      this.form = form;
   }

   /** 当前形态（拳 / 剑 / 长柄 / 法器 / 弓）。 */
   public WeaponAppearance form() {
      return this.form;
   }

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
