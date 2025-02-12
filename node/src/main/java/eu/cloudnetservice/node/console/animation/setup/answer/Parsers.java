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

package eu.cloudnetservice.node.console.animation.setup.answer;

import com.google.common.base.Enums;
import com.google.common.base.Preconditions;
import eu.cloudnetservice.common.JavaVersion;
import eu.cloudnetservice.common.StringUtil;
import eu.cloudnetservice.common.collection.Pair;
import eu.cloudnetservice.driver.network.HostAndPort;
import eu.cloudnetservice.driver.service.ServiceEnvironmentType;
import eu.cloudnetservice.node.Node;
import eu.cloudnetservice.node.util.JavaVersionResolver;
import eu.cloudnetservice.node.util.NetworkUtil;
import eu.cloudnetservice.node.version.ServiceVersion;
import eu.cloudnetservice.node.version.ServiceVersionType;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import lombok.NonNull;

public final class Parsers {

  public Parsers() {
    throw new UnsupportedOperationException();
  }

  public static @NonNull QuestionAnswerType.Parser<String> nonEmptyStr() {
    return input -> {
      if (input.trim().isEmpty()) {
        throw ParserException.INSTANCE;
      }
      return input;
    };
  }

  public static @NonNull QuestionAnswerType.Parser<String> limitedStr(int length) {
    return input -> {
      if (input.length() > length) {
        throw ParserException.INSTANCE;
      }
      return input;
    };
  }

  public static @NonNull <T extends Enum<T>> QuestionAnswerType.Parser<T> enumConstant(@NonNull Class<T> enumClass) {
    return input -> Preconditions.checkNotNull(Enums.getIfPresent(enumClass, StringUtil.toUpper(input)).orNull());
  }

  public static @NonNull QuestionAnswerType.Parser<String> regex(@NonNull Pattern pattern) {
    return input -> {
      if (pattern.matcher(input).matches()) {
        return input;
      }
      throw ParserException.INSTANCE;
    };
  }

  public static @NonNull QuestionAnswerType.Parser<Pair<String, JavaVersion>> javaVersion() {
    return input -> {
      var version = JavaVersionResolver.resolveFromJavaExecutable(input);
      if (version == null) {
        throw ParserException.INSTANCE;
      }
      return new Pair<>(input.trim(), version);
    };
  }

  public static @NonNull QuestionAnswerType.Parser<Pair<ServiceVersionType, ServiceVersion>> serviceVersion() {
    return input -> {
      // install no version
      if (input.equalsIgnoreCase("none")) {
        return null;
      }
      // try to split the name of the version
      var result = input.split("-", 2);
      if (result.length != 2) {
        throw ParserException.INSTANCE;
      }
      // get the type
      var type = Node.instance().serviceVersionProvider().getServiceVersionType(result[0]);
      if (type == null) {
        throw ParserException.INSTANCE;
      }

      // get the version
      var version = type.version(result[1]);
      if (version == null) {
        throw ParserException.INSTANCE;
      }

      // combine the result
      return new Pair<>(type, version);
    };
  }

  public static @NonNull QuestionAnswerType.Parser<ServiceEnvironmentType> serviceEnvironmentType() {
    return input -> {
      var type = Node.instance().serviceVersionProvider().getEnvironmentType(input);
      if (type != null) {
        return type;
      }

      throw ParserException.INSTANCE;
    };
  }

  public static @NonNull QuestionAnswerType.Parser<String> nonExistingTask() {
    return input -> {
      var task = Node.instance().serviceTaskProvider().serviceTask(input);
      if (task != null) {
        throw ParserException.INSTANCE;
      }
      return input.trim();
    };
  }

  @SafeVarargs
  public static @NonNull <T> QuestionAnswerType.Parser<T> allOf(@NonNull QuestionAnswerType.Parser<T>... parsers) {
    return input -> {
      T result = null;
      for (var parser : parsers) {
        result = parser.parse(input);
      }
      return result;
    };
  }

  public static @NonNull QuestionAnswerType.Parser<UUID> uuid() {
    return UUID::fromString;
  }

  public static @NonNull QuestionAnswerType.Parser<Integer> anyNumber() {
    return Integer::parseInt;
  }

  public static @NonNull QuestionAnswerType.Parser<Integer> ranged(int from, int to) {
    return input -> {
      var value = Integer.parseInt(input);
      if (value < from || value > to) {
        throw ParserException.INSTANCE;
      }
      return value;
    };
  }

  public static @NonNull QuestionAnswerType.Parser<Boolean> bool() {
    return input -> {
      if (input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("no")) {
        return input.equalsIgnoreCase("yes");
      } else {
        throw ParserException.INSTANCE;
      }
    };
  }

  public static @NonNull QuestionAnswerType.Parser<HostAndPort> validatedHostAndPort(boolean withPort) {
    return input -> {
      var host = NetworkUtil.parseHostAndPort(input, withPort);
      if (host == null) {
        throw ParserException.INSTANCE;
      }
      return host;
    };
  }

  public static @NonNull QuestionAnswerType.Parser<HostAndPort> assignableHostAndPort(boolean withPort) {
    return input -> {
      var host = NetworkUtil.parseAssignableHostAndPort(input, withPort);
      if (host == null) {
        throw ParserException.INSTANCE;
      }
      return host;
    };
  }

  public static @NonNull QuestionAnswerType.Parser<HostAndPort> nonWildcardHost(
    @NonNull QuestionAnswerType.Parser<HostAndPort> parser
  ) {
    return input -> {
      var hostAndPort = parser.parse(input);
      if (hostAndPort == null || NetworkUtil.checkWildcard(hostAndPort)) {
        throw ParserException.INSTANCE;
      }

      return hostAndPort;
    };
  }

  public static @NonNull QuestionAnswerType.Parser<String> assignableHostAndPortOrAlias() {
    return input -> {
      var ipAlias = Node.instance().config().ipAliases().get(input);
      // the input is an ip alias
      if (ipAlias != null) {
        return ipAlias;
      }
      // parse a host and check if it is assignable
      var host = NetworkUtil.parseAssignableHostAndPort(input, false);
      // we've found an assignable host
      if (host != null) {
        return host.host();
      }
      throw ParserException.INSTANCE;
    };
  }

  public static @NonNull <I, O> QuestionAnswerType.Parser<O> andThen(
    @NonNull QuestionAnswerType.Parser<I> parser,
    @NonNull Function<I, O> combiner
  ) {
    return input -> combiner.apply(parser.parse(input));
  }

  public static final class ParserException extends RuntimeException {

    public static final ParserException INSTANCE = new ParserException();
  }
}
