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

package eu.cloudnetservice.modules.cloudperms.minestom.listener;

import eu.cloudnetservice.modules.cloudperms.CloudPermissionsHelper;
import eu.cloudnetservice.wrapper.Wrapper;
import lombok.NonNull;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.fakeplayer.FakePlayer;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.extras.MojangAuth;

public final class MinestomCloudPermissionsPlayerListener {

  public MinestomCloudPermissionsPlayerListener() {
    var node = EventNode.type("cloudnet-cloudperms", EventFilter.PLAYER);
    MinecraftServer.getGlobalEventHandler().addChild(node
      .addListener(AsyncPlayerPreLoginEvent.class, this::handleAsyncPreLogin)
      .addListener(PlayerDisconnectEvent.class, this::handleDisconnect));
  }

  private void handleAsyncPreLogin(@NonNull AsyncPlayerPreLoginEvent event) {
    var player = event.getPlayer();
    // ignore fake players
    if (player instanceof FakePlayer) {
      return;
    }

    if (player.isOnline()) {
      // setup the permission user if the player wasn't kicked during the login process
      CloudPermissionsHelper.initPermissionUser(
        Wrapper.instance().permissionManagement(),
        player.getUuid(),
        player.getUsername(),
        message -> player.kick(LegacyComponentSerializer.legacySection().deserialize(message)),
        MojangAuth.isEnabled());
    }
  }

  private void handleDisconnect(@NonNull PlayerDisconnectEvent event) {
    // ignore fake players
    if (event.getPlayer() instanceof FakePlayer) {
      return;
    }

    // remove the player from the cache
    CloudPermissionsHelper.handlePlayerQuit(Wrapper.instance().permissionManagement(), event.getPlayer().getUuid());
  }
}
