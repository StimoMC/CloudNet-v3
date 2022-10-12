/*
 * Copyright 2019-2022 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.cloudnetservice.modules.bridge.platform.fabric.mixin.forwarding;

import eu.cloudnetservice.modules.bridge.platform.fabric.FabricBridgeManagement;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.dedicated.DedicatedServer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.SERVER)
@Mixin(DedicatedServer.class)
public final class DedicatedServerMixin {

  @Final
  @Shadow
  static Logger LOGGER;

  @Inject(
    method = "initServer",
    at = @At(
      remap = false,
      value = "INVOKE_STRING",
      shift = At.Shift.AFTER,
      target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;)V",
      args = "ldc=To change this, set \"online-mode\" to \"true\" in the server.properties file."
    )
  )
  private void initServer(CallbackInfoReturnable<Boolean> ci) {
    if (!FabricBridgeManagement.DISABLE_CLOUDNET_FORWARDING) {
      LOGGER.warn("##################");
      LOGGER.warn("CloudNet will handle ip forwarding in BungeeCord format!");
      LOGGER.warn("Unless the access to your server is properly restricted, it opens up the ability for hackers to "
        + "connect with any username they choose.");
      LOGGER.warn("Please see https://www.spigotmc.org/wiki/firewall-guide/ for further information.");
      LOGGER.warn("##################");
    }
  }
}
