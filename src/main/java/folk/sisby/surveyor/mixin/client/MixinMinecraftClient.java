package folk.sisby.surveyor.mixin.client;

import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.client.SurveyorClientEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
	@Inject(method = "joinWorld", at = @At("HEAD"))
	void saveOnLeaveWorld(ClientWorld newWorld, CallbackInfo ci) {
		MinecraftClient self = (MinecraftClient) (Object) this;
		if (self.world != null && WorldSummary.of(self.world).isClient()) {
			WorldSummary.of(self.world).save(self.world, SurveyorClient.getWorldSavePath(self.world), false);
		}
	}

	@Inject(method = "joinWorld", at = @At("TAIL"))
	void loadOnJoinWorld(ClientWorld newWorld, CallbackInfo ci) {
		SurveyorClientEvents.INITIALIZING_WORLD = true;
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"))
	void saveSummaryOnDisconnect(Screen screen, CallbackInfo ci) {
		MinecraftClient self = (MinecraftClient) (Object) this;
		if (self.world != null && WorldSummary.of(self.world).isClient()) {
			WorldSummary.of(self.world).save(self.world, SurveyorClient.getWorldSavePath(self.world), false);
		}
	}
}
