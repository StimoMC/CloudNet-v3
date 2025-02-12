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

package eu.cloudnetservice.modules.report.emitter.defaults;

import eu.cloudnetservice.driver.CloudNetDriver;
import eu.cloudnetservice.driver.module.ModuleWrapper;
import eu.cloudnetservice.driver.module.driver.DriverModule;
import eu.cloudnetservice.modules.report.emitter.ReportDataWriter;
import eu.cloudnetservice.modules.report.emitter.SpecificReportDataEmitter;
import java.util.Collection;
import lombok.NonNull;

public final class LocalModuleDataEmitter extends SpecificReportDataEmitter<ModuleWrapper> {

  public LocalModuleDataEmitter() {
    super((writer, modules) -> writer.appendString("Local Modules (").appendInt(modules.size()).appendString("):"));
  }

  @Override
  public @NonNull Collection<ModuleWrapper> collectData() {
    return CloudNetDriver.instance().moduleProvider().modules();
  }

  @Override
  public @NonNull ReportDataWriter emitData(@NonNull ReportDataWriter writer, @NonNull ModuleWrapper value) {
    var module = value.module();
    writer = writer
      .beginSection(module.name())
      // Group: eu.cloudnetservice; Name: CloudFlare; Version: 1.0; Lifecycle: STARTED
      .appendString("Group: ")
      .appendString(module.group())
      .appendString("; Name: ")
      .appendString(module.name())
      .appendString("; Version: ")
      .appendString(module.version())
      .appendString("; Lifecycle: ")
      .appendString(value.moduleLifeCycle().name())
      .appendNewline();

    // append configuration if possible
    writer.appendString("Module Configuration:").appendNewline();
    if (module.moduleConfig().storesSensitiveData()) {
      // sensitive data, don't print that out
      writer.appendString("<retracted, stores sensitive data>");
    } else if (module instanceof DriverModule driverModule) {
      // print out the whole config
      writer.appendString(driverModule.readConfig().toPrettyJson());
    } else {
      // unable to read the config
      writer.appendString("<unable to read configuration>");
    }

    // end the section
    return writer.endSection();
  }
}
