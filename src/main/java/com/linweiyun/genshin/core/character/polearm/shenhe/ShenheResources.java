// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.polearm.shenhe;

import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.ComboData;
import com.linweiyun.genshin.core.system.combat.action.data.Engagement;
import com.linweiyun.genshin.core.system.combat.action.data.Hit;
import com.linweiyun.genshin.core.system.combat.action.data.SkillData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ShenheResources {
   public static final String ID = "shenhe";
   public static final int MAX_COMBO = 3;
   private static final String[] COMBO_ANIMATIONS = new String[]{"sword_idle_attack_01", "sword_idle_attack_02", "sword_idle_attack_03"};
   private static final int[] COMBO_DURATIONS = new int[]{20, 20, 18};
   private static final int[] COMBO_HIT_DELAYS = new int[]{4, 3, 10};
   private static final int[] COMBO_HIT_SCOPES = new int[]{3, 3, 4};
   public static final String COMBO_END_ANIMATION = "sword_idle_attack_end";
   public static final int COMBO_END_TICKS = 18;
   private static final int TAP_DURATION = 25;
   private static final int HOLD_DURATION = 30;
   private static final int SKILL_HIT_DELAY = 8;
   public static final String[] GLOW_SCREEN_BONES = new String[]{"ysmGlow_texiao"};
   private static final String PUPPET_ANIMATION_FILE = "character/shenhe/shenhe_puppet.animation.json";
   public static final String CHARGED_ATTACK_ANIMATION = "decoding_mode";
   private static final String MODEL_AUTHOR = "White_clams白蛤蜊";
   private static final String MODEL_AUTHOR_URL = "https://space.bilibili.com/168185637";
   private static final Engagement NO_GENERIC_MOVE = Engagement.melee().withDash(false).withAdhesion(0.0, 0.0);
   public static final Set<String> SCREEN_ANIMATIONS = Set.of(
      COMBO_ANIMATIONS[0], COMBO_ANIMATIONS[1], COMBO_ANIMATIONS[2], "sword_idle_attack_end", "decoding_mode"
   );
   public static final Set<String> FLIGHT_ANIMATIONS = Set.of("fly", "fly_up", "fly_down");
   public static final Set<String> TEA_ANIMATIONS = Set.of("gui", "extra48");
   public static final Set<String> CLOSED_EYE_ANIMATIONS = Set.of("extra_equip", "sleep");
   public static final CharacterRenderData RENDER_DATA = CharacterRenderData.character("shenhe", CharacterRenderData.defaultAnimMapping(), 1.0F)
      .withTranslucentBones(GLOW_SCREEN_BONES)
      .withAnimationFile("character/shenhe/shenhe_puppet.animation.json")
      .withModelAuthor("White_clams白蛤蜊", "https://space.bilibili.com/168185637");
   public static final CharacterActionData ACTION_DATA = build();

   private ShenheResources() {
   }

   private static CharacterActionData build() {
      CharacterActionData base = CharacterActionData.fallback(3);
      Map<Integer, ActionStep> comboSteps = new LinkedHashMap<>();

      for (int stage = 1; stage <= 3; stage++) {
         int index = stage - 1;
         int hitDelay = COMBO_HIT_DELAYS[index];
         ActionStep step = new ActionStep(
            COMBO_ANIMATIONS[index],
            COMBO_DURATIONS[index],
            hitDelay + 2,
            2,
            List.of(),
            List.of(new Hit(hitDelay, 0.0, 1.5, COMBO_HIT_SCOPES[index])),
            List.of(),
            0.0F,
            0.0F,
            0,
            8
         );
         if (stage == 3) {
            step.withComboEnd("sword_idle_attack_end", 18);
         }

         comboSteps.put(stage, step);
      }

      ComboData combo = new ComboData(3, comboSteps);
      ActionStep tap = new ActionStep("skill", 25, 14, 3, List.of(), List.of(new Hit(8, 0.0, 1.5, 3.0)), List.of(), 0.0F, 0.0F, 0, 0)
         .withEngagement(NO_GENERIC_MOVE);
      ActionStep hold = new ActionStep("skill_hold", 30, 14, 3, List.of(), List.of(new Hit(8, 0.0, 1.5, 3.0)), List.of(), 0.0F, 0.0F, 0, 0)
         .withEngagement(NO_GENERIC_MOVE);
      return new CharacterActionData(combo, new SkillData(tap, hold), base.burst(), base.dodge());
   }
}
