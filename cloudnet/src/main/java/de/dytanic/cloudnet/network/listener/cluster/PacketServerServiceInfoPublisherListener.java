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

package de.dytanic.cloudnet.network.listener.cluster;

import de.dytanic.cloudnet.CloudNet;
import de.dytanic.cloudnet.common.language.LanguageManager;
import de.dytanic.cloudnet.common.log.LogManager;
import de.dytanic.cloudnet.common.log.Logger;
import de.dytanic.cloudnet.driver.network.INetworkChannel;
import de.dytanic.cloudnet.driver.network.def.packet.PacketClientServerServiceInfoPublisher;
import de.dytanic.cloudnet.driver.network.protocol.IPacket;
import de.dytanic.cloudnet.driver.network.protocol.IPacketListener;
import de.dytanic.cloudnet.driver.service.ServiceInfoSnapshot;

//TODO: replace with channel message
public final class PacketServerServiceInfoPublisherListener implements IPacketListener {

  private static final Logger LOGGER = LogManager.getLogger(PacketServerServiceInfoPublisherListener.class);

  @Override
  public void handle(INetworkChannel channel, IPacket packet) {
    packet.getBuffer().markReaderIndex();

    ServiceInfoSnapshot serviceInfoSnapshot = packet.getBuffer().readObject(ServiceInfoSnapshot.class);
    PacketClientServerServiceInfoPublisher.PublisherType publisherType =
      packet.getBuffer().readEnumConstant(PacketClientServerServiceInfoPublisher.PublisherType.class);

    if (CloudNet.getInstance().getCloudServiceManager().handleServiceUpdate(publisherType, serviceInfoSnapshot)) {
      this.publishMessageIfNecessary(publisherType, serviceInfoSnapshot);

      packet.getBuffer().resetReaderIndex();
      CloudNet.getInstance().sendAllServices(packet);
    }
  }

  private void publishMessageIfNecessary(PacketClientServerServiceInfoPublisher.PublisherType type,
    ServiceInfoSnapshot snapshot) {
    switch (type) {
      case STARTED:
        LOGGER.info(LanguageManager.getMessage("cloud-service-pre-start-message-different-node")
          .replace("%task%", snapshot.getServiceId().getTaskName())
          .replace("%serviceId%", String.valueOf(snapshot.getServiceId().getTaskServiceId()))
          .replace("%id%", snapshot.getServiceId().getUniqueId().toString())
          .replace("%node%", snapshot.getServiceId().getNodeUniqueId()));
        break;
      case STOPPED:
        LOGGER.info(LanguageManager.getMessage("cloud-service-pre-stop-message-different-node")
          .replace("%task%", snapshot.getServiceId().getTaskName())
          .replace("%serviceId%", String.valueOf(snapshot.getServiceId().getTaskServiceId()))
          .replace("%id%", snapshot.getServiceId().getUniqueId().toString())
          .replace("%node%", snapshot.getServiceId().getNodeUniqueId()));
        break;
      default:
        break;
    }
  }
}
