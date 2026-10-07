package com.linweiyun.genshin.core.status;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.elementlib.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.elementlib.core.system.about.ElementalAttachmentInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * <b>角色身上的寒元素（冰附着减速）</b> —— 让减速跟着「出战角色」走，而不是跟着玩家走。
 *
 * <h2>为什么不能直接用 {@code CryoElement.onAttach}</h2>
 * 元素自带的减速只对<b>非玩家生物</b>生效（{@code GenshinElement.isNonPlayerLiving} 把它拦掉了），
 * 而且它是挂在实体身上的。冰雾要的是「附着挂在角色上、减速挂在玩家身上」，
 * 这样切人就是天然的「换一个附着」：
 *
 * <pre>
 * 站进冰雾 → 出战角色挂上弱冰 → 玩家被减速
 * 切人     → 新角色没有冰附着 → 减速移除        （切角色取消）
 * 切回来   → 那个角色的冰附着还在 → 减速恢复    （切回来依旧）
 * 附着到期 → 减速移除
 * </pre>
 *
 * <p>每 tick 重算一次，所以不需要维护「谁被减速了」的额外状态 ——
 * 状态就是「出战角色身上有没有冰附着」这一个事实。
 */
@EventBusSubscriber
public final class CharacterChillHandler {

    /** 减速修饰符的 id。 */
    private static final ResourceLocation SLOW_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, "character_cryo_slow");

    /** 减速幅度（百分比，负数是减速）。 */
    private static final float SLOW_AMOUNT = -0.15f;

    private CharacterChillHandler() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean chilled = isActiveCharacterChilled(player);
        boolean alreadySlowed = speed.hasModifier(SLOW_MODIFIER_ID);

        if (chilled && !alreadySlowed) {
            speed.addTransientModifier(new AttributeModifier(
                    SLOW_MODIFIER_ID, SLOW_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!chilled && alreadySlowed) {
            speed.removeModifier(SLOW_MODIFIER_ID);
        }
    }

    /** 出战角色身上有没有冰（或冻）附着。 */
    private static boolean isActiveCharacterChilled(Player player) {
        if (!player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT)) {
            return false;
        }
        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (attachment == null) {
            return false;
        }
        PGCharacter current = attachment.getCurrentCharacter();
        if (current == null) {
            return false;
        }
        return hasCryoAura(StatusAccessor.of(current.getData()));
    }

    private static boolean hasCryoAura(StatusContainer container) {
        if (container == null) {
            return false;
        }
        for (var instance : container.getAll()) {
            if (instance.isFinished() || !(instance instanceof ElementalAttachmentInstance attachment)) {
                continue;
            }
            GenshinElement element = attachment.getElement();
            if (element == null) {
                continue;
            }
            if (element.getMainElement() == ModElements.CYRO.get() && attachment.getUnit() > 0f) {
                return true;
            }
        }
        return false;
    }
}
