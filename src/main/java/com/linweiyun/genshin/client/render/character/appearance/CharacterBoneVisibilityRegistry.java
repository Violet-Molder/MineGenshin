// 角色「武器显隐」控制器的可选显式登记表（不是必需的）。
//
// 主路径是反射自动发现：新角色只要在自己的角色包里放一个同名前缀的
// CharacterBoneVisibility 子类（XxxBoneVisibility）就自动生效，什么都不用改。
//
// 这个登记表只是给「个别不想走反射、或子类命名不遵循约定」的角色提供一个
// 显式指定控制器的入口 —— 一般用不上。真要加新角色，优先照反射约定来。
package com.linweiyun.genshin.client.render.character.appearance;

/**
 * 角色「武器显隐」控制器的可选显式登记入口（反射自动发现通常是更优解）。
 *
 * <p>仅当某个角色不想遵循「同包 + <code>角色类名+BoneVisibility</code>」的反射约定、
 * 想手动绑定控制器时才需要来这里。绝大多数新角色无需使用本类。
 */
public final class CharacterBoneVisibilityRegistry {

    private CharacterBoneVisibilityRegistry() {
    }

    public static void registerAll() {
        // 示例：申鹤默认走反射自动发现的 ShenheBoneVisibility，无需在这里显式绑定。
        // 若某角色需要非约定命名，可以这样：
        // CharacterBoneVisibility.register(SomeCharacter.class, new SomeController());
    }
}