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

package eu.cloudnetservice.modules.bridge.platform.bukkit;

import eu.cloudnetservice.modules.bridge.platform.PlatformBridgeManagement;
import eu.cloudnetservice.modules.bridge.platform.helper.ServerPlatformHelper;
import eu.cloudnetservice.modules.bridge.player.NetworkPlayerServerInfo;
import eu.cloudnetservice.wrapper.Wrapper;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class BukkitPlayerManagementListener implements Listener {

  private final Plugin plugin;
  private final PlatformBridgeManagement<Player, NetworkPlayerServerInfo> management;

  public BukkitPlayerManagementListener(
    @NonNull Plugin plugin,
    @NonNull PlatformBridgeManagement<Player, NetworkPlayerServerInfo> management
  ) {
    this.plugin = plugin;
    this.management = management;
  }

  @EventHandler
  public void handle(@NonNull PlayerLoginEvent event) {
    var task = this.management.selfTask();
    // check if the current task is present
    if (task != null) {
      // check if maintenance is activated
      if (task.maintenance() && !event.getPlayer().hasPermission("cloudnet.bridge.maintenance")) {
        event.setResult(PlayerLoginEvent.Result.KICK_WHITELIST);
        this.management.configuration().handleMessage(
          BukkitUtil.playerLocale(event.getPlayer()),
          "server-join-cancel-because-maintenance",
          event::setKickMessage);
        return;
      }
      // check if a custom permission is required to join
      var permission = task.properties().getString("requiredPermission");
      if (permission != null && !event.getPlayer().hasPermission(permission)) {
        event.setResult(PlayerLoginEvent.Result.KICK_WHITELIST);
        this.management.configuration().handleMessage(
          BukkitUtil.playerLocale(event.getPlayer()),
          "server-join-cancel-because-permission",
          event::setKickMessage);
      }
    }
  }

  @EventHandler
  public void handle(@NonNull PlayerJoinEvent event) {
    ServerPlatformHelper.sendChannelMessageLoginSuccess(
      event.getPlayer().getUniqueId(),
      this.management.createPlayerInformation(event.getPlayer()));
    // update the service info in the next tick
    Bukkit.getScheduler().runTask(this.plugin, () -> Wrapper.instance().publishServiceInfoUpdate());
  }

  @EventHandler
  public void handle(@NonNull PlayerQuitEvent event) {
    ServerPlatformHelper.sendChannelMessageDisconnected(
      event.getPlayer().getUniqueId(),
      this.management.ownNetworkServiceInfo());
    // update the service info in the next tick
    Bukkit.getScheduler().runTask(this.plugin, () -> Wrapper.instance().publishServiceInfoUpdate());
  }
}
