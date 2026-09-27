package com.linweiyun.genshin.content.effect.character;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import javax.annotation.Nullable;

public class CharacterEffectHelper {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);
    /**
     * 获取角色身上的效果容器
     * @param character 角色数据
     * @return 效果容器
     */
    public static CharacterEffectContainer getEffects(PGCharacterData character) {
        return character.getEffectContainer();                               // 从角色数据获取效果容器
    }

    /**
     * 添加效果到角色
     * 如果角色已有相同效果，调用覆盖方法；否则调用添加方法
     * @param holder 效果持有者（玩家）
     * @param character 效果作用的角色数据
     * @param instance 要添加的效果实例
     */
    public static void addEffect(Player holder, PGCharacter character, CharacterEffectInstance instance) {
        CharacterEffectContainer container = character.getData().getEffectContainer(); // 获取效果容器
        ICharacterEffect newEffect = instance.getEffect();                   // 从注册表获取效果对象
        if (newEffect == null) return;                                       // 效果未注册，直接返回

        // 互斥组：高优先级在场时，低优先级直接不上场（例如 星超导 在场则不接受 星扩散）
        if (!newEffect.canApplyWith(container)) {                            // 检查能否共存
            LOGGER.debug("[效果] {} 被互斥规则拦下（当前容器已有更高优先级效果）| char={}",
                    CharacterEffectContainer.getEffectIdString(newEffect), character.getName());
            return;
        }

        CharacterEffectInstance existing = container.getEffectInstance(instance.getEffectIdString()); // 查找已有同ID效果

        if (existing != null) {
            // 已有相同效果 → 调用覆盖方法，由各效果类自行决定如何处理（叠加/刷新/忽略等）
            newEffect.onEffectOverride(holder, character, existing, instance);
            container.addEffect(instance);                                   // 替换旧效果数据
        } else {
            // 没有相同效果 → 直接添加
            container.addEffect(instance);                                   // 添加到容器
            newEffect.onEffectAdded(holder, character, instance);            // 调用添加回调
        }
        character.getData().syncEffectsToTag();                                        // 同步效果数据到NBT标签
    }

    /**
     * 从角色移除效果
     * @param holder 效果持有者（玩家）
     * @param character 效果作用的角色数据
     * @param effect 要移除的效果对象
     */
    public static void removeEffect(Player holder, PGCharacter character, ICharacterEffect effect) {
        CharacterEffectContainer container = character.getData().getEffectContainer(); // 获取效果容器
        CharacterEffectInstance instance = container.getEffectInstance(effect); // 查找效果实例

        if (instance != null) {
            effect.onEffectRemoved(holder, character, instance);             // 调用移除回调
            container.removeEffect(effect);                                  // 从容器移除
        }

        character.getData().syncEffectsToTag();                                        // 同步效果数据到NBT标签
    }

    /**
     * 清除角色身上的所有效果
     * @param holder 效果持有者（玩家）
     * @param character 效果作用的角色数据
     */
    public static void clearEffects(Player holder, PGCharacter character) {
        CharacterEffectContainer container = character.getData().getEffectContainer(); // 获取效果容器
        for (CharacterEffectInstance instance : container.getEffects()) {    // 遍历所有效果
            ICharacterEffect effect = instance.getEffect();                  // 获取效果对象
            if (effect != null) {
                effect.onEffectRemoved(holder, character, instance);         // 调用移除回调
            }
        }
        container.clear();                                                   // 清空容器
        character.getData().syncEffectsToTag();                                        // 同步效果数据到NBT标签
    }

    /**
     * 获取角色身上指定效果的实例
     * @param character 角色数据
     * @param effect 效果对象
     * @return 效果实例，不存在返回null
     */
    public static @Nullable CharacterEffectInstance getEffectInstance(PGCharacterData character, ICharacterEffect effect) {
        return character.getEffectContainer().getEffectInstance(effect);     // 从容器查找
    }

    /**
     * 检查角色是否拥有指定效果
     * @param character 角色数据
     * @param effect 效果对象
     * @return 是否拥有该效果
     */
    public static boolean hasEffect(PGCharacterData character, ICharacterEffect effect) {
        return character.getEffectContainer().hasEffect(effect);             // 从容器检查
    }
}
