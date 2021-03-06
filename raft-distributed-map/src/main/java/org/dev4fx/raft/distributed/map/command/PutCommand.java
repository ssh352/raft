/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hover-raft (tools4j), Anton Anufriev, Marco Terzer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.dev4fx.raft.distributed.map.command;

import java.io.Serializable;
import java.util.Objects;

public class PutCommand<K extends Serializable, V extends Serializable> implements Command<K, V> {
    private final int mapId;
    private final K key;
    private final V value;
    private final FutureResult<? super V> futureResult;

    public PutCommand(final int mapId,
                      final K key,
                      final V value,
                      final FutureResult<? super V> futureResult) {
        this.mapId = mapId;
        this.key = Objects.requireNonNull(key);
        this.value = Objects.requireNonNull(value);
        this.futureResult = Objects.requireNonNull(futureResult);
    }

    public int mapId() {
        return mapId;
    }

    public K key() {
        return key;
    }

    public V value() {
        return value;
    }

    public void complete(final V result) {
        futureResult.accept(result);
    }

    @Override
    public void accept(long sequence, final CommandHandler<K, V> commandHandler) {
        commandHandler.onCommand(sequence, this);
    }
}
