package io.github.airi.clientmod.mixin.client;

import java.util.ArrayList;
import java.util.List;

import io.github.airi.clientmod.AiriUserClientModClient;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DebugHud.class)
public abstract class DebugHudMixin {
	@Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
	private void airi$appendObservationPanel(CallbackInfoReturnable<List<String>> cir) {
		List<String> baseLines = cir.getReturnValue();
		List<String> mergedLines = new ArrayList<>(baseLines.size() + 18);
		mergedLines.addAll(baseLines);
		mergedLines.add("");
		mergedLines.addAll(AiriUserClientModClient.getDebugStore().buildPanelLines());
		mergedLines.add("");
		mergedLines.addAll(AiriUserClientModClient.getTransportStatusStore().buildPanelLines());
		cir.setReturnValue(mergedLines);
	}
}
