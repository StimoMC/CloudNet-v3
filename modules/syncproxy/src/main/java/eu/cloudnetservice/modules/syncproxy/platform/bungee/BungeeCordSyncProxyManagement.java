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

package eu.cloudnetservice.modules.syncproxy.platform.bungee;

import eu.cloudnetservice.driver.registry.ServiceRegistry;
import eu.cloudnetservice.modules.bridge.platform.bungeecord.BungeeCordHelper;
import eu.cloudnetservice.modules.syncproxy.platform.PlatformSyncProxyManagement;
import java.util.Collection;
import java.util.UUID;
import lombok.NonNull;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public final class BungeeCordSyncProxyManagement extends PlatformSyncProxyManagement<ProxiedPlayer> {

  private final Plugin plugin;

  public BungeeCordSyncProxyManagement(@NonNull Plugin plugin) {
    this.plugin = plugin;
    this.init();
  }

  @Override
  public void registerService(@NonNull ServiceRegistry registry) {
    registry.registerProvider(PlatformSyncProxyManagement.class, "BungeeCordSyncProxyManagement", this);
  }

  @Override
  public void unregisterService(@NonNull ServiceRegistry registry) {
    registry.unregisterProvider(PlatformSyncProxyManagement.class, "BungeeCordSyncProxyManagement");
  }

  @Override
  public @NonNull Collection<ProxiedPlayer> onlinePlayers() {
    return this.plugin.getProxy().getPlayers();
  }

  @Override
  public @NonNull String playerName(@NonNull ProxiedPlayer player) {
    return player.getName();
  }

  @Override
  public @NonNull UUID playerUniqueId(@NonNull ProxiedPlayer player) {
    return player.getUniqueId();
  }

  @Override
  public void playerTabList(@NonNull ProxiedPlayer player, @Nullable String header, @Nullable String footer) {
    player.setTabHeader(
      header != null ? BungeeCordHelper.translateToComponent(this.replaceTabPlaceholder(header, player)) : null,
      footer != null ? BungeeCordHelper.translateToComponent(this.replaceTabPlaceholder(footer, player)) : null);
  }

  @Override
  public void disconnectPlayer(@NonNull ProxiedPlayer player, @NonNull String message) {
    player.disconnect(BungeeCordHelper.translateToComponent(message));
  }

  @Override
  public void messagePlayer(@NonNull ProxiedPlayer player, @Nullable String message) {
    if (message != null) {
      player.sendMessage(BungeeCordHelper.translateToComponent(message));
    }
  }

  @Override
  public boolean checkPlayerPermission(@NonNull ProxiedPlayer player, @NonNull String permission) {
    return player.hasPermission(permission);
  }

  private @NonNull String replaceTabPlaceholder(@NonNull String input, @NonNull ProxiedPlayer player) {
    return input
      .replace("%ping%", String.valueOf(player.getPing()))
      .replace("%server%", player.getServer() == null ? "UNAVAILABLE" : player.getServer().getInfo().getName());
  }
}
