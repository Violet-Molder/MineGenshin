// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.allweapon.linweiyun;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.appearance.WeaponAppearance;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

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

   /** 起飞前摇：六种形态各一档（数值表在 {@link LinweiyunSkillLogic}）。 */
   @Override
   public int flyStartTicks() {
      return LinweiyunSkillLogic.flyStartTicks(this.form);
   }

   /**
    * 起飞前摇的动画：<b>按形态各一条</b>（{@code fly_start_sword} / {@code fly_start_polearm} …）。
    *
    * <p>没做素材的形态会查不到动画，{@code AnimationAvailability} 会拦住切换 ——
    * 状态与前摇计时照常走，只是视觉上停在上一帧。
    */
   @Override
   public String flyStartAnimation() {
      return "fly_start_" + this.form.name().toLowerCase(Locale.ROOT);
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
