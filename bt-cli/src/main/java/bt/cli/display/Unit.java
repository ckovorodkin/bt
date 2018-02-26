/*
 * Copyright (c) 2016—2018 Andrei Tomashpolskiy and individual contributors.
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

package bt.cli.display;

/**
 * @author Oleg Ermolaev Date: 25.02.2018 18:35
 */
public class Unit {
    private final long value;
    private final String alias;

    public Unit(long value, String alias) {
        this.value = value;
        this.alias = alias;
    }

    public static String getDisplayString(String format, long value, Unit[] units) {
        return getDisplayString(format, value, units, true);
    }

    public static String getDisplayString(String format, long value, Unit[] units, boolean hideZero) {
        assert value >= 0;
        for (int i = units.length - 1; i >= 0; --i) {
            if (units[i].getValue() <= value) {
                final double d = value / (double) units[i].getValue();
                return String.format(format, d, units[i].getAlias());
            }
        }
        assert value == 0;
        if (hideZero) {
            return "";
        } else {
            return String.format(format, 0.0, units[0].getAlias());
        }
    }

    public long getValue() {
        return value;
    }

    public String getAlias() {
        return alias;
    }
}
