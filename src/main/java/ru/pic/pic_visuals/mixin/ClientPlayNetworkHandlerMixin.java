package ru.pic.pic_visuals.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.pic.pic_visuals.Stats;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    // require = 0: if this ever fails to apply, the mod still works (TPS just shows "--").
    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"), require = 0)
    private void pic$timeUpdate(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        Stats.onTimeUpdate();
    }
}
