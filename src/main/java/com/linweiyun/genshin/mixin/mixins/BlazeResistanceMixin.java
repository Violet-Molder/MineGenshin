package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.content.entities.teyvat.TeyvatLiving;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Blaze.class)
public abstract class BlazeResistanceMixin implements TeyvatLiving {

    private static final float PYRO_RESISTANCE = 0.80f;

    @Override
    public float getElementResistance(GenshinElement element) {
        if (element == ModElements.PYRO.get()) return PYRO_RESISTANCE;
        return 0.10f;
    }

    @Override
    public float getPhysicalResistance() {
        return 0.10f;
    }
}