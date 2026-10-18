package dev.spacebod.observer.mixin;

import dev.spacebod.observer.news.Newsroom;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Notes each advancement as it is completed: the rewards are granted exactly once, at that moment. */
@Mixin(PlayerAdvancements.class)
abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@Inject(method = "award", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"))
	private void observer$onCompleted(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		Newsroom.onAdvancement(player, holder);
	}
}
