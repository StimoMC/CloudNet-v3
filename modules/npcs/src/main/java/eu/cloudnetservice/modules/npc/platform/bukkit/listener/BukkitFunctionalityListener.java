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

package eu.cloudnetservice.modules.npc.platform.bukkit.listener;

import com.github.juliarn.npclib.api.event.AttackNpcEvent;
import com.github.juliarn.npclib.api.event.InteractNpcEvent;
import com.github.juliarn.npclib.api.event.ShowNpcEvent;
import com.github.juliarn.npclib.api.protocol.enums.EntityStatus;
import com.github.juliarn.npclib.api.protocol.enums.ItemSlot;
import com.github.juliarn.npclib.api.protocol.meta.EntityMetadataFactory;
import com.github.juliarn.npclib.ext.labymod.LabyModExtension;
import eu.cloudnetservice.modules.npc.NPC;
import eu.cloudnetservice.modules.npc.platform.bukkit.BukkitPlatformNPCManagement;
import eu.cloudnetservice.modules.npc.platform.bukkit.entity.NPCBukkitPlatformSelector;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public final class BukkitFunctionalityListener implements Listener {

  private static final ItemSlot[] ITEM_SLOTS = ItemSlot.values();

  private final BukkitPlatformNPCManagement management;
  private final Plugin plugin;

  public BukkitFunctionalityListener(@NonNull BukkitPlatformNPCManagement management, @NonNull Plugin plugin) {
    this.management = management;
    this.plugin = plugin;

    var bus = management.npcPlatform().eventBus();
    bus.subscribe(AttackNpcEvent.class, this::handleNpcAttack);
    bus.subscribe(InteractNpcEvent.class, this::handleNpcInteract);
    bus.subscribe(ShowNpcEvent.Post.class, this::handleNpcShow);
  }

  public void handleNpcShow(@NonNull ShowNpcEvent.Post event) {
    var packetFactory = event.npc().platform().packetFactory();
    packetFactory
      .createEntityMetaPacket(true, EntityMetadataFactory.skinLayerMetaFactory())
      .scheduleForTracked(event.npc());
    event.npc().flagValue(NPCBukkitPlatformSelector.SELECTOR_ENTITY).ifPresent(selectorEntity -> {
      packetFactory.createEntityMetaPacket(
        this.collectEntityStatus(selectorEntity.npc()),
        EntityMetadataFactory.entityStatusMetaFactory()
      ).scheduleForTracked(event.npc());

      var entries = selectorEntity.npc().items().entrySet();
      for (var entry : entries) {
        if (entry.getKey() >= 0 && entry.getKey() <= 5) {
          var item = new ItemStack(Material.matchMaterial(entry.getValue()));
          packetFactory.createEquipmentPacket(ITEM_SLOTS[entry.getKey()], item).scheduleForTracked(event.npc());
        }
      }
    });
  }

  public void handleNpcAttack(@NonNull AttackNpcEvent event) {
    Bukkit.getScheduler().runTask(
      this.plugin,
      () -> this.handleClick(event.player(), null, event.npc().entityId(), true));
  }

  public void handleNpcInteract(@NonNull InteractNpcEvent event) {
    Bukkit.getScheduler().runTask(
      this.plugin,
      () -> this.handleClick(event.player(), null, event.npc().entityId(), false));
  }

  @EventHandler
  public void handle(@NonNull PlayerInteractEntityEvent event) {
    this.handleClick(event.getPlayer(), event, event.getRightClicked().getEntityId(), false);
  }

  @EventHandler(ignoreCancelled = true)
  public void handle(@NonNull EntityDamageByEntityEvent event) {
    if (event.getDamager() instanceof Player damager) {
      this.handleClick(damager, event, event.getEntity().getEntityId(), true);
    }
  }

  @EventHandler
  public void handle(@NonNull InventoryClickEvent event) {
    var item = event.getCurrentItem();
    var inv = event.getClickedInventory();
    var clicker = event.getWhoClicked();
    // check if we can handle the event
    if (item != null && item.hasItemMeta() && inv != null && inv.getHolder() == null && clicker instanceof Player) {
      this.management.trackedEntities().values().stream()
        .filter(npc -> npc.selectorInventory().equals(inv))
        .findFirst()
        .ifPresent(npc -> {
          event.setCancelled(true);
          npc.handleInventoryInteract(inv, (Player) clicker, item);
        });
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void handle(@NonNull PlayerJoinEvent event) {
    var player = event.getPlayer();

    // create a new scoreboard for the player if the player uses the main scoreboard
    var manager = player.getServer().getScoreboardManager();
    if (manager != null && player.getScoreboard().equals(manager.getMainScoreboard())) {
      player.setScoreboard(manager.getNewScoreboard());
    }

    // we have to register each entity to the players scoreboard
    for (var entity : this.management.trackedEntities().values()) {
      if (entity.spawned()) {
        entity.registerScoreboardTeam(player.getScoreboard());
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void playOnJoinEmoteIds(@NonNull PlayerJoinEvent event) {
    var entry = this.management.applicableNPCConfigurationEntry();
    if (entry != null) {
      var onJoinEmoteIds = entry.emoteConfiguration().onJoinEmoteIds();
      var selectedNpcId = this.management.randomEmoteId(entry.emoteConfiguration(), onJoinEmoteIds);
      // check if an emote id could be selected
      if (selectedNpcId >= -1) {
        // play the emote to all npcs
        for (var npc : this.management.npcPlatform().npcTracker().trackedNpcs()) {
          // verify that the player *could* see the emote
          if (npc.position().worldId().equals(event.getPlayer().getWorld().getName())) {
            // check if the emote id is fixed
            if (selectedNpcId != -1) {
              LabyModExtension
                .createEmotePacket(this.management.npcPlatform().packetFactory())
                .schedule(event.getPlayer(), npc);
            } else {
              var randomEmote = onJoinEmoteIds[ThreadLocalRandom.current().nextInt(0, onJoinEmoteIds.length)];
              LabyModExtension
                .createEmotePacket(this.management.npcPlatform().packetFactory(), randomEmote)
                .schedule(event.getPlayer(), npc);
            }
          }
        }
      }
    }
  }

  private @NonNull Collection<EntityStatus> collectEntityStatus(@NonNull NPC npc) {
    Collection<EntityStatus> status = new HashSet<>();
    if (npc.glowing()) {
      status.add(EntityStatus.GLOWING);
    }

    if (npc.flyingWithElytra()) {
      status.add(EntityStatus.FLYING_WITH_ELYTRA);
    }

    if (npc.burning()) {
      status.add(EntityStatus.ON_FIRE);
    }

    return status;
  }

  private void handleClick(@NonNull Player player, @Nullable Cancellable cancellable, int entityId, boolean left) {
    this.management.trackedEntities().values().stream()
      .filter(npc -> npc.entityId() == entityId)
      .findFirst()
      .ifPresent(entity -> {
        // cancel the event if needed
        if (cancellable != null) {
          cancellable.setCancelled(true);
        }
        // handle click
        if (left) {
          entity.handleLeftClickAction(player);
        } else {
          entity.handleRightClickAction(player);
        }
      });
  }
}
