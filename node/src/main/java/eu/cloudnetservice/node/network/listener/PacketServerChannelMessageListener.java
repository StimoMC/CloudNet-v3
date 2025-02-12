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

package eu.cloudnetservice.node.network.listener;

import eu.cloudnetservice.driver.channel.ChannelMessage;
import eu.cloudnetservice.driver.event.EventManager;
import eu.cloudnetservice.driver.event.events.channel.ChannelMessageReceiveEvent;
import eu.cloudnetservice.driver.network.NetworkChannel;
import eu.cloudnetservice.driver.network.buffer.DataBuf;
import eu.cloudnetservice.driver.network.protocol.Packet;
import eu.cloudnetservice.driver.network.protocol.PacketListener;
import eu.cloudnetservice.node.Node;
import eu.cloudnetservice.node.provider.NodeMessenger;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

public final class PacketServerChannelMessageListener implements PacketListener {

  private final NodeMessenger messenger;
  private final EventManager eventManager;

  public PacketServerChannelMessageListener(@NonNull NodeMessenger messenger, @NonNull EventManager eventManager) {
    this.messenger = messenger;
    this.eventManager = eventManager;
  }

  @Override
  public void handle(@NonNull NetworkChannel channel, @NonNull Packet packet) {
    var comesFromWrapper = packet.content().readBoolean();
    var message = packet.content().readObject(ChannelMessage.class);
    // disable releasing of the message content
    message.content().disableReleasing();
    // check if we should handle the message locally
    var handleLocally = message.targets().stream().anyMatch(target -> switch (target.type()) {
      case ALL -> true;
      case NODE -> target.name() == null || target.name().equals(Node.instance().componentName());
      default -> false;
    });
    if (handleLocally) {
      // mark the index of the data buf
      message.content().startTransaction();
      // call the receive event
      var responseTask = this.eventManager
        .callEvent(new ChannelMessageReceiveEvent(message, channel, packet.uniqueId() != null))
        .queryResponse();
      // reset the index
      message.content().redoTransaction();
      // wait for the response to become available if given before resuming
      if (responseTask != null) {
        responseTask.thenAccept(response -> this.resumeHandling(packet, channel, message, response, comesFromWrapper));
        return;
      }
    }
    // resume instantly
    this.resumeHandling(packet, channel, message, null, comesFromWrapper);
  }

  private void resumeHandling(
    @NonNull Packet packet,
    @NonNull NetworkChannel channel,
    @NonNull ChannelMessage message,
    @Nullable ChannelMessage initialResponse,
    boolean comesFromWrapper
  ) {
    // do not redirect the channel message to the cluster to prevent infinite loops
    if (packet.uniqueId() != null) {
      this.messenger.sendChannelMessageQueryAsync(message, comesFromWrapper)
        .orTimeout(20, TimeUnit.SECONDS)
        .whenComplete((result, exception) -> {
          DataBuf responseContent;
          // check if the handling was successful
          if (exception == null) {
            // respond with the result or just the single initial response if given
            if (result == null) {
              responseContent = initialResponse == null
                ? DataBuf.empty()
                : DataBuf.empty().writeObject(Set.of(initialResponse));
            } else {
              // add the initial response if given before writing
              if (initialResponse != null) {
                result.add(initialResponse);
              }
              // serialize the response
              if (result.isEmpty()) {
                responseContent = DataBuf.empty();
              } else {
                responseContent = DataBuf.empty().writeObject(result);
              }
            }
          } else {
            // just respond with nothing when an exception was thrown
            responseContent = DataBuf.empty();
          }
          // send the results to the sender
          channel.sendPacket(packet.constructResponse(responseContent));
        });
    } else {
      this.messenger.sendChannelMessage(message, comesFromWrapper);
    }
    // force release the message
    message.content().enableReleasing().release();
  }
}
