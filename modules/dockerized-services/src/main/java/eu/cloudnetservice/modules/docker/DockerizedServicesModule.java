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

package eu.cloudnetservice.modules.docker;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import eu.cloudnetservice.common.document.gson.JsonDocument;
import eu.cloudnetservice.driver.module.ModuleLifeCycle;
import eu.cloudnetservice.driver.module.ModuleTask;
import eu.cloudnetservice.driver.module.driver.DriverModule;
import eu.cloudnetservice.modules.docker.config.DockerConfiguration;
import eu.cloudnetservice.modules.docker.config.DockerImage;
import eu.cloudnetservice.node.Node;
import java.time.Duration;
import java.util.Set;
import lombok.NonNull;

public class DockerizedServicesModule extends DriverModule {

  private DockerConfiguration configuration;

  @ModuleTask
  public void loadConfiguration() {
    this.configuration = this.readConfig(DockerConfiguration.class, () -> new DockerConfiguration(
      "docker-jvm",
      "host",
      DockerImage.builder().repository("azul/zulu-openjdk").tag("17-jre-headless").build(),
      Set.of(),
      Set.of(),
      Set.of(),
      "unix:///var/run/docker.sock",
      null,
      null,
      null,
      null,
      null,
      null));
  }

  @ModuleTask(order = 22)
  public void registerServiceFactory() {
    var clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
      .withDockerHost(this.configuration.dockerHost())
      .withDockerCertPath(this.configuration.dockerCertPath())
      .withDockerTlsVerify(this.configuration.dockerCertPath() != null)
      .withRegistryUsername(this.configuration.registryUsername())
      .withRegistryEmail(this.configuration.registryEmail())
      .withRegistryPassword(this.configuration.registryPassword())
      .withRegistryUrl(this.configuration.registryUrl())
      .build();
    var dockerHttpClient = new ApacheDockerHttpClient.Builder()
      .dockerHost(clientConfig.getDockerHost())
      .sslConfig(clientConfig.getSSLConfig())
      .connectionTimeout(Duration.ofSeconds(30))
      .responseTimeout(Duration.ofSeconds(30))
      .build();
    // create the client and instantiate the service factory based on the information
    var dockerClient = DockerClientImpl.getInstance(clientConfig, dockerHttpClient);
    Node.instance().cloudServiceProvider().addCloudServiceFactory(
      this.configuration.factoryName(),
      new DockerizedServiceFactory(
        Node.instance(),
        this.eventManager(),
        dockerClient,
        this.configuration));
  }

  @ModuleTask(event = ModuleLifeCycle.STARTED)
  public void registerComponents() {
    Node.instance().commandProvider().register(new DockerCommand(this));
  }

  @ModuleTask(event = ModuleLifeCycle.STOPPED)
  public void unregisterServiceFactory() {
    Node.instance().cloudServiceProvider().removeCloudServiceFactory(this.configuration.factoryName());
  }

  public @NonNull DockerConfiguration config() {
    return this.configuration;
  }

  public void config(@NonNull DockerConfiguration configuration) {
    this.configuration = configuration;
    this.writeConfig(JsonDocument.newDocument(configuration));
  }
}
