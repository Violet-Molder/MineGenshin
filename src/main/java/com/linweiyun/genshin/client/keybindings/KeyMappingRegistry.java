package com.linweiyun.genshin.client.keybindings;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

/**
 * 角色动作的键位与输入拦截。
 *
 * <h2>键位（对齐参考2，去掉格挡）</h2>
 * <ul>
 *   <li>普攻 = 鼠标左键（含按住蓄力）</li>
 *   <li>战技 = C，闪避 = X，大招 = R</li>
 *   <li>抽卡 = H（原来占着 R，让给大招了）</li>
 * </ul>
 *
 * <h2>为什么继承 KeyMapping 重写 setDown</h2>
 * {@code setDown} 在 GLFW 回调里、比 tick 更早被调用，在其中直接触发动作相当于
 * 「按下的当帧就出动画」，对连招手感有实际影响。tick 里的 {@code consumeClick()} 仍要调，
 * 用来把按键队列排空，否则其它逻辑会读到积压状态。
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class KeyMappingRegistry {

  public static final KeyMapping.Category CATEGORY =
          new KeyMapping.Category(Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "category"));

  /** 动作键基类：只在「按下」的那一帧回调一次，并且不依赖 tick 队列。 */
  private abstract static class ActionKey extends KeyMapping {
    private boolean wasDown;

    ActionKey(String name, int keyCode) {
      super(name, InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }

    ActionKey(String name, InputConstants.Type type, int keyCode) {
      super(name, type, keyCode, CATEGORY);
    }

    @Override
    public void setDown(boolean isDown) {
      super.setDown(isDown);

      if (this.wasDown == isDown) return;
      this.wasDown = isDown;

      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) return;
      if (!TeyvatWorldInvasion.isClientInvaded()) return;
      if (!isInGenshinMode(player)) return;

      if (isDown) {
        onPressed(player);
      } else {
        onReleased(player);
      }
    }

    protected void onPressed(LocalPlayer player) {}

    protected void onReleased(LocalPlayer player) {}
  }

  /** 普攻：鼠标左键。按下即出招，按住则蓄力。 */
  public static final Lazy<KeyMapping> ATTACK_KEY = Lazy.of(() ->
          new ActionKey("key.minegenshin.attack", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            @Override
            protected void onPressed(LocalPlayer player) {
              // 大剑（持续型重击）按下先不出普攻，见 ActionStateMachine#pressAttack
              ActionStateMachine.pressAttack(player);
            }

            @Override
            protected void onReleased(LocalPlayer player) {
              ActionStateMachine.releaseAttack(player);
            }
          });

  /** 战技（C）。短按 / 长按有区别的角色走按住判定，见 {@link ActionStateMachine#pressSkill}。 */
  public static final Lazy<KeyMapping> C_KEY = Lazy.of(() ->
          new ActionKey("key.minegenshin.c_mode", GLFW.GLFW_KEY_C) {
            @Override
            protected void onPressed(LocalPlayer player) {
              ActionStateMachine.pressSkill(player);
            }

            @Override
            protected void onReleased(LocalPlayer player) {
              ActionStateMachine.releaseSkill(player);
            }
          });

  /** 闪避（X）。 */
  public static final Lazy<KeyMapping> X_KEY = Lazy.of(() ->
          new ActionKey("key.minegenshin.x_mode", GLFW.GLFW_KEY_X) {
            @Override
            protected void onPressed(LocalPlayer player) {
              ActionStateMachine.tryDodge(player);
            }
          });

  /** 大招（R）。 */
  public static final Lazy<KeyMapping> R_KEY = Lazy.of(() ->
          new ActionKey("key.minegenshin.r_mode", GLFW.GLFW_KEY_R) {
            @Override
            protected void onPressed(LocalPlayer player) {
              ActionStateMachine.tryUltimate(player);
            }
          });

  /** 抽卡（H）：原来绑在 R 上，R 让给大招了。 */
  public static final Lazy<KeyMapping> WISH_KEY = Lazy.of(() ->
          new KeyMapping("key.minegenshin.wish", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));

  public static final Lazy<KeyMapping> G_KEY = Lazy.of(() ->
          new KeyMapping("key.minegenshin.g_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));

  public static final Lazy<KeyMapping> V_KEY = Lazy.of(() ->
          new KeyMapping("key.minegenshin.v_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));

  public static final Lazy<KeyMapping> O_KEY = Lazy.of(() ->
          new KeyMapping("key.minegenshin.o_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));

  public static final Lazy<KeyMapping> F_KEY = Lazy.of(() ->
          new KeyMapping("key.minegenshin.f_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY));

  public static final Lazy<KeyMapping> CHARACTER_INFO_SCREEN_KEY = Lazy.of(() ->
          new KeyMapping("key.minegenshin.character_info_screen_key", InputConstants.Type.KEYSYM,
                  GLFW.GLFW_KEY_U, CATEGORY));

  public static final Lazy<KeyMapping> ARTIFACT_EQUIP_SCREEN_KEY = Lazy.of(() ->
          new KeyMapping("key.minegenshin.artifact_equip_screen_key", InputConstants.Type.KEYSYM,
                  GLFW.GLFW_KEY_B, CATEGORY));

  /** 升格界面（N）。 */
  public static final Lazy<KeyMapping> ARTIFACT_EQUIP_SCREEN_KEY_2 = Lazy.of(() ->
          new KeyMapping("key.minegenshin.artifact_equip_screen_key_2", InputConstants.Type.KEYSYM,
                  GLFW.GLFW_KEY_N, CATEGORY));

  /**
   * 角色配置页（K）。
   *
   * <p>这个键原来绑在 K 上、且在 {@code KeyInputHandler} 里<b>只读了 isDown 什么都没做</b>
   * （空壳）。现在它真的接上了：打开当前出战角色的配置页
   * （{@code ScreenNavigator.openCharacterConfigScreen}）。
   *
   * <p><b>2026-09-27 用户口径：就留在 K</b>（曾经计划过和「升格界面」对调、让配置页用 N，
   * 后来取消）—— 所以这里绑 K，升格界面（{@link #ARTIFACT_EQUIP_SCREEN_KEY_2}）回到 N。
   * 注意用户自己的 options.txt 里一直留着的也是 K，改回来之后文字与实机就一致了。
   */
  public static final Lazy<KeyMapping> CONFIG_SCREEN_KEY = Lazy.of(() ->
          new KeyMapping("key.minegenshin.config_key", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

  private static boolean isInGenshinMode(LocalPlayer player) {
    return player.hasData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT)
            && player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT);
  }

  @SubscribeEvent // on the mod event bus only on the physical client
  public static void registerBindings(RegisterKeyMappingsEvent event) {
    event.registerCategory(CATEGORY);
    event.register(ATTACK_KEY.get());
    event.register(C_KEY.get());
    event.register(X_KEY.get());
    event.register(R_KEY.get());
    event.register(WISH_KEY.get());
    event.register(G_KEY.get());
    event.register(V_KEY.get());
    event.register(O_KEY.get());
    event.register(F_KEY.get());
    event.register(CHARACTER_INFO_SCREEN_KEY.get());
    event.register(ARTIFACT_EQUIP_SCREEN_KEY.get());
    event.register(ARTIFACT_EQUIP_SCREEN_KEY_2.get());
    event.register(CONFIG_SCREEN_KEY.get());
  }

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Post event) {
    // 动作已在 setDown 里 0 延迟触发，这里只是把按键队列排空
    drain(ATTACK_KEY.get());
    drain(C_KEY.get());
    drain(X_KEY.get());
    drain(R_KEY.get());
  }

  private static void drain(KeyMapping keyMapping) {
    while (keyMapping.consumeClick()) {
      // 仅排空点击队列
    }
  }

  /**
   * 变身后屏蔽原版左键行为（挖方块 / 攻击冷却条）。
   *
   * <p>左键被复用成了「普攻」，而原版 {@code keyAttack} 默认也是左键，
   * 不拦的话玩家一出手就会顺手把脚下的方块挖了。
   */
  @SubscribeEvent
  public static void onInteractionKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
    if (!event.isAttack()) return;

    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null || !isInGenshinMode(player)) return;

    event.setSwingHand(false);
    event.setCanceled(true);
  }

  /** 供网络层/调试用：请服务端切换原神模式。 */
  public static void requestGenshinMode(boolean enabled) {
    NetworkManager.setGenshinModeToServer(enabled);
  }
}
