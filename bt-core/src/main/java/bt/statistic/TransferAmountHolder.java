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

package bt.statistic;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Oleg Ermolaev Date: 17.02.2018 8:55
 */
public class TransferAmountHolder implements TransferAmount, TransferAmountHandler {
    private final TransferAmountHolder parent;
    private final AtomicLong upload;
    private final AtomicLong download;

    public TransferAmountHolder(TransferAmountHolder parent) {
        this.parent = parent;
        this.upload = new AtomicLong();
        this.download = new AtomicLong();
    }

    @Override
    public long getUpload() {
        return upload.get();
    }

    @Override
    public long getDownload() {
        return download.get();
    }

    @Override
    public void handleUpload(long value) {
        upload.addAndGet(value);
        if (parent != null) {
            parent.handleUpload(value);
        }
    }

    @Override
    public void handleDownload(long value) {
        download.addAndGet(value);
        if (parent != null) {
            parent.handleDownload(value);
        }
    }
}
