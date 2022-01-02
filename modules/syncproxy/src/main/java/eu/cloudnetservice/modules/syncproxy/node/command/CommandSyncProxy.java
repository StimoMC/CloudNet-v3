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

package eu.cloudnetservice.modules.syncproxy.node.command;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.parsers.Parser;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import eu.cloudnetservice.cloudnet.common.Nameable;
import eu.cloudnetservice.cloudnet.common.language.I18n;
import eu.cloudnetservice.cloudnet.node.CloudNet;
import eu.cloudnetservice.cloudnet.node.command.exception.ArgumentNotAvailableException;
import eu.cloudnetservice.cloudnet.node.command.source.CommandSource;
import eu.cloudnetservice.modules.syncproxy.config.SyncProxyConfiguration;
import eu.cloudnetservice.modules.syncproxy.config.SyncProxyLoginConfiguration;
import eu.cloudnetservice.modules.syncproxy.config.SyncProxyMotd;
import eu.cloudnetservice.modules.syncproxy.config.SyncProxyTabListConfiguration;
import eu.cloudnetservice.modules.syncproxy.node.NodeSyncProxyManagement;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import lombok.NonNull;

public final class CommandSyncProxy {

  private final NodeSyncProxyManagement syncProxyManagement;

  public CommandSyncProxy(@NonNull NodeSyncProxyManagement syncProxyManagement) {
    this.syncProxyManagement = syncProxyManagement;
  }

  @Parser(suggestions = "loginConfiguration")
  public SyncProxyLoginConfiguration loginConfigurationParser(CommandContext<CommandSource> $, Queue<String> input) {
    var name = input.remove();

    return this.syncProxyManagement.configuration().loginConfigurations()
      .stream()
      .filter(login -> login.targetGroup().equals(name)).findFirst()
      .orElseThrow(
        () -> new ArgumentNotAvailableException(I18n.trans("module-syncproxy-command-create-entry-group-not-found")));
  }

  @Suggestions("loginConfiguration")
  public List<String> suggestLoginConfigurations(CommandContext<CommandSource> $, String input) {
    return this.syncProxyManagement.configuration().loginConfigurations()
      .stream()
      .map(SyncProxyLoginConfiguration::targetGroup)
      .toList();
  }

  @Parser(name = "newConfiguration", suggestions = "newConfiguration")
  public String newConfigurationParser(CommandContext<CommandSource> $, Queue<String> input) {
    var name = input.remove();
    var configuration = CloudNet.instance().groupConfigurationProvider()
      .groupConfiguration(name);
    if (configuration == null) {
      throw new ArgumentNotAvailableException(I18n.trans("command-service-base-group-not-found"));
    }

    if (this.syncProxyManagement.configuration().loginConfigurations()
      .stream()
      .anyMatch(login -> login.targetGroup().equalsIgnoreCase(name))) {
      throw new ArgumentNotAvailableException(I18n.trans("module-syncproxy-command-create-entry-group-already-exists"));
    }

    if (this.syncProxyManagement.configuration().tabListConfigurations()
      .stream()
      .anyMatch(tabList -> tabList.targetGroup().equalsIgnoreCase(name))) {
      throw new ArgumentNotAvailableException(I18n.trans("module-syncproxy-command-create-entry-group-already-exists"));
    }
    return name;
  }

  @Suggestions("newConfiguration")
  public List<String> suggestNewLoginConfigurations(CommandContext<CommandSource> $, String input) {
    return CloudNet.instance().groupConfigurationProvider().groupConfigurations()
      .stream()
      .map(Nameable::name)
      .toList();
  }

  @CommandMethod("syncproxy|sp list")
  public void listConfigurations(CommandSource source) {
    this.displayListConfiguration(source, this.syncProxyManagement.configuration());
  }

  @CommandMethod("syncproxy|sp create entry <targetGroup>")
  public void createEntry(
    CommandSource source,
    @Argument(value = "targetGroup", parserName = "newConfiguration") String name
  ) {
    var loginConfiguration = SyncProxyLoginConfiguration.createDefault(name);
    var tabListConfiguration = SyncProxyTabListConfiguration.createDefault(name);

    this.syncProxyManagement.configuration().loginConfigurations().add(loginConfiguration);
    this.syncProxyManagement.configuration().tabListConfigurations().add(tabListConfiguration);
    this.updateSyncProxyConfiguration();

    source.sendMessage(I18n.trans("module-syncproxy-command-create-entry-success"));
  }

  @CommandMethod("syncproxy|sp target <targetGroup>")
  public void listConfiguration(
    CommandSource source,
    @Argument("targetGroup") SyncProxyLoginConfiguration loginConfiguration
  ) {
    this.displayConfiguration(source, loginConfiguration);
  }

  @CommandMethod("syncproxy|sp target <targetGroup> maxPlayers <amount>")
  public void setMaxPlayers(
    CommandSource source,
    @Argument("targetGroup") SyncProxyLoginConfiguration loginConfiguration,
    @Argument("amount") int amount
  ) {
    var updatedLoginConfiguration = SyncProxyLoginConfiguration.builder(loginConfiguration)
      .maxPlayers(amount)
      .build();

    this.syncProxyManagement.configurationSilently(
      SyncProxyConfiguration.builder(this.syncProxyManagement.configuration())
        .addLoginConfiguration(updatedLoginConfiguration)
        .build());

    source.sendMessage(
      I18n.trans("module-syncproxy-command-set-maxplayers")
        .replace("%group%", loginConfiguration.targetGroup())
        .replace("%amount%", Integer.toString(amount)));
  }

  @CommandMethod("syncproxy|sp target <targetGroup> whitelist add <name>")
  public void addWhiteList(
    CommandSource source,
    @Argument("targetGroup") SyncProxyLoginConfiguration loginConfiguration,
    @Argument("name") String name
  ) {
    loginConfiguration.whitelist().add(name);
    this.updateSyncProxyConfiguration();

    source.sendMessage(I18n.trans("module-syncproxy-command-add-whitelist-entry")
      .replace("%name%", name)
      .replace("%group%", loginConfiguration.targetGroup()));
  }

  @CommandMethod("syncproxy|sp target <targetGroup> whitelist remove <name>")
  public void removeWhiteList(
    CommandSource source,
    @Argument("targetGroup") SyncProxyLoginConfiguration loginConfiguration,
    @Argument("name") String name
  ) {
    if (loginConfiguration.whitelist().remove(name)) {
      this.updateSyncProxyConfiguration();
    }

    source.sendMessage(I18n.trans("module-syncproxy-command-remove-whitelist-entry")
      .replace("%name%", name)
      .replace("%group%", loginConfiguration.targetGroup()));
  }

  @CommandMethod("syncproxy|sp target <targetGroup> maintenance <enabled>")
  public void maintenance(
    CommandSource source,
    @Argument("targetGroup") SyncProxyLoginConfiguration loginConfiguration,
    @Argument("enabled") boolean enabled
  ) {
    var updatedLoginConfiguration = SyncProxyLoginConfiguration.builder(loginConfiguration)
      .maintenance(enabled)
      .build();

    this.syncProxyManagement.configurationSilently(
      SyncProxyConfiguration.builder(this.syncProxyManagement.configuration())
        .addLoginConfiguration(updatedLoginConfiguration)
        .build());

    source.sendMessage(I18n.trans("module-syncproxy-command-set-maintenance")
      .replace("%group%", loginConfiguration.targetGroup())
      .replace("%maintenance%", Boolean.toString(enabled)));
  }

  private void updateSyncProxyConfiguration() {
    this.syncProxyManagement.configuration(this.syncProxyManagement.configuration());
  }

  private void displayListConfiguration(CommandSource source, SyncProxyConfiguration syncProxyConfiguration) {
    for (var syncProxyLoginConfiguration : syncProxyConfiguration
      .loginConfigurations()) {
      this.displayConfiguration(source, syncProxyLoginConfiguration);
    }

    for (var syncProxyTabListConfiguration : syncProxyConfiguration
      .tabListConfigurations()) {
      source.sendMessage(
        "* " + syncProxyTabListConfiguration.targetGroup(),
        "AnimationsPerSecond: " + syncProxyTabListConfiguration.animationsPerSecond(),
        " ",
        "Entries: "
      );

      var index = 1;
      for (var tabList : syncProxyTabListConfiguration.entries()) {
        source.sendMessage(
          "- " + index++,
          "Header: " + tabList.header(),
          "Footer: " + tabList.footer()
        );
      }
    }
  }

  private void displayConfiguration(CommandSource source,
    SyncProxyLoginConfiguration syncProxyLoginConfiguration) {
    source.sendMessage(
      "* " + syncProxyLoginConfiguration.targetGroup(),
      "Maintenance: " + (syncProxyLoginConfiguration.maintenance() ? "enabled" : "disabled"),
      "Max-Players: " + syncProxyLoginConfiguration.maxPlayers()
    );

    this.displayWhitelist(source, syncProxyLoginConfiguration.whitelist());

    source.sendMessage("Motds:");
    for (var syncProxyMotd : syncProxyLoginConfiguration.motds()) {
      this.displayMotd(source, syncProxyMotd);
    }

    for (var syncProxyMotd : syncProxyLoginConfiguration.maintenanceMotds()) {
      this.displayMotd(source, syncProxyMotd);
    }
  }

  private void displayMotd(CommandSource source, SyncProxyMotd syncProxyMotd) {
    source.sendMessage(
      "- Motd",
      "AutoSlot: " + syncProxyMotd.autoSlot(),
      "AutoSlot-MaxPlayerDistance: " + syncProxyMotd.autoSlotMaxPlayersDistance(),
      "Protocol-Text: " + syncProxyMotd.protocolText(),
      "First Line: " + syncProxyMotd.firstLine(),
      "Second Line: " + syncProxyMotd.secondLine(),
      "PlayerInfo: "
    );

    for (var playerInfoItem : syncProxyMotd.playerInfo()) {
      source.sendMessage("- " + playerInfoItem);
    }
  }

  private void displayWhitelist(CommandSource source, Collection<String> whitelistEntries) {
    source.sendMessage("Whitelist:");

    for (var whitelistEntry : whitelistEntries) {
      source.sendMessage("- " + whitelistEntry);
    }
  }
}