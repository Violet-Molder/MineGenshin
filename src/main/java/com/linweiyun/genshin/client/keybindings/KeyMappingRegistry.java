// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.keybindings;

import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.common.util.Lazy;

@EventBusSubscriber(Dist.CLIENT)
public class KeyMappingRegistry {
   public static final Category CATEGORY = new Category(Identifier.fromNamespaceAndPath("minegenshin", "category"));
   public static final Lazy<KeyMapping> ATTACK_KEY = Lazy.of(() -> new KeyMappingRegistry.ActionKey("key.minegenshin.attack", Type.MOUSE, 0) {
      @Override
      protected void onPressed(LocalPlayer player) {
         ActionStateMachine.pressAttack(player);
      }

      @Override
      protected void onReleased(LocalPlayer player) {
         ActionStateMachine.releaseAttack(player);
      }
   });
   public static final Lazy<KeyMapping> C_KEY = Lazy.of(() -> new KeyMappingRegistry.ActionKey("key.minegenshin.c_mode", 67) {
      @Override
      protected void onPressed(LocalPlayer player) {
         ActionStateMachine.pressSkill(player);
      }

      @Override
      protected void onReleased(LocalPlayer player) {
         ActionStateMachine.releaseSkill(player);
      }
   });
   public static final Lazy<KeyMapping> X_KEY = Lazy.of(() -> new KeyMappingRegistry.ActionKey("key.minegenshin.x_mode", 88) {
      @Override
      protected void onPressed(LocalPlayer player) {
         ActionStateMachine.tryDodge(player);
      }
   });
   public static final Lazy<KeyMapping> R_KEY = Lazy.of(() -> new KeyMappingRegistry.ActionKey("key.minegenshin.r_mode", 82) {
      @Override
      protected void onPressed(LocalPlayer player) {
         ActionStateMachine.tryUltimate(player);
      }
   });
   public static final Lazy<KeyMapping> WISH_KEY = Lazy.of(() -> new KeyMapping("key.minegenshin.wish", Type.KEYSYM, 72, CATEGORY));
   public static final Lazy<KeyMapping> G_KEY = Lazy.of(() -> new KeyMapping("key.minegenshin.g_mode", Type.KEYSYM, 71, CATEGORY));
   public static final Lazy<KeyMapping> V_KEY = Lazy.of(() -> new KeyMapping("key.minegenshin.v_mode", Type.KEYSYM, 86, CATEGORY));
   public static final Lazy<KeyMapping> O_KEY = Lazy.of(() -> new KeyMapping("key.minegenshin.o_mode", Type.KEYSYM, 79, CATEGORY));
   public static final Lazy<KeyMapping> F_KEY = Lazy.of(() -> new KeyMapping("key.minegenshin.f_mode", Type.KEYSYM, 70, CATEGORY));
   public static final Lazy<KeyMapping> CHARACTER_INFO_SCREEN_KEY = Lazy.of(
      () -> new KeyMapping("key.minegenshin.character_info_screen_key", Type.KEYSYM, 85, CATEGORY)
   );
   public static final Lazy<KeyMapping> ARTIFACT_EQUIP_SCREEN_KEY = Lazy.of(
      () -> new KeyMapping("key.minegenshin.artifact_equip_screen_key", Type.KEYSYM, 66, CATEGORY)
   );
   public static final Lazy<KeyMapping> ARTIFACT_EQUIP_SCREEN_KEY_2 = Lazy.of(
      () -> new KeyMapping("key.minegenshin.artifact_equip_screen_key_2", Type.KEYSYM, 78, CATEGORY)
   );
   public static final Lazy<KeyMapping> CONFIG_SCREEN_KEY = Lazy.of(() -> new KeyMapping("key.minegenshin.config_key", Type.KEYSYM, 75, CATEGORY));

   private static boolean isInGenshinMode(LocalPlayer player) {
      return player.hasData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT) && (Boolean)player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT);
   }

   @SubscribeEvent
   public static void registerBindings(RegisterKeyMappingsEvent event) {
      event.registerCategory(CATEGORY);
      event.register((KeyMapping)ATTACK_KEY.get());
      event.register((KeyMapping)C_KEY.get());
      event.register((KeyMapping)X_KEY.get());
      event.register((KeyMapping)R_KEY.get());
      event.register((KeyMapping)WISH_KEY.get());
      event.register((KeyMapping)G_KEY.get());
      event.register((KeyMapping)V_KEY.get());
      event.register((KeyMapping)O_KEY.get());
      event.register((KeyMapping)F_KEY.get());
      event.register((KeyMapping)CHARACTER_INFO_SCREEN_KEY.get());
      event.register((KeyMapping)ARTIFACT_EQUIP_SCREEN_KEY.get());
      event.register((KeyMapping)ARTIFACT_EQUIP_SCREEN_KEY_2.get());
      event.register((KeyMapping)CONFIG_SCREEN_KEY.get());
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      drain((KeyMapping)ATTACK_KEY.get());
      drain((KeyMapping)C_KEY.get());
      drain((KeyMapping)X_KEY.get());
      drain((KeyMapping)R_KEY.get());
   }

   private static void drain(KeyMapping keyMapping) {
      while (keyMapping.consumeClick()) {
      }
   }

   @SubscribeEvent
   public static void onInteractionKeyMapping(InteractionKeyMappingTriggered event) {
      if (event.isAttack()) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && isInGenshinMode(player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
         }
      }
   }

   public static void requestGenshinMode(boolean enabled) {
      NetworkManager.setGenshinModeToServer(enabled);
   }

   private abstract static class ActionKey extends KeyMapping {
      private boolean wasDown;

      ActionKey(String name, int keyCode) {
         super(name, Type.KEYSYM, keyCode, KeyMappingRegistry.CATEGORY);
      }

      ActionKey(String name, Type type, int keyCode) {
         super(name, type, keyCode, KeyMappingRegistry.CATEGORY);
      }

      public void setDown(boolean isDown) {
         super.setDown(isDown);
         if (this.wasDown != isDown) {
            this.wasDown = isDown;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
               if (TeyvatWorldInvasion.isClientInvaded()) {
                  if (KeyMappingRegistry.isInGenshinMode(player)) {
                     if (isDown) {
                        this.onPressed(player);
                     } else {
                        this.onReleased(player);
                     }
                  }
               }
            }
         }
      }

      protected void onPressed(LocalPlayer player) {
      }

      protected void onReleased(LocalPlayer player) {
      }
   }
}
