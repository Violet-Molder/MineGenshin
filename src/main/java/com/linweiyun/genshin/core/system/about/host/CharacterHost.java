package com.linweiyun.genshin.core.system.about.host;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.elementlib.core.system.about.AttachmentProfile;
import com.linweiyun.elementlib.core.system.about.AttachmentSource;
import com.linweiyun.elementlib.core.system.about.host.ElementalHost;
import com.linweiyun.elementlib.core.attachment.StatusContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * <b>角色宿主</b> —— 附着挂在「出战角色」而不是玩家身上的那类载体。
 *
 * <p>存在的理由：玩家实体只有一个，但队伍里有多个角色，附着必须跟着角色走 ——
 * 站进冰雾挂的是<b>当前出战角色</b>的冰，切人就是换一个附着（见
 * {@code core/status/CharacterChillHandler}）。以前这类附着靠 {@code attach(player, 角色容器, ...)}
 * 这种「目标与容器不是同一个东西」的写法实现，现在它就是 {@link ElementalHost} 的一个正常实现。
 */
public final class CharacterHost implements ElementalHost {

    private final PGCharacter character;

    private CharacterHost(PGCharacter character) {
        this.character = character;
    }

    @Nullable
    public static CharacterHost of(@Nullable PGCharacter character) {
        return character == null ? null : new CharacterHost(character);
    }

    @Override
    public boolean isValid() {
        return character.getData() != null;
    }

    @Override
    public StatusContainer container() {
        return isValid() ? character.getData().getStatusContainer() : null;
    }

    @Override
    public String hostKey() {
        return "character:" + character.getCharacterUUID()
                + "::" + character.getClass().getSimpleName();
    }

    /**
     * 角色宿主不做额外筛查：角色该收到什么元素由挂载它的来源决定
     * （冰雾给谁挂、什么时候挂，是技能自己的事）。
     */
    @Override
    public boolean acceptsElement(GenshinElement element, AttachmentSource source,
                                  AttachmentProfile profile) {
        return true;
    }

    /**
     * 角色宿主的元素钩子是空实现。
     *
     * <p>元素本体上的 {@code onAttach} 是「附着挂在实体上时顺便做的效果」，
     * 而角色宿主的宿主不是实体（一个实体装着多个角色），效果的承接者是玩家 ——
     * 这类效果必须在宿主之外按「出战角色是谁」逐 tick 重算，见 {@code CharacterChillHandler}。
     */
    @Override
    public void onElementAttached(GenshinElement element) {
    }

    @Override
    public void onElementDetached(GenshinElement element) {
    }

    /**
     * 反应飘字的落点：出水/出火这类环境附着触发的蒸发、冻结，都发生在「出战角色」身上，
     * 但玩家在世界上操作的那个身体就是玩家实体，所以字落在玩家头上。
     *
     * <p>只做飘字。上面两个 {@code onElement*} 仍然是空的：效果不能顺手挂到玩家实体上，
     * 否则切人之后那份效果还留在玩家身上（这正是「角色宿主不是实体」的原因）。
     */
    @Override
    public LivingEntity indicatorAnchor() {
        var data = character.getData();
        if (data == null) return null;
        Player owner = data.getOwnerPlayer();
        return owner != null && owner.isAlive() ? owner : null;
    }

    public PGCharacter character() {
        return character;
    }

    @Override
    public String toString() {
        return hostKey();
    }
}
