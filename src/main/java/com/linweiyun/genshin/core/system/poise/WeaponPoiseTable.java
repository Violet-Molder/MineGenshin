// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.system.poise;

import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.items.weapon.bow.Bow;
import com.linweiyun.genshin.content.items.weapon.catalyst.Catalyst;
import com.linweiyun.genshin.content.items.weapon.claymore.Claymore;
import com.linweiyun.genshin.content.items.weapon.fist.Fist;
import com.linweiyun.genshin.content.items.weapon.polearm.Polearm;
import com.linweiyun.genshin.content.items.weapon.sword.Sword;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.poise.impact.ImpactLevel;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;

public final class WeaponPoiseTable {
   private static final int MIN_WEAPON_IMPACT_LEVEL = 2;

   private WeaponPoiseTable() {
   }

   public static WeaponPoiseTable.WeaponClass weaponOf(@Nullable PGCharacter character) {
      if (character == null) {
         return WeaponPoiseTable.WeaponClass.UNKNOWN;
      }

      WeaponPoiseTable.WeaponClass equipped = weaponOfStack(character);
      return equipped != WeaponPoiseTable.WeaponClass.UNKNOWN ? equipped : character.currentWeaponType();
   }

   private static WeaponPoiseTable.WeaponClass weaponOfStack(PGCharacter character) {
      try {
         ItemStack stack = character.getData().getWeapon();
         return stack != null && !stack.isEmpty() && stack.getItem() instanceof WeaponItem item
            ? weaponOfClass(item.getClass())
            : WeaponPoiseTable.WeaponClass.UNKNOWN;
      } catch (RuntimeException e) {
         return WeaponPoiseTable.WeaponClass.UNKNOWN;
      }
   }

   public static WeaponPoiseTable.WeaponClass weaponOfClass(@Nullable Class<?> weaponClass) {
      if (weaponClass == null) {
         return WeaponPoiseTable.WeaponClass.UNKNOWN;
      } else if (Sword.class.isAssignableFrom(weaponClass)) {
         return WeaponPoiseTable.WeaponClass.SWORD;
      } else if (Fist.class.isAssignableFrom(weaponClass)) {
         return WeaponPoiseTable.WeaponClass.FIST;
      } else if (Claymore.class.isAssignableFrom(weaponClass)) {
         return WeaponPoiseTable.WeaponClass.CLAYMORE;
      } else if (Polearm.class.isAssignableFrom(weaponClass)) {
         return WeaponPoiseTable.WeaponClass.POLEARM;
      } else if (Catalyst.class.isAssignableFrom(weaponClass)) {
         return WeaponPoiseTable.WeaponClass.CATALYST;
      } else {
         return Bow.class.isAssignableFrom(weaponClass) ? WeaponPoiseTable.WeaponClass.BOW : WeaponPoiseTable.WeaponClass.UNKNOWN;
      }
   }

   public static float basePoise(WeaponPoiseTable.WeaponClass weapon, @Nullable AttackType attackType) {
      if (attackType != null && weapon != null && weapon != WeaponPoiseTable.WeaponClass.UNKNOWN) {
         return switch (attackType) {
            case NORMAL_ATTACK -> normalPoise(weapon);
            case CHARGED_ATTACK -> chargedPoise(weapon);
            case PLUNGING_ATTACK -> landingPoise(weapon, WeaponPoiseTable.LandingPhase.FALL);
            default -> Float.NaN;
         };
      } else {
         return Float.NaN;
      }
   }

   public static float normalPoise(WeaponPoiseTable.WeaponClass weapon) {
      return switch (weapon) {
         case SWORD, FIST -> 50.0F;
         case CLAYMORE -> 107.4F;
         case POLEARM -> 45.8F;
         case CATALYST -> 10.2F;
         case BOW -> 15.7F;
         case UNKNOWN -> Float.NaN;
      };
   }

   public static float chargedPoise(WeaponPoiseTable.WeaponClass weapon) {
      return switch (weapon) {
         case SWORD, FIST -> 60.0F;
         case CLAYMORE -> 81.7F;
         case POLEARM -> 120.0F;
         case CATALYST -> 90.0F;
         case BOW -> 20.0F;
         case UNKNOWN -> Float.NaN;
      };
   }

   public static float landingPoise(WeaponPoiseTable.WeaponClass weapon, WeaponPoiseTable.LandingPhase phase) {
      if (weapon != null && phase != null) {
         return switch (weapon) {
            case SWORD, FIST -> {
               switch (phase) {
                  case FALL:
                     yield 25.0F;
                  case LOW:
                     yield 100.0F;
                  case HIGH:
                     yield 150.0F;
                  default:
                     throw new MatchException(null, null);
               }
            }
            case CLAYMORE -> {
               switch (phase) {
                  case FALL:
                     yield 35.0F;
                  case LOW:
                     yield 150.0F;
                  case HIGH:
                     yield 200.0F;
                  default:
                     throw new MatchException(null, null);
               }
            }
            case POLEARM -> {
               switch (phase) {
                  case FALL:
                     yield 25.0F;
                  case LOW:
                     yield 100.0F;
                  case HIGH:
                     yield 150.0F;
                  default:
                     throw new MatchException(null, null);
               }
            }
            case CATALYST -> {
               switch (phase) {
                  case FALL:
                     yield 5.0F;
                  case LOW:
                     yield 50.0F;
                  case HIGH:
                     yield 100.0F;
                  default:
                     throw new MatchException(null, null);
               }
            }
            case BOW -> {
               switch (phase) {
                  case FALL:
                     yield 10.0F;
                  case LOW:
                     yield 50.0F;
                  case HIGH:
                     yield 100.0F;
                  default:
                     throw new MatchException(null, null);
               }
            }
            case UNKNOWN -> Float.NaN;
         };
      } else {
         return Float.NaN;
      }
   }

   @Nullable
   public static ImpactLevel baseImpact(WeaponPoiseTable.WeaponClass weapon, @Nullable AttackType attackType) {
      if (attackType != null && weapon != null && weapon != WeaponPoiseTable.WeaponClass.UNKNOWN) {
         return switch (attackType) {
            case NORMAL_ATTACK -> levelOf(normalImpact(weapon));
            case CHARGED_ATTACK -> levelOf(chargedImpact(weapon));
            case PLUNGING_ATTACK -> levelOf(landingImpactLevel(weapon, WeaponPoiseTable.LandingPhase.FALL));
            default -> null;
         };
      } else {
         return null;
      }
   }

   public static int landingImpactLevel(WeaponPoiseTable.WeaponClass weapon, WeaponPoiseTable.LandingPhase phase) {
      if (weapon != null && phase != null) {
         return switch (weapon) {
            case SWORD, CLAYMORE, POLEARM, FIST -> {
               switch (phase) {
                  case FALL:
                     yield 2;
                  case LOW:
                     yield 4;
                  case HIGH:
                     yield 7;
                  default:
                     throw new MatchException(null, null);
               }
            }
            case CATALYST -> {
               switch (phase) {
                  case FALL:
                     yield 2;
                  case LOW:
                     yield 3;
                  case HIGH:
                     yield 4;
                  default:
                     throw new MatchException(null, null);
               }
            }
            case BOW -> {
               switch (phase) {
                  case FALL:
                     yield 2;
                  case LOW:
                     yield 2;
                  case HIGH:
                     yield 3;
                  default:
                     throw new MatchException(null, null);
               }
            }
            case UNKNOWN -> 0;
         };
      } else {
         return 0;
      }
   }

   public static int normalImpact(WeaponPoiseTable.WeaponClass weapon) {
      return switch (weapon) {
         case SWORD, CLAYMORE, POLEARM, FIST -> 3;
         case CATALYST -> 2;
         case BOW -> 1;
         case UNKNOWN -> 0;
      };
   }

   public static int chargedImpact(WeaponPoiseTable.WeaponClass weapon) {
      return switch (weapon) {
         case SWORD, FIST -> 2;
         case CLAYMORE -> 3;
         case POLEARM -> 5;
         case CATALYST -> 3;
         case BOW -> 2;
         case UNKNOWN -> 0;
      };
   }

   private static ImpactLevel levelOf(int level) {
      int clamped = Math.max(2, Math.clamp(level, 0, 9));
      return ImpactLevel.ofLevel(clamped);
   }

   public enum LandingPhase {
      FALL,
      LOW,
      HIGH;
   }

   public enum WeaponClass {
      SWORD,
      CLAYMORE,
      POLEARM,
      CATALYST,
      BOW,
      FIST,
      UNKNOWN;
   }
}
