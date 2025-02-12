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

package eu.cloudnetservice.driver.network.rpc;

import eu.cloudnetservice.driver.network.buffer.DataBufFactory;
import eu.cloudnetservice.driver.network.rpc.object.ObjectMapper;
import lombok.NonNull;

/**
 * The base class implemented by anything which is related to rpc.
 *
 * @since 4.0
 */
public interface RPCProvider {

  /**
   * Get the class which is targeted by the associated rpc.
   *
   * @return the target class.
   */
  @NonNull Class<?> targetClass();

  /**
   * Get the object mapper which is used for (de-) serialization of objects if needed.
   *
   * @return the associated object mapper.
   */
  @NonNull ObjectMapper objectMapper();

  /**
   * Get the data buf factory which is used to allocate network data buffers if needed.
   *
   * @return the associated data buf factory.
   */
  @NonNull DataBufFactory dataBufFactory();
}
