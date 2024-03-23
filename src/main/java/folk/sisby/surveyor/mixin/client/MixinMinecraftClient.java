package folk.sisby.surveyor.mixin.client;

import folk.sisby.surveyor.SurveyorWorld;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.client.SurveyorClientEvents;
import folk.sisby.surveyor.packet.C2SKnownLandmarksPacket;
import folk.sisby.surveyor.packet.C2SKnownStructuresPacket;
import folk.sisby.surveyor.packet.C2SKnownTerrainPacket;
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
        if (self.world != null && ((SurveyorWorld) self.world).surveyor$getWorldSummary().isClient()) {
            ((SurveyorWorld) self.world).surveyor$getWorldSummary().save(self.world, SurveyorClient.getWorldSavePath(self.world), false);
        }
    }

    @Inject(method = "joinWorld", at = @At("HEAD"))
    void loadOnJoinWorld(ClientWorld newWorld, CallbackInfo ci) {
        if (((SurveyorWorld) newWorld).surveyor$getWorldSummary().isClient()) {
            WorldSummary summary = ((SurveyorWorld) newWorld).surveyor$getWorldSummary();
            new C2SKnownTerrainPacket(summary.terrain().bitSet(null)).send();
            new C2SKnownStructuresPacket(summary.structures().keySet(null)).send();
            new C2SKnownLandmarksPacket(summary.landmarks().keySet(null).asMap()).send();
            SurveyorClientEvents.Invoke.clientWorldLoad(newWorld, summary);
        }
        SurveyorClientEvents.INITIALIZING_WORLD = true;
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"))
    void saveSummaryOnDisconnect(Screen screen, CallbackInfo ci) {
        MinecraftClient self = (MinecraftClient) (Object) this;
        if (self.world != null && ((SurveyorWorld) self.world).surveyor$getWorldSummary().isClient()) {
            ((SurveyorWorld) self.world).surveyor$getWorldSummary().save(self.world, SurveyorClient.getWorldSavePath(self.world), false);
        }
    }
}
