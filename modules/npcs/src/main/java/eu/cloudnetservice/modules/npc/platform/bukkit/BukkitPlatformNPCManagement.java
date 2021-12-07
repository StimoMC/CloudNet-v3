/*
 * Copyright 2019-2021 CloudNetService team & contributors
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

package eu.cloudnetservice.modules.npc.platform.bukkit;

import com.github.juliarn.npc.NPCPool;
import com.github.juliarn.npc.modifier.LabyModModifier.LabyModAction;
import com.google.common.base.Verify;
import de.dytanic.cloudnet.driver.service.ServiceEnvironmentType;
import de.dytanic.cloudnet.driver.service.ServiceInfoSnapshot;
import de.dytanic.cloudnet.driver.service.ServiceLifeCycle;
import de.dytanic.cloudnet.ext.bridge.WorldPosition;
import eu.cloudnetservice.modules.npc.NPC;
import eu.cloudnetservice.modules.npc.NPC.NPCType;
import eu.cloudnetservice.modules.npc.configuration.NPCConfiguration;
import eu.cloudnetservice.modules.npc.configuration.NPCConfigurationEntry;
import eu.cloudnetservice.modules.npc.platform.PlatformNPCManagement;
import eu.cloudnetservice.modules.npc.platform.PlatformSelectorEntity;
import eu.cloudnetservice.modules.npc.platform.bukkit.entity.EntityBukkitPlatformSelectorEntity;
import eu.cloudnetservice.modules.npc.platform.bukkit.entity.NPCBukkitPlatformSelector;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class BukkitPlatformNPCManagement extends PlatformNPCManagement<Location, Player, ItemStack, Inventory> {

  protected final Plugin plugin;
  protected final NPCPool npcPool;
  protected final Scoreboard scoreboard;
  protected final BukkitTask knockBackTask;

  protected volatile BukkitTask npcEmoteTask;

  public BukkitPlatformNPCManagement(@NotNull Plugin plugin) {
    this.plugin = plugin;
    this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

    // npc pool init
    NPCConfigurationEntry entry = this.getApplicableNPCConfigurationEntry();
    if (entry != null) {
      this.npcPool = NPCPool.builder(plugin)
        .actionDistance(entry.getNpcPoolOptions().getActionDistance())
        .spawnDistance(entry.getNpcPoolOptions().getSpawnDistance())
        .tabListRemoveTicks(entry.getNpcPoolOptions().getTabListRemoveTicks())
        .build();
    } else {
      this.npcPool = NPCPool.builder(plugin).build();
    }

    // start the emote player
    this.startEmoteTask(false);
    // start the knock back task
    this.knockBackTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
      NPCConfigurationEntry configEntry = this.getApplicableNPCConfigurationEntry();
      if (configEntry != null) {
        // check if knock back is enabled
        double distance = configEntry.getKnockbackDistance();
        double strength = configEntry.getKnockbackStrength();
        if (distance > 0 && strength > 0) {
          // select the knockback emote id now (sometimes we need to play them sync for all npcs)
          int[] labyModEmotes = configEntry.getEmoteConfiguration().getOnKnockbackEmoteIds();
          int emoteId = this.getRandomEmoteId(configEntry.getEmoteConfiguration(), labyModEmotes);
          //
          for (PlatformSelectorEntity<Location, Player, ItemStack, Inventory> value : this.trackedEntities.values()) {
            if (value.isSpawned()) {
              // select all nearby entities of each spawned mob
              Collection<Entity> nearbyEntities = value.getLocation().getWorld().getNearbyEntities(
                value.getLocation(),
                distance,
                distance,
                distance);
              // loop over all entities and knock them back
              if (!nearbyEntities.isEmpty()) {
                for (Entity entity : nearbyEntities) {
                  // check if the entity is a player
                  if (entity instanceof Player && !entity.hasPermission("cloudnet.npcs.knockback.bypass")) {
                    Player player = (Player) entity;
                    // apply the strength to the curren vector
                    Vector vector = player.getLocation().toVector().subtract(value.getLocation().toVector())
                      .normalize()
                      .multiply(strength)
                      .setY(0.2);
                    if (NumberConversions.isFinite(vector.getX()) && NumberConversions.isFinite(vector.getZ())) {
                      // apply the velocity
                      player.setVelocity(vector);
                      // check if we should send a labymod emote
                      if (value instanceof NPCBukkitPlatformSelector) {
                        if (emoteId == -1) {
                          int emote = labyModEmotes[ThreadLocalRandom.current().nextInt(0, labyModEmotes.length)];
                          ((NPCBukkitPlatformSelector) value).getHandleNpc()
                            .labymod()
                            .queue(LabyModAction.EMOTE, emote)
                            .send(player);
                        } else {
                          // use the selected emote
                          ((NPCBukkitPlatformSelector) value).getHandleNpc()
                            .labymod()
                            .queue(LabyModAction.EMOTE, emoteId)
                            .send(player);
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }, 20, 5);
  }

  @NotNull
  @Override
  protected PlatformSelectorEntity<Location, Player, ItemStack, Inventory> createSelectorEntity(@NotNull NPC base) {
    return base.getNpcType() == NPCType.ENTITY
      ? new EntityBukkitPlatformSelectorEntity(this, this.plugin, base)
      : new NPCBukkitPlatformSelector(this, this.plugin, base, this.npcPool);
  }

  @Override
  public @NotNull WorldPosition toWorldPosition(@NotNull Location location, @NotNull String group) {
    Verify.verifyNotNull(location.getWorld(), "world unloaded");
    return new WorldPosition(
      location.getX(),
      location.getY(),
      location.getZ(),
      location.getYaw(),
      location.getPitch(),
      location.getWorld().getName(),
      group);
  }

  @Override
  public @NotNull Location toPlatformLocation(@NotNull WorldPosition position) {
    World world = Bukkit.getWorld(position.getWorld());
    return new Location(
      world,
      position.getX(),
      position.getY(),
      position.getZ(),
      (float) position.getYaw(),
      (float) position.getPitch());
  }

  @Override
  protected boolean shouldTrack(@NotNull ServiceInfoSnapshot service) {
    return service.getLifeCycle() == ServiceLifeCycle.RUNNING
      && ServiceEnvironmentType.JAVA_SERVER.get(service.getServiceId().getEnvironment().getProperties());
  }

  @Override
  public void handleInternalNPCConfigUpdate(@NotNull NPCConfiguration configuration) {
    super.handleInternalNPCConfigUpdate(configuration);
    // re-schedule the emote task if it's not yet running
    this.startEmoteTask(false);
  }

  public @NotNull Scoreboard getScoreboard() {
    return this.scoreboard;
  }

  public @NotNull NPCPool getNpcPool() {
    return this.npcPool;
  }

  protected void startEmoteTask(boolean force) {
    // only start the task if not yet running
    if (this.npcEmoteTask == null || force) {
      NPCConfigurationEntry ent = this.getApplicableNPCConfigurationEntry();
      if (ent != null && ent.getEmoteConfiguration().getMinEmoteDelayTicks() > 0) {
        // get the delay for the next npc emote play
        long delay;
        if (ent.getEmoteConfiguration().getMaxEmoteDelayTicks() > ent.getEmoteConfiguration().getMinEmoteDelayTicks()) {
          delay = ThreadLocalRandom.current().nextLong(
            ent.getEmoteConfiguration().getMinEmoteDelayTicks(),
            ent.getEmoteConfiguration().getMaxEmoteDelayTicks());
        } else {
          delay = ent.getEmoteConfiguration().getMinEmoteDelayTicks();
        }
        // run the task
        this.npcEmoteTask = Bukkit.getScheduler().runTaskLaterAsynchronously(this.plugin, () -> {
          // select an emote to play
          int[] emotes = ent.getEmoteConfiguration().getEmoteIds();
          int emoteId = this.getRandomEmoteId(ent.getEmoteConfiguration(), emotes);
          // check if we can select an emote
          if (emoteId >= -1) {
            // play the emote on each npc
            for (com.github.juliarn.npc.NPC npc : this.npcPool.getNPCs()) {
              if (emoteId == -1) {
                npc.labymod()
                  .queue(LabyModAction.EMOTE, emotes[ThreadLocalRandom.current().nextInt(0, emotes.length)])
                  .send();
              } else {
                npc.labymod().queue(LabyModAction.EMOTE, emoteId).send();
              }
            }
          }
          // re-schedule
          this.startEmoteTask(true);
        }, delay);
      } else {
        this.npcEmoteTask = null;
      }
    }
  }
}