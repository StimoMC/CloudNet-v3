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

package eu.cloudnetservice.node.event.network;

import eu.cloudnetservice.driver.event.events.network.NetworkEvent;
import eu.cloudnetservice.driver.network.NetworkChannel;
import eu.cloudnetservice.node.service.CloudService;
import lombok.NonNull;

public final class NetworkServiceAuthSuccessEvent extends NetworkEvent {

  private final CloudService cloudService;

  public NetworkServiceAuthSuccessEvent(@NonNull CloudService cloudService, @NonNull NetworkChannel channel) {
    super(channel);
    this.cloudService = cloudService;
  }

  public @NonNull CloudService cloudService() {
    return this.cloudService;
  }
}
