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

package eu.cloudnetservice.modules.signs.platform.bukkit.functionality;

import eu.cloudnetservice.modules.signs.platform.PlatformSignManagement;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class SignInteractListener implements Listener {

  protected final PlatformSignManagement<Player, Location, ?> signManagement;

  public SignInteractListener(@NonNull PlatformSignManagement<Player, Location, ?> signManagement) {
    this.signManagement = signManagement;
  }

  @EventHandler
  public void handle(PlayerInteractEvent event) {
    if (event.getAction() == Action.RIGHT_CLICK_BLOCK
      && event.getClickedBlock() != null
      && event.getClickedBlock().getState() instanceof org.bukkit.block.Sign) {
      // get the sign at the given position
      var pos = this.signManagement.convertPosition(event.getClickedBlock().getLocation());
      var sign = this.signManagement.platformSignAt(pos);

      // execute the interact action if the sign is registered
      if (sign != null) {
        event.setCancelled(true);
        sign.handleInteract(event.getPlayer().getUniqueId(), event.getPlayer());
      }
    }
  }
}
