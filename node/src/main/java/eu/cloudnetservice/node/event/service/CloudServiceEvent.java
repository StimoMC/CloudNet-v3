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

package eu.cloudnetservice.node.event.service;

import eu.cloudnetservice.driver.event.events.DriverEvent;
import eu.cloudnetservice.driver.service.ServiceConfiguration;
import eu.cloudnetservice.driver.service.ServiceInfoSnapshot;
import eu.cloudnetservice.node.service.CloudService;
import lombok.NonNull;

public abstract class CloudServiceEvent extends DriverEvent {

  private final CloudService service;

  public CloudServiceEvent(@NonNull CloudService service) {
    this.service = service;
  }

  public @NonNull CloudService service() {
    return this.service;
  }

  public @NonNull ServiceConfiguration serviceConfiguration() {
    return this.service.serviceConfiguration();
  }

  public @NonNull ServiceInfoSnapshot serviceInfo() {
    return this.service.serviceInfo();
  }
}
