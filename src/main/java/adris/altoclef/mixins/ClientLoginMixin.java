package adris.altoclef.mixins;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.ClientLoginEvent;
import adris.altoclef.eventbus.events.ClientTickEvent;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLoginNetworkHandler.class)
public class ClientLoginMixin {
    @Inject(method = "onHello", at = @At("HEAD"))
    private void onHello(LoginHelloC2SPacket packet, CallbackInfo ci) {
       // EventBus.publish(new ClientTickEvent());
        EventBus.publish(new ClientLoginEvent());
    }
}