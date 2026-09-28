package com.linweiyun.genshin.core.character.allweapon.linweiyun;

import com.linweiyun.genshin.core.character.CharacterHelper;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.allweapon.AllWeaponCharacter;
import com.linweiyun.genshin.core.character.appearance.WeaponAppearance;
import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.system.combat.animation.config.LocomotionAnims;
import com.geckolib.animation.RawAnimation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 林薇云的动画接线。
 *
 * <p>常态部分直接复用 {@link LocomotionAnims#DEFAULT}（名字全通用：idle / walk / run / jump /
 * landing …），另外把<b>飞行三态</b>接上：她的动画文件里有 {@code fly}，
 * 所以三条都用它 —— 不接的话飞着上升会掉进「空中」分支去播 jump
 * （就是「飞行时不停触发跳跃动画」那个毛病）。
 *
 * <p>她的动画文件里另有按形态区分的 {@code fly_sword / fly_claymore / fly_catalyst / fly_bow}
 * 与长柄那一组 {@code fly_start_polearm / fly_idle_polearm / fly_front_polearm / fly_up_polearm /
 * fly_down_polearm / fly_quckly_polearm}（拳头形态用通用那条 {@code fly}）—— 按形态换飞行片段走
 * {@link #locomotionFor(Player)}：{@code LocomotionAnims} 存的是固定名字、拿不到玩家，
 * 所以由接口新开的那条「按玩家挑」的路子来做。
 *
 * <p>动作（普攻 / 战技 / 爆发）走通用 {@code ResourceDrivenActionHandler}，
 * 动作动画登记在 {@link #SPECIAL_ANIMS} —— 名单里的动画硬切（0 刻过渡）。
 */
public final class LinweiyunAnimations implements CharacterAnimations {

    public static final LinweiyunAnimations INSTANCE = new LinweiyunAnimations();

   /** 常态 + 飞行三态（三条都指向她唯一的 {@code fly}）。 */
   public static final LocomotionAnims LOCOMOTION = LocomotionAnims.DEFAULT.withFlight("fly", "fly", "fly");

   /**
    * 六种武器形态各自的飞行片段。
    *
    * <p>拳：{@code fly}（她没有单独的拳头飞行素材）；其余五种各一条 ——
    * 名字就是她动画文件里那五条。上升 / 下降暂时共用同一条（素材里还没有按形态再分上下）。
    */
   private static final Map<WeaponAppearance, LocomotionAnims> FLIGHT_BY_FORM = buildFlightByForm();

   /** 长柄水平飞行那三条：站着悬停 / 往前飞 / 疾跑冲刺（素材用用户自己起的名字）。 */
   private static final String POLEARM_HOVER = "fly_idle_polearm";
   private static final String POLEARM_FRONT = "fly_front_polearm";
   /** 名字是用户原话里的拼写（quickly 少一个 i），别顺手改。 */
   private static final String POLEARM_QUICK = "fly_quckly_polearm";

   public static final Map<String, String> STATE_SOUNDS = Map.ofEntries();

   public static final Set<String> SPECIAL_ANIMS = Set.of(
           "shenhe_attack_1",
           "shenhe_attack_2",
           "fly_start_polearm"
   );

   private LinweiyunAnimations() {
   }

   private static Map<WeaponAppearance, LocomotionAnims> buildFlightByForm() {
      Map<WeaponAppearance, LocomotionAnims> map = new EnumMap<>(WeaponAppearance.class);
      map.put(WeaponAppearance.FIST, LocomotionAnims.DEFAULT.withFlight("fly", "fly", "fly"));
      map.put(WeaponAppearance.SWORD, LocomotionAnims.DEFAULT.withFlight("fly_sword", "fly_sword", "fly_sword"));
      map.put(WeaponAppearance.POLEARM, LocomotionAnims.DEFAULT.withFlight(
              POLEARM_HOVER, "fly_up_polearm", "fly_down_polearm"));
      map.put(WeaponAppearance.CLAYMORE, LocomotionAnims.DEFAULT.withFlight("fly_claymore", "fly_claymore", "fly_claymore"));
      map.put(WeaponAppearance.CATALYST, LocomotionAnims.DEFAULT.withFlight("fly_catalyst", "fly_catalyst", "fly_catalyst"));
      map.put(WeaponAppearance.BOW, LocomotionAnims.DEFAULT.withFlight("fly_bow", "fly_bow", "fly_bow"));
      return map;
   }

    @Override
   public LocomotionAnims locomotion() {
      return LOCOMOTION;
   }

   /** 按她现在选的武器形态挑飞行动画（没选 / 不是全武器类角色 → 通用那条）。 */
   @Override
   public LocomotionAnims locomotionFor(Player player) {
      WeaponAppearance form = currentForm(player);
      return form == null ? LOCOMOTION : FLIGHT_BY_FORM.getOrDefault(form, LOCOMOTION);
   }

   /**
    * 长柄的「水平飞行」再分三档：<b>疾跑 → 冲刺</b>、<b>有水平移动 → 往前飞</b>、
    * <b>都不动 → 悬停</b>（用户给的五条素材里的三条水平档）。
    *
    * <p>上升 / 下降仍然是 {@link LocomotionAnims#flyUp()} / {@link #locomotionFor} 里那两条，
    * 由控制器按竖直速度先判掉，走不到这里。
    */
   @Override
   public RawAnimation flyVariant(Player player, LocomotionAnims loco, boolean moving) {
      if (!moving || currentForm(player) != WeaponAppearance.POLEARM) {
         return loco.fly();
      }
      return RawAnimation.begin().thenLoop(player.isSprinting() ? POLEARM_QUICK : POLEARM_FRONT);
   }

   /** 她现在的武器形态；不是全武器类角色就返回 {@code null}。 */
   private static WeaponAppearance currentForm(Player player) {
      PGCharacter character = CharacterHelper.getCurrentCharacter(player);
      return character instanceof AllWeaponCharacter allWeapon ? allWeapon.currentWeaponForm() : null;
   }

   @Override
   public Set<String> specialAnims() {

      return SPECIAL_ANIMS;
   }

    @Override
    public int exitTransitionTicks() {
        return 5;
    }

    @Nullable
    @Override
    public String soundForState(String stateName) {
        return STATE_SOUNDS.get(stateName);
    }
}
