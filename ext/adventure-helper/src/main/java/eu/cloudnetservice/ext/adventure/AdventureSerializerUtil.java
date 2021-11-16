/*
 * Copyright 2019-2021 CloudNetService team & contributors
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

package eu.cloudnetservice.ext.adventure;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

public final class AdventureSerializerUtil {

  public static final char HEX_CHAR = '#';
  public static final char LEGACY_CHAR = '&';
  public static final char BUNGEE_HEX_CHAR = 'x';

  private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
    .character('§')
    .extractUrls()
    .hexColors()
    .build();

  private AdventureSerializerUtil() {
    throw new UnsupportedOperationException();
  }

  public static @NotNull Component serialize(@NotNull String textToSerialize) {
    StringBuilder result = new StringBuilder();
    // find all legacy chars
    char[] chars = textToSerialize.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      // check if the current char is a legacy text char
      if (i < chars.length - 1) {
        // check if the next char is a legacy color char
        char next = chars[i + 1];
        if (chars[i] == LEGACY_CHAR) {
          if ((next >= '0' && next <= '9') || (next >= 'a' && next <= 'f') || next == 'r') {
            result.append('§');
            continue;
          }
          // check if the next char is a hex begin char
          // 7 because of current hex_char 6_digit_hex (for example &#000fff)
          if (next == HEX_CHAR && i + 7 < chars.length) {
            result.append('§');
            continue;
          }
        }
        // check for the stupid bungee cord chat hex format
        // 13 because of current hex_char 12_digit_hex (for example &x&0&0&0&f&f&f)
        if (next == BUNGEE_HEX_CHAR && i + 13 < chars.length) {
          // open the modern hex format
          result.append('§').append(HEX_CHAR);
          // replace the terrible format
          // begin at i+3 to skip the initial &x
          // end at i+14 because the hex format is 14 chars long
          // pos+=2 to skip each &
          for (int pos = i + 3; pos < i + 14; pos += 2) {
            result.append(chars[pos]);
          }
          // skip over the hex thing and continue there
          i += 13;
          continue;
        }
      }
      // append just the char at the position
      result.append(chars[i]);
    }
    // serialize the text now
    return SERIALIZER.deserialize(result.toString());
  }
}
