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

package bt.cli.comparator;

import java.net.InetAddress;
import java.util.Comparator;

/**
 * @author Oleg Ermolaev Date: 24.02.2018 10:49
 */
public class InetAddressComparator implements Comparator<InetAddress> {
    public static final InetAddressComparator INET_ADDRESS_COMPARATOR = new InetAddressComparator();

    public static int compareInetAddress(InetAddress o1, InetAddress o2) {
        return INET_ADDRESS_COMPARATOR.compare(o1, o2);
    }

    @Override
    public int compare(InetAddress o1, InetAddress o2) {
        final byte[] a1 = o1.getAddress();
        final byte[] a2 = o2.getAddress();
        final int length = Math.min(a1.length, a2.length);
        for (int i = 0; i < length; ++i) {
            final int cmp = Integer.compare(((int) a1[i]) & 0xFF, ((int) a2[i]) & 0xFF);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(a1.length, a2.length);
    }
}
