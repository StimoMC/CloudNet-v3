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

package eu.cloudnetservice.driver.event;

import lombok.NonNull;

/**
 * An exception to indicate that something went wrong in relation to an event listener. By default, this exception is
 * thrown when an event invoker cannot get generated successfully or a listener invocation fails for any reason.
 *
 * @since 4.0
 */
public final class EventListenerException extends RuntimeException {

  /**
   * Constructs a new event listener exception with the given message and cause.
   *
   * @param message the detailed context message why the exception occurred.
   * @param cause   the cause why the exception occurred. In this cause the cause is required.
   * @throws NullPointerException if either message or cause is null.
   */
  public EventListenerException(@NonNull String message, @NonNull Throwable cause) {
    super(message, cause);
  }
}
