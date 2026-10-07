package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.asset.AssetRedirects;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源重定向层的注入点：给原版的文件表补进本项目布局里的文件。
 *
 * <p>原版四个入口都经由 {@link FileToIdConverter}（方块状态 / 物品定义 / 模型 / 图集贴图），
 * 所以在这一处补条目，四条通道同时生效。规则与边界见 {@link AssetRedirects}：
 * 只处理本 MOD 命名空间、只处理它认识的四个根、且原版已有同名真实文件时不覆盖
 * （{@code putIfAbsent}），方便临时用原版目录做 A/B 对照。
 *
 * <p>注入点选在 {@code RETURN}：原版自己的扫描结果一个都不动，只做「追加」。
 */
@Mixin(FileToIdConverter.class)
public class FileToIdConverterRedirectMixin {

    /** 这个转换器管的目录（目标类的 {@code prefix} 字段）。 */
    @Shadow
    @Final
    private String prefix;

    /** 单个文件的通道：{@code items} / {@code models} / {@code textures/<子目录>}。 */
    @Inject(method = "listMatchingResources", at = @At("RETURN"), cancellable = true)
    private void minegenshin$injectRedirectedResources(ResourceManager manager,
                                                       CallbackInfoReturnable<Map<ResourceLocation, Resource>> cir) {
        Map<ResourceLocation, Resource> extra = AssetRedirects.resolve(this.minegenshin$directory(), manager);
        if (extra.isEmpty()) {
            return;
        }

        Map<ResourceLocation, Resource> merged = new HashMap<>(cir.getReturnValue());
        boolean added = false;
        for (Map.Entry<ResourceLocation, Resource> entry : extra.entrySet()) {
            if (merged.putIfAbsent(entry.getKey(), entry.getValue()) == null) {
                added = true;
            }
        }
        if (added) {
            cir.setReturnValue(merged);
        }
    }

    /** 资源栈的通道：{@code blockstates}（原版按「多包叠加」读，返回的是 List）。 */
    @Inject(method = "listMatchingResourceStacks", at = @At("RETURN"), cancellable = true)
    private void minegenshin$injectRedirectedStacks(ResourceManager manager,
                                                    CallbackInfoReturnable<Map<ResourceLocation, List<Resource>>> cir) {
        Map<ResourceLocation, Resource> extra = AssetRedirects.resolve(this.minegenshin$directory(), manager);
        if (extra.isEmpty()) {
            return;
        }

        Map<ResourceLocation, List<Resource>> merged = new HashMap<>(cir.getReturnValue());
        boolean added = false;
        for (Map.Entry<ResourceLocation, Resource> entry : extra.entrySet()) {
            if (merged.putIfAbsent(entry.getKey(), List.of(entry.getValue())) == null) {
                added = true;
            }
        }
        if (added) {
            cir.setReturnValue(merged);
        }
    }

    /** 这个转换器管的目录。 */
    private String minegenshin$directory() {
        return this.prefix;
    }
}
