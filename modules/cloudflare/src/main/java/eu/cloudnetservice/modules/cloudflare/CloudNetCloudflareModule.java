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

package eu.cloudnetservice.modules.cloudflare;

import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import eu.cloudnetservice.common.StringUtil;
import eu.cloudnetservice.common.collection.Pair;
import eu.cloudnetservice.common.document.gson.JsonDocument;
import eu.cloudnetservice.common.language.I18n;
import eu.cloudnetservice.common.log.LogManager;
import eu.cloudnetservice.common.log.Logger;
import eu.cloudnetservice.driver.module.ModuleLifeCycle;
import eu.cloudnetservice.driver.module.ModuleTask;
import eu.cloudnetservice.driver.module.driver.DriverModule;
import eu.cloudnetservice.modules.cloudflare.cloudflare.CloudFlareRecordManager;
import eu.cloudnetservice.modules.cloudflare.cloudflare.DnsRecordDetail;
import eu.cloudnetservice.modules.cloudflare.config.CloudflareConfiguration;
import eu.cloudnetservice.modules.cloudflare.config.CloudflareConfigurationEntry;
import eu.cloudnetservice.modules.cloudflare.config.CloudflareGroupConfiguration;
import eu.cloudnetservice.modules.cloudflare.dns.DnsRecord;
import eu.cloudnetservice.modules.cloudflare.dns.DnsType;
import eu.cloudnetservice.modules.cloudflare.listener.CloudflareServiceStateListener;
import eu.cloudnetservice.node.Node;
import eu.cloudnetservice.node.util.NetworkUtil;
import java.net.Inet6Address;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

public final class CloudNetCloudflareModule extends DriverModule {

  private static final UUID NODE_RECORDS_ID = UUID.randomUUID();
  private static final Logger LOGGER = LogManager.logger(CloudNetCloudflareModule.class);

  private final CloudFlareRecordManager recordManager = new CloudFlareRecordManager();
  private CloudflareConfiguration cloudflareConfiguration;

  @ModuleTask(event = ModuleLifeCycle.LOADED)
  public void convertConfiguration() {
    var config = this.readConfig().get("config");
    if (config != null) {
      this.writeConfig(JsonDocument.newDocument(config));
    }
  }

  @ModuleTask(order = 127, event = ModuleLifeCycle.STARTED)
  public void loadConfiguration() {
    var config = this.readConfig(
      CloudflareConfiguration.class,
      () -> new CloudflareConfiguration(Lists.newArrayList(new CloudflareConfigurationEntry(
        false,
        CloudflareConfigurationEntry.AuthenticationMethod.GLOBAL_KEY,
        StringUtil.generateRandomString(7),
        NetworkUtil.localAddress(),
        "user@example.com",
        "api_token_string",
        "zoneId",
        "example.com",
        Lists.newArrayList(new CloudflareGroupConfiguration("Proxy", "@", 1, 1))))));
    this.updateConfiguration(config);
  }

  @ModuleTask(order = 126, event = ModuleLifeCycle.STARTED)
  public void createNodeRecordsWhenNeeded() {
    for (var entry : this.cloudflareConfiguration.entries()) {
      if (entry.enabled()) {
        // collect the host information, continue if invalid
        var hostInformation = this.parseAddressOfEntry(entry);
        if (hostInformation == null) {
          continue;
        }

        // list all records of the entry and check if we need to create a dns record
        var expectedName = String.format("%s.%s", entry.entryName(), entry.domainName());
        this.recordManager.listRecords(entry).thenAccept(records -> {
          var existingRecord = records.stream()
            .filter(record -> record.type().equals(hostInformation.first().name())
              && record.name().equalsIgnoreCase(expectedName) && record.content().equals(hostInformation.second()))
            .findFirst()
            .orElse(null);

          // check if the record exists or create a new record
          if (existingRecord == null) {
            this.recordManager.createRecord(NODE_RECORDS_ID, entry, new DnsRecord(
              hostInformation.first(),
              expectedName,
              hostInformation.second(),
              1,
              false,
              JsonDocument.emptyDocument()));
          } else {
            // mark the record as created
            LOGGER.fine("Skipping creation of record for %s because the record %s exists", null, entry, existingRecord);
            this.recordManager.trackedRecords().put(
              NODE_RECORDS_ID,
              new DnsRecordDetail(existingRecord.id(), existingRecord, entry));
          }
        });
      }
    }
  }

  @ModuleTask(order = 125, event = ModuleLifeCycle.STARTED)
  public void finishStartup() {
    this.registerListener(new CloudflareServiceStateListener(this));
  }

  @ModuleTask(event = ModuleLifeCycle.RELOADING)
  public void handleReload() {
    // store the old entries for later comparison
    var oldEntries = this.cloudflareConfiguration.entries();

    // re-load the configuration and get the new entries from it
    this.loadConfiguration();
    var newEntries = this.cloudflareConfiguration.entries();

    // get all entries which are tracked & the entries created for all nodes
    var trackedEntries = this.recordManager.trackedRecords();
    var nodeRecordEntries = trackedEntries.get(NODE_RECORDS_ID);

    // find all newly added records - while we could also remove all old records, this could result in unexpected issues
    // like proxy SRV records pointing to a deleted record
    var stillExistingEntries = newEntries.stream()
      .filter(CloudflareConfigurationEntry::enabled)
      .flatMap(entry -> oldEntries.stream()
        .filter(old -> CloudflareConfigurationEntry.mightEqual(old, entry))
        .map(oldEntry -> new Pair<>(entry, oldEntry)))
      .toList();

    // find all entries which are present in the old and new configuration file
    // without comparing the group-based record creation configurations
    stillExistingEntries.stream()
      .filter(Predicate.not(pair -> {
        var newEntry = pair.first();
        var oldEntry = pair.second();

        // compare the entries if they might equal
        return Objects.equals(newEntry.authenticationMethod(), oldEntry.authenticationMethod())
          && Objects.equals(newEntry.hostAddress(), oldEntry.hostAddress())
          && Objects.equals(newEntry.email(), oldEntry.email())
          && Objects.equals(newEntry.apiToken(), oldEntry.apiToken())
          && Objects.equals(newEntry.zoneId(), oldEntry.zoneId())
          && Objects.equals(newEntry.domainName(), oldEntry.domainName());
      }))
      .map(Pair::first)
      .map(newConfigEntry -> nodeRecordEntries.stream()
        .filter(entry -> CloudflareConfigurationEntry.mightEqual(entry.configurationEntry(), newConfigEntry))
        .findFirst()
        .map(detail -> new Pair<>(newConfigEntry, detail))
        .orElseGet(() -> new Pair<>(newConfigEntry, null)))
      .forEach(pair -> {
        // try to build a dns record for the entry
        var record = this.buildRecord(pair.first());
        if (record == null) {
          return;
        }

        // patch or create a new record based on the information we've collected
        CompletableFuture<DnsRecordDetail> future;
        if (pair.second() == null) {
          // no previous record found, create a new one
          future = this.recordManager.createRecord(NODE_RECORDS_ID, pair.first(), record);
        } else {
          // previous record found, remove the tracked one & patch the existing one
          trackedEntries.remove(NODE_RECORDS_ID, pair.second());
          future = this.recordManager.patchRecord(NODE_RECORDS_ID, pair.second(), record);
        }

        // add a listener to the future to print out a nice message
        future.thenAccept(detail -> {
          // check if the record was created
          if (detail != null) {
            LOGGER.info(I18n.trans(
              "module-cloudflare-create-dns-record-for-service",
              pair.first().domainName(),
              Node.instance().config().identity().uniqueId(),
              detail.id()));
          }
        });
      });

    // filter out all newly added entries and create records for them
    var addedEntries = newEntries.stream()
      .filter(entry -> !oldEntries.contains(entry) && stillExistingEntries.stream()
        .noneMatch(pair -> Objects.equals(pair.second().entryName(), entry.entryName())))
      .toList();
    this.createRecordsForEntries(addedEntries);
  }

  @ModuleTask(order = 64, event = ModuleLifeCycle.STOPPED)
  public void removeAllServiceRecords() {
    var deletionFutures = this.recordManager.trackedRecords().entries().stream()
      .filter(trackedRecordEntry -> {
        // check if the entry is a record for our node
        if (trackedRecordEntry.getKey().equals(NODE_RECORDS_ID)) {
          // check if a configuration entry exists for the record
          var detail = trackedRecordEntry.getValue();
          return !this.cloudflareConfiguration.entries().contains(detail.configurationEntry());
        }

        // always delete service records
        return true;
      })
      .map(Map.Entry::getValue)
      .map(this.recordManager::deleteRecord)
      .toArray(CompletableFuture[]::new);

    // create one final future from all deletion futures and wait
    // for them to complete or time them out after 15 seconds
    CompletableFuture.allOf(deletionFutures)
      .orTimeout(15, TimeUnit.SECONDS)
      .exceptionally(ex -> null)
      .join();
  }

  public @NonNull CloudFlareRecordManager recordManager() {
    return this.recordManager;
  }

  public @NonNull CloudflareConfiguration configuration() {
    return this.cloudflareConfiguration;
  }

  public void updateConfiguration(@NonNull CloudflareConfiguration cloudflareConfiguration) {
    this.cloudflareConfiguration = cloudflareConfiguration;
    this.writeConfig(JsonDocument.newDocument(cloudflareConfiguration));
  }

  private void createRecordsForEntries(@NonNull Collection<CloudflareConfigurationEntry> entries) {
    var nodeConfig = Node.instance().config();
    for (var entry : entries) {
      if (entry.enabled()) {
        // build a record which we can create if possible
        var record = this.buildRecord(entry);
        if (record != null) {
          // create a new record for the entry
          this.recordManager.createRecord(NODE_RECORDS_ID, entry, record).thenAccept(detail -> {
            // check if the record was created
            if (detail != null) {
              LOGGER.info(I18n.trans(
                "module-cloudflare-create-dns-record-for-service",
                entry.domainName(),
                nodeConfig.identity().uniqueId(),
                detail.id()));
            }
          });
        }
      }
    }
  }

  private @Nullable DnsRecord buildRecord(@NonNull CloudflareConfigurationEntry entry) {
    // parse the address info if possible
    var address = this.parseAddressOfEntry(entry);
    if (address == null) {
      return null;
    }

    // create a new record for the entry
    return new DnsRecord(
      address.first(),
      String.format("%s.%s", entry.entryName(), entry.domainName()),
      address.second(),
      1,
      false,
      JsonDocument.emptyDocument());
  }

  private @Nullable Pair<DnsType, String> parseAddressOfEntry(@NonNull CloudflareConfigurationEntry entry) {
    try {
      // parse the host address and from that the dns type from the configuration
      var address = InetAddresses.forString(entry.hostAddress());
      var dnsType = address instanceof Inet6Address ? DnsType.AAAA : DnsType.A;

      // return the parsed values
      return new Pair<>(dnsType, address.getHostAddress());
    } catch (IllegalArgumentException exception) {
      LOGGER.severe("Host address %s is invalid", exception, entry.hostAddress());
      return null;
    }
  }
}
