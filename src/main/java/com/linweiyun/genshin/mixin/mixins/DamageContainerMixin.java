package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.mixin.interfaces.IDamageSourceModifier;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DamageContainer Mixin —— 允许在伤害管线中替换 DamageSource
 */
@Mixin(DamageContainer.class)
public class DamageContainerMixin implements IDamageSourceModifier {

    // 影子字段 —— 映射 DamageContainer 中原始的 source 字段
    @Final
    @Shadow
    private DamageSource source;

    // 注入字段 —— 存储替换后的伤害源
    @Unique
    private DamageSource modifiedSource;

    /**
     * @param newSource 替换用的伤害源（通常是内嵌了DamageSpec的ModDamageSource）
     */
    @Override
    public void setModifiedSource(DamageSource newSource) {
        this.modifiedSource = newSource;
    }

    @Override
    public DamageSource getEffectiveSource() {
        return this.modifiedSource != null ? this.modifiedSource : this.source;
    }

    /**
     * 拦截 getSource() 方法
     * 如果已设置替换源，返回替换源；否则返回原始源
     */
    @Inject(method = "getSource", at = @At("HEAD"), cancellable = true)
    public void onGetSource(CallbackInfoReturnable<DamageSource> cir) {
        cir.setReturnValue(getEffectiveSource());
    }
}
