package com.linweiyun.genshin.client.render.character.appearance;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CharacterBonePhysics {
    private static final CharacterBonePhysics DEFAULT = new CharacterBonePhysics() { };

    private static final Map<Class<?>, CharacterBonePhysics> CACHE = new ConcurrentHashMap<>();

    /** 包级构造：禁止外部 new，允许同包/子类继承。 */
    protected CharacterBonePhysics() { }

    public static CharacterBonePhysics forCharacter(@Nullable PGCharacter character) {
        if (character == null) return DEFAULT;
        Class<?> roleClass = character.getClass();
        CharacterBonePhysics resolved = CACHE.get(roleClass);
        if (resolved != null) return resolved;
        if (CACHE.containsKey(roleClass)) return DEFAULT;
        CharacterBonePhysics controller = resolve(roleClass);
        CACHE.put(roleClass, controller == null ? DEFAULT : controller);
        return controller == null ? DEFAULT : controller;
    }

    @Nullable
    private static CharacterBonePhysics resolve(Class<?> roleClass) {
        String simpleName = roleClass.getSimpleName();
        // 多候选命名：既支持「角色类名+BonePhysics」，也支持「角色名（去 Character 后缀）+BonePhysics」，
        // 例如 SandroneCharacter -> SandroneBonePhysics
        String[] candidates = new String[] {
                roleClass.getName() + "BonePhysics",
                roleClass.getPackageName() + "." + stripCharacterSuffix(simpleName) + "BonePhysics"
        };
        for (String subtypeName : candidates) {
            try {
                Class<?> subtype = Class.forName(subtypeName, false, roleClass.getClassLoader());
                if (CharacterBonePhysics.class.isAssignableFrom(subtype)) {
                    var ctor = subtype.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    return (CharacterBonePhysics) ctor.newInstance();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private static String stripCharacterSuffix(String simpleName) {
        return simpleName.endsWith("Character")
                ? simpleName.substring(0, simpleName.length() - "Character".length())
                : simpleName;
    }

    /** 唯一钩子：返回一个 BoneUpdater，在每帧动画结算后给骨骼快照叠加物理旋转。 */
    public RenderPassInfo.BoneUpdater<GeoRenderState> clothUpdater(Player player, PGCharacter character) {
        return null;   // 默认无物理
    }
}