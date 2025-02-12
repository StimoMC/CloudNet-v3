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

package eu.cloudnetservice.node.event.task;

import eu.cloudnetservice.driver.event.Cancelable;
import eu.cloudnetservice.driver.event.Event;
import eu.cloudnetservice.driver.service.ServiceTask;
import lombok.NonNull;

public class LocalServiceTaskRemoveEvent extends Event implements Cancelable {

  private final ServiceTask task;
  private volatile boolean cancelled;

  public LocalServiceTaskRemoveEvent(@NonNull ServiceTask task) {
    this.task = task;
  }

  public @NonNull ServiceTask task() {
    return this.task;
  }

  public boolean cancelled() {
    return this.cancelled;
  }

  public void cancelled(boolean cancelled) {
    this.cancelled = cancelled;
  }
}
