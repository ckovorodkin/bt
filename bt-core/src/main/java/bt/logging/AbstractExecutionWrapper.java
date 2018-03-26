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

package bt.logging;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Oleg Ermolaev Date: 26.03.2018 6:31
 */
public abstract class AbstractExecutionWrapper {
    public void run(Runnable runnable) {
        wrap(runnable).run();
    }

/*
    public <T> void accept(Consumer<T> consumer, T t) {
        wrap(consumer).accept(t);
    }

    public <T, U> void accept(BiConsumer<T, U> biConsumer, T t, U u) {
        wrap(biConsumer).accept(t, u);
    }
*/

    public <U> U get(Supplier<U> supplier) {
        return wrap(supplier).get();
    }

/*
    public <T, R> R apply(Function<T, R> function, T t) {
        return wrap(function).apply(t);
    }

    public <T, U, R> R apply(BiFunction<T, U, R> function, T t, U u) {
        return wrap(function).apply(t, u);
    }
*/

    public Runnable wrap(Runnable runnable) {
        return isBypass() ? runnable : () -> wrapAndRun(runnable);
    }

    public <T> Consumer<T> wrap(Consumer<T> consumer) {
        return isBypass() ? consumer : (t) -> wrapAndRun(() -> consumer.accept(t));
    }

    public <T, U> BiConsumer<T, U> wrap(BiConsumer<T, U> biConsumer) {
        return isBypass() ? biConsumer : (t, u) -> wrapAndRun(() -> biConsumer.accept(t, u));
    }

    public <T> Supplier<T> wrap(Supplier<T> supplier) {
        return isBypass() ? supplier : () -> wrapAndSupply(supplier);
    }

    public <T, R> Function<T, R> wrap(Function<T, R> function) {
        return isBypass() ? function : (t) -> wrapAndSupply(() -> function.apply(t));
    }

    public <T, U, R> BiFunction<T, U, R> wrap(BiFunction<T, U, R> function) {
        return isBypass() ? function : (t, u) -> wrapAndSupply(() -> function.apply(t, u));
    }

    protected abstract boolean isBypass();

    protected abstract void wrapAndRun(Runnable action);

    protected abstract <U> U wrapAndSupply(Supplier<U> supplier);
}
