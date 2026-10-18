package dev.spacebod.observer.mixin;

import dev.spacebod.observer.news.Newsroom;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notes how each death happened, in the game's own words, for the obituaries and the Mishaps desk. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@Inject(method = "die", at = @At("HEAD"))
	private void observer$onDie(DamageSource source, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		Component message = self.getCombatTracker().getDeathMessage();
		Newsroom.onDeath(self, message.getString(), message.getContents() instanceof TranslatableContents t ? t.getKey() : "");
	}
}
