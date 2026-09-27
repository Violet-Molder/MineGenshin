// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.core.asset.pack.GeoPackResources;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPackResourceManager.class)
public class MultiPackResourceManagerBundleMixin {
   @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
   private void minegenshin$bundleResource(Identifier location, CallbackInfoReturnable<Optional<Resource>> cir) {
      Resource bundled = GeoPackResources.resolve(this.minegenshin$manager(), location);
      if (bundled != null) {
         cir.setReturnValue(Optional.of(bundled));
      }
   }

   @Inject(method = "getResourceStack", at = @At("HEAD"), cancellable = true)
   private void minegenshin$bundleResourceStack(Identifier location, CallbackInfoReturnable<List<Resource>> cir) {
      Resource bundled = GeoPackResources.resolve(this.minegenshin$manager(), location);
      if (bundled != null) {
         cir.setReturnValue(List.of(bundled));
      }
   }

   @Inject(method = "listResources", at = @At("RETURN"), cancellable = true)
   private void minegenshin$bundleListResources(String directory, Predicate<Identifier> filter, CallbackInfoReturnable<Map<Identifier, Resource>> cir) {
      Map<Identifier, Resource> bundled = GeoPackResources.under(this.minegenshin$manager(), directory, filter);
      if (!bundled.isEmpty()) {
         Map<Identifier, Resource> merged = new LinkedHashMap<>((Map<? extends Identifier, ? extends Resource>)cir.getReturnValue());
         bundled.forEach(merged::putIfAbsent);
         cir.setReturnValue(merged);
      }
   }

   @Inject(method = "listResourceStacks", at = @At("RETURN"), cancellable = true)
   private void minegenshin$bundleListResourceStacks(
      String directory, Predicate<Identifier> filter, CallbackInfoReturnable<Map<Identifier, List<Resource>>> cir
   ) {
      Map<Identifier, Resource> bundled = GeoPackResources.under(this.minegenshin$manager(), directory, filter);
      if (!bundled.isEmpty()) {
         Map<Identifier, List<Resource>> merged = new LinkedHashMap<>((Map<? extends Identifier, ? extends List<Resource>>)cir.getReturnValue());
         bundled.forEach((location, resource) -> merged.putIfAbsent(location, List.of(resource)));
         cir.setReturnValue(merged);
      }
   }

   private ResourceManager minegenshin$manager() {
      return (ResourceManager)this;
   }
}
