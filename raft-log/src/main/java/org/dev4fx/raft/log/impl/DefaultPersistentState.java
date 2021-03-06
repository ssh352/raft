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
package org.dev4fx.raft.log.impl;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.dev4fx.raft.mmap.api.RegionAccessor;
import org.dev4fx.raft.log.api.PersistentState;

import java.util.Objects;

public class DefaultPersistentState implements PersistentState {
    private static final int NULL_POSITION = -1;
    private static final int TERM_OFFSET = 0;
    private static final int TERM_SIZE = 4;

    private static final int SIZE_OFFSET = 0;
    private static final int SIZE_SIZE = 8;
    private static final int CURRENT_TERM_OFFSET = SIZE_OFFSET + SIZE_SIZE;
    private static final int CURRENT_TERM_SIZE = TERM_SIZE;
    private static final int VOTED_FOR_OFFSET = CURRENT_TERM_OFFSET + CURRENT_TERM_SIZE;
    private static final int VOTED_FOR_SIZE = 4;

    private static final int PAYLOAD_POSITION_OFFSET = TERM_OFFSET + TERM_SIZE;
    private static final int PAYLOAD_POSITION_SIZE = 8;
    private static final int PAYLOAD_LENGTH_OFFSET = PAYLOAD_POSITION_OFFSET + PAYLOAD_POSITION_SIZE;
    private static final int PAYLOAD_LENGTH_SIZE = 4;
    private static final int INDEX_ROW_SIZE = TERM_SIZE + PAYLOAD_POSITION_SIZE + PAYLOAD_LENGTH_SIZE;

    private final RegionAccessor indexAccessor;
    private final RegionAccessor payloadAccessor;
    private final RegionAccessor headerAccessor;

    private final UnsafeBuffer headerBuffer;
    private final UnsafeBuffer indexBuffer;
    private final UnsafeBuffer payloadBuffer;

    private long payloadNextAppendPosition;

    public DefaultPersistentState(final RegionAccessor indexAccessor,
                                  final RegionAccessor payloadAccessor,
                                  final RegionAccessor headerAccessor) {
        this.indexAccessor = Objects.requireNonNull(indexAccessor);
        this.payloadAccessor = Objects.requireNonNull(payloadAccessor);
        this.headerAccessor = Objects.requireNonNull(headerAccessor);
        headerBuffer = new UnsafeBuffer();
        indexBuffer = new UnsafeBuffer();
        payloadBuffer = new UnsafeBuffer();
        headerAccessor.wrap(0, headerBuffer);
        resetPayloadNextAppendPosition();
    }

    private void initPayloadNextAppendPosition(final long lastIndex) {
        if (payloadNextAppendPosition == NULL_POSITION) {
            if (lastIndex > NULL_INDEX) {
                wrapIndex(lastIndex);
                final long lastPayloadPosition = indexPayloadPosition();
                final int lastPayloadLength = indexPayloadLength();
                payloadNextAppendPosition = lastPayloadPosition + lastPayloadLength;
            } else {
                payloadNextAppendPosition = 0;
            }
        }
    }

    private void resetPayloadNextAppendPosition() {
        payloadNextAppendPosition = NULL_POSITION;
    }

    private void incrementPayloadNextAppendPosition(final long length) {
        payloadNextAppendPosition += length;
    }

    @Override
    public void append(final int term, final DirectBuffer buffer, final int offset, final int length) {
        final long lastIndex = lastIndex();
        initPayloadNextAppendPosition(lastIndex);

        if (payloadAccessor.wrap(payloadNextAppendPosition, payloadBuffer)) {
            if (payloadBuffer.capacity() < length) {
                incrementPayloadNextAppendPosition(payloadBuffer.capacity());
                if (!payloadAccessor.wrap(payloadNextAppendPosition, payloadBuffer)) {
                    throw new IllegalStateException("Failed to wrap payload buffer for position " + payloadNextAppendPosition);
                }
            }
            buffer.getBytes(offset, payloadBuffer, 0, length);
            wrapIndex(lastIndex + 1);
            indexTerm(term);
            indexPayloadPosition(payloadNextAppendPosition);
            indexPayloadLength(length);
            size(size() + 1);
            incrementPayloadNextAppendPosition(length);
        } else {
            throw new IllegalStateException("Failed to wrap payload buffer for position " + payloadNextAppendPosition);
        }
    }

    @Override
    public long size() {
        return headerBuffer.getLong(SIZE_OFFSET);
    }

    private void size(final long size) {
        headerBuffer.putLong(SIZE_OFFSET, size);
    }

    @Override
    public int currentTerm() {
        return headerBuffer.getInt(CURRENT_TERM_OFFSET);
    }

    @Override
    public void currentTerm(final int term) {
        headerBuffer.putInt(CURRENT_TERM_OFFSET, term);
    }

    @Override
    public int votedFor() {
        return headerBuffer.getInt(VOTED_FOR_OFFSET);
    }

    @Override
    public void votedFor(final int serverId) {
        headerBuffer.putInt(VOTED_FOR_OFFSET, serverId);
    }


    private void wrapIndex(long index) {
        if (!indexAccessor.wrap(index * INDEX_ROW_SIZE, indexBuffer)) {
            throw new IllegalStateException("Failed to wrap index buffer for index " + index);
        }
    }

    private int indexTerm() {
        return indexBuffer.getInt(TERM_OFFSET);
    }

    private void indexTerm(final int term) {
        indexBuffer.putInt(TERM_OFFSET, term);
    }

    private long indexPayloadPosition() {
        return indexBuffer.getLong(PAYLOAD_POSITION_OFFSET);
    }

    private void indexPayloadPosition(final long payloadPosition) {
        indexBuffer.putLong(PAYLOAD_POSITION_OFFSET, payloadPosition);
    }

    private int indexPayloadLength() {
        return indexBuffer.getInt(PAYLOAD_LENGTH_OFFSET);
    }

    private void indexPayloadLength(final int payloadLength) {
        indexBuffer.putInt(PAYLOAD_LENGTH_OFFSET, payloadLength);
    }

    @Override
    public int term(final long index) {
        if (index > NULL_INDEX) {
            final long lastIndex = lastIndex();
            if (index > lastIndex) {
                throw new IllegalArgumentException("Index " + index + " of out last index boundary " + lastIndex);
            }
            wrapIndex(index);
            return indexTerm();
        } else {
            return NULL_TERM;
        }
    }

    @Override
    public void wrap(final long index, final DirectBuffer buffer) {
        final long lastIndex = lastIndex();
        if (index > NULL_INDEX && index <= lastIndex) {
            wrapIndex(index);
            final long payloadPosition = indexPayloadPosition();
            final int payloadLength = indexPayloadLength();
            if (payloadAccessor.wrap(payloadPosition, payloadBuffer)) {
                buffer.wrap(payloadBuffer, 0, payloadLength);
            } else {
                throw new IllegalStateException("Failed to wrap payload buffer for position " + payloadPosition);
            }
        } else {
            throw new IllegalArgumentException("Index [" + index + "] must be positive and <= " + lastIndex);
        }
    }

    @Override
    public void truncate(final long size) {
        final long currentSize = size();
        if (size >= 0 && size <= currentSize) {
            size(size);
            resetPayloadNextAppendPosition();
        } else {
            throw new IllegalArgumentException("Size [" + size + "] must be positive and <= current size " + currentSize);
        }
    }

    @Override
    public void close() {
        payloadAccessor.close();
        indexAccessor.close();
        headerAccessor.close();
    }
}
