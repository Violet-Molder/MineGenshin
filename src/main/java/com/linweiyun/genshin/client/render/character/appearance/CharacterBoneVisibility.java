// 单个角色的「骨骼显隐」控制器（基类-子类模式）。
//
// 目标：新增一个角色时，只要在【它自己的角色包里】放一个 XxxBoneVisibility 子类，
// 就自动生效 —— 不需要去任何其它包加一行接线。改某个角色的骨骼显隐也只改它自己包里
// 那个类，不碰共享工具类。
//
// 自动发现规则（约定优于配置）：
//   - 出战角色 instance 的实际类（比如 Shenhe）的「同包」里，放一个
//     简单名 = <角色类简单名>BoneVisibility 的子类（如 ShenheBoneVisibility）；
//   - 首次遇到某角色时用反射定位它并实例化（带缓存），之后直接复用；
//   - 找不到就退回基类默认。
//
// 模型规范（约定优于配置）：每个角色模型都有一根根骨骼叫 allbody（本体），
// 其余骨骼一组归为「其它 / 装饰」，另外可能有一根 weapon（武器）。
// 基类默认 = 只渲染 allbody 子树，隐藏 allbody 之外的一切，以及名为 weapon 的骨骼
// （无论 weapon 是在 allbody 内挂在手上、还是像法器一样独立在外 —— 全部隐藏，
// 防止挂在 allbody 内时被整棵 allbody 一起带出来）。需要亮的骨骼由子类覆写决定。
//
// 联动角色（IBCharacter）的模型是对方的，不按本 MOD 的命名约定，所以约定改成「按角色声明」：
// 该角色的 assets/minegenshin/character/<id>/resources.json 里写
//   "bones": { "body_root": "bone2", "weapon": ["blade_right"], "hide": [...] }
// 声明了就按声明走（body_root 写 "*" 表示不裁剪），没声明就还是上面的 allbody / weapon 约定。
// 见 CharacterBoneSpec 的注释。
//
// 注意：基类引用 client 渲染类型（BoneUpdater/Player…），只在客户端渲染路径触达它；
// 服务器端不会走到 forCharacter()，也就不会加载任何子类，天然安全。
package com.linweiyun.genshin.client.render.character.appearance;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.asset.source.CharacterBoneSpec;
import com.linweiyun.genshin.asset.source.CharacterResourceSources;
import com.linweiyun.genshin.core.character.PGCharacter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 单个角色「骨骼怎么显隐」的控制器。
 *
 * <p>唯一钩子 {@link #weaponUpdater}：根据玩家/角色返回一个 {@link BoneUpdater}，
 * 负责渲染前对骨骼做缩放 / <code>skipRender</code>。默认实现符合模型规范：
 * 只渲染 {@code allbody} 子树，其它骨骼一律藏掉，武器骨骼（{@code weapon}）也藏掉。
 *
 * <p>给角色定制：在它自己的角色包里写 <code>class XxxBoneVisibility extends CharacterBoneVisibility</code>
 * （同包 + 同名前缀），覆写 {@link #weaponUpdater} 即可，无需任何注册接线。
 * 见类头注释里的自动发现规则。
 */
public abstract class CharacterBoneVisibility {

   /** 无专属子类时的兜底：基类默认武器显隐。 */
   private static final CharacterBoneVisibility DEFAULT = new CharacterBoneVisibility() {
   };

   /** 角色类 → 已解析的控制器（避免每次反射）。 */
   private static final Map<Class<?>, CharacterBoneVisibility> CACHE = new ConcurrentHashMap<>();

   /**
    * 可选：显式给某个角色指定控制器（一般不需要 —— 反射自动发现即可）。
    *
    * <p>反射按「角色实际类 + BoneVisibility」命名约定自动定位，是默认主路径；
    * 这个方法只留给个别需要非约定命名的角色，或不想要反射时用。会覆盖约定结果。
    *
    * @param characterClass 角色实际类
    * @param controller     控制器实例
    */
   public static void register(Class<?> characterClass, CharacterBoneVisibility controller) {
      if (characterClass != null && controller != null) {
         CACHE.put(characterClass, controller);
      }
   }

   /** 包级构造：禁止外部直接 new，但允许同包/子类继承。 */
   protected CharacterBoneVisibility() {
   }

   /**
    * 取某角色的武器显隐控制器。
    *
    * <p>按 {@code character} 的实际类在「同包」里反射查找 {@code 类名+BoneVisibility} 子类；
    * 找不到（未定制 / 非角色实体 / null）就返回基类默认。
    *
    * @param character 当前出战角色，可为 null
    */
   public static CharacterBoneVisibility forCharacter(@Nullable PGCharacter character) {
      if (character == null) {
         return DEFAULT;
      }
      Class<?> roleClass = character.getClass();
      CharacterBoneVisibility resolved = CACHE.get(roleClass);
      // 缓存命中的真实控制器直接复用（含显式 register 和反射解析而来的）。
      if (resolved != null) {
         return resolved;
      }
      // 哨兵标记「该角色已确认没有子类」，避免反复反射；真实控制器从不为 null。
      if (CACHE.containsKey(roleClass)) {
         return DEFAULT;
      }
      CharacterBoneVisibility controller = resolve(roleClass);
      CACHE.put(roleClass, controller == null ? NULL_SENTINEL : controller);
      return controller == null ? DEFAULT : controller;
   }

   /** 兜底标记：占位，表示该角色无专属子类。 */
   private static final CharacterBoneVisibility NULL_SENTINEL = new CharacterBoneVisibility() {
   };

   /**
    * 反射定位角色同包的 <code>XxxBoneVisibility</code> 子类并实例化；找不到返回 null。
    */
   @Nullable
   private static CharacterBoneVisibility resolve(Class<?> roleClass) {
      String subtypeName = roleClass.getName() + "BoneVisibility";
      try {
         Class<?> subtype = Class.forName(subtypeName, false, roleClass.getClassLoader());
         if (subtype != null && CharacterBoneVisibility.class.isAssignableFrom(subtype)) {
            // 用同包构造：不强制 public，放开 setAccessible 以便各角色类默认私有/包级构造。
            java.lang.reflect.Constructor<?> ctor = subtype.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (CharacterBoneVisibility) ctor.newInstance();
         }
      } catch (ReflectiveOperationException | RuntimeException ignored) {
         // 没找到子类或实例化失败：退回默认。
      }
      return null;
   }

   /**
    * 计算本角色当前的骨骼显隐规则。
    *
    * <p>默认实现符合模型规范：遍历当前模型的所有骨骼，<b>只保留 {@code allbody} 子树</b>，
    * 其它骨骼（归为 other / 装饰）整棵隐藏；同时把名为 {@code weapon} 的骨骼也隐藏，
    * 无论它是挂在 allbody 内部（手上）还是独立在外（法器）。这样既不会让装饰件默认冒出来，
    * 也不会让藏在 allbody 里的武器被整棵带出来。
    *
    * <p>子类覆写时拿到的是相同的 {@code player}/{@code character}，可自行换成自己的骨骼名/动画状态。
    *
    * @param player    玩家（用于读动画状态等）
    * @param character 当前出战角色，可能为 null
    */
   public BoneUpdater<GeoRenderState> weaponUpdater(Player player, @Nullable PGCharacter character) {
      CharacterBoneSpec spec = boneSpecOf(character);
      return (renderPassInfo, snapshots) -> {
         Map<String, GeoBone> bones = renderPassInfo.model().boneLookup().get();
         if (bones == null || bones.isEmpty()) {
            return;
         }
         for (GeoBone bone : bones.values()) {
            if (shouldHide(bone, spec)) {
               snapshots.ifPresent(bone.name(), snapshot -> {
                  snapshot.setScale(0.0F, 0.0F, 0.0F);
                  snapshot.skipRender(true);
                  snapshot.skipChildrenRender(true);
               });
            }
         }
      };
   }

   /**
    * 取某个角色的骨骼约定。
    *
    * <p>来源表（{@code character/<id>/resources.json}）里写了 {@code bones} 段就按它走，
    * 没写（以及非联动角色）就是本 MOD 约定。
    */
   protected static CharacterBoneSpec boneSpecOf(@Nullable PGCharacter character) {
      return character == null ? CharacterBoneSpec.DEFAULT : CharacterResourceSources.bones(character.getTextureId());
   }

   /**
    * 判断某根骨骼是否默认隐藏。
    *
    * <p>规则：<b>不在 allbody 子树内</b> 的骨骼（即其它/装饰件），或<b>名为 {@code weapon}</b>
    * 的骨骼（防挂在 allbody 内时被连带渲染），都算作要隐藏。allbody 本身及其内部（除 weapon）
    * 一律保留。
    *
    * @param bone 待判断的骨骼
    */
   protected boolean shouldHide(GeoBone bone) {
      return shouldHide(bone, CharacterBoneSpec.DEFAULT);
   }

   /**
    * 按该角色的骨骼约定判断某根骨骼是否隐藏。
    *
    * <p>约定里 {@code body_root} 为 null 表示这个模型不做裁剪（本体之外的骨骼照常渲染），
    * 联动角色常常这么写 —— 对方的模型里没有本 MOD 那套 {@code allbody} 分层。
    *
    * @param bone 待判断的骨骼
    * @param spec 该角色的骨骼约定
    */
   protected boolean shouldHide(GeoBone bone, CharacterBoneSpec spec) {
      if (spec.hidden().contains(bone.name())) {
         return true;
      }
      if (spec.conventional() && WEAPON_BONE_NAME.equals(bone.name())) {
         return true;
      }
      String bodyRoot = spec.bodyRoot();
      if (bodyRoot == null) {
         return false;
      }
      // 本体根的祖先容器（如顶层 Root → allbody → …）必须保留：
      // 它本身在本体之外，但它的子树承载着本体，隐藏它会连带把整棵树藏掉。
      if (containsDescendant(bone, bodyRoot)) {
         return false;
      }
      return !isInside(bone, bodyRoot);
   }

   /** 武器骨骼在模型里的固定命名。 */
   protected static final String WEAPON_BONE_NAME = "weapon";

   /** 本体根骨骼在模型里的固定命名。 */
   protected static final String ALLBODY_BONE_NAME = "allbody";

   /**
    * 判断某根骨骼是否属于 {@code allbody} 的子树（沿 parent 向上，只要祖先链中出现过 allbody 就算）。
    *
    * <p>注意：allbody 不一定是模型的最顶层根（例如层级为 Root → allbody → …），
    * 因此这里逐级向上找，命中名为 {@code allbody} 即返回 true，而不是把骨骼一路顶到最顶根再比对。
    */
   private static boolean isInside(GeoBone bone, String bodyRoot) {
      GeoBone cursor = bone;
      while (cursor != null) {
         if (bodyRoot.equals(cursor.name())) {
            return true;
         }
         cursor = cursor.parent();
      }
      return false;
   }

   /**
    * 判断某根骨骼的子树中是否含有名为 {@code allbody} 的后代。
    *
    * <p>用于保护 allbody 的祖先容器（如 Root → allbody → …）：这类骨骼自身可能在 allbody
    * 之外，但携带本体子树，一旦被隐藏会连带整个模型消失，因此必须保留。
    */
   private static boolean containsDescendant(GeoBone bone, String bodyRoot) {
      for (GeoBone child : bone.children()) {
         if (bodyRoot.equals(child.name()) || containsDescendant(child, bodyRoot)) {
            return true;
         }
      }
      return false;
   }
}
