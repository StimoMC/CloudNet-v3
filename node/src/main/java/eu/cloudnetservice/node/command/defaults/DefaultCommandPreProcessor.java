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

package eu.cloudnetservice.node.command.defaults;

import cloud.commandframework.execution.preprocessor.CommandPreprocessingContext;
import cloud.commandframework.execution.preprocessor.CommandPreprocessor;
import cloud.commandframework.services.types.ConsumerService;
import eu.cloudnetservice.node.Node;
import eu.cloudnetservice.node.command.CommandProvider;
import eu.cloudnetservice.node.command.source.CommandSource;
import eu.cloudnetservice.node.event.command.CommandPreProcessEvent;
import lombok.NonNull;

/**
 * {@inheritDoc}
 */
final class DefaultCommandPreProcessor implements CommandPreprocessor<CommandSource> {

  private final CommandProvider provider;

  DefaultCommandPreProcessor(@NonNull CommandProvider provider) {
    this.provider = provider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void accept(@NonNull CommandPreprocessingContext<CommandSource> context) {
    var commandContext = context.getCommandContext();
    var source = context.getCommandContext().getSender();
    // we only process command executions and not the tab complete handling
    if (commandContext.isSuggestions()) {
      return;
    }

    // get the first argument and retrieve the command info using it
    var firstArgument = commandContext.getRawInput().getFirst();
    var commandInfo = this.provider.command(firstArgument);
    // should never happen - just make sure
    if (commandInfo != null) {
      var preProcessEvent = Node.instance().eventManager()
        .callEvent(new CommandPreProcessEvent(commandContext.getRawInput(), commandInfo, source, this.provider));
      if (preProcessEvent.cancelled()) {
        ConsumerService.interrupt();
      }
    }
  }
}
