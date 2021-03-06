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
package org.dev4fx.raft.distributed.map.out;

import org.agrona.MutableDirectBuffer;
import org.dev4fx.raft.distributed.map.codec.Serialiser;
import org.dev4fx.raft.distributed.map.command.*;
import org.dev4fx.raft.dmap.sbe.*;
import org.dev4fx.raft.state.CommandPublisher;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class EncodingCommandHandler<K extends Serializable, V extends Serializable> implements CommandHandler<K, V> {
    private final int sourceId;
    private final MutableDirectBuffer encodingBuffer;
    private final MutableDirectBuffer entryEncodingBuffer;
    private final Serialiser<K> keySerialiser;
    private final Serialiser<V> valueSerialiser;
    private final CommandPublisher commandPublisher;

    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final PutCommandEncoder putCommandEncoder = new PutCommandEncoder();
    private final PutAllCommandEncoder putAllCommandEncoder = new PutAllCommandEncoder();
    private final RemoveCommandEncoder removeCommandEncoder = new RemoveCommandEncoder();
    private final ClearCommandEncoder clearCommandEncoder = new ClearCommandEncoder();

    public EncodingCommandHandler(final int sourceId,
                                  final MutableDirectBuffer encodingBuffer,
                                  final MutableDirectBuffer entryEncodingBuffer,
                                  final Serialiser<K> keySerialiser,
                                  final Serialiser<V> valueSerialiser,
                                  final CommandPublisher commandPublisher) {
        this.sourceId = sourceId;
        this.encodingBuffer = Objects.requireNonNull(encodingBuffer);
        this.entryEncodingBuffer = Objects.requireNonNull(entryEncodingBuffer);
        this.keySerialiser = Objects.requireNonNull(keySerialiser);
        this.valueSerialiser = Objects.requireNonNull(valueSerialiser);
        this.commandPublisher = Objects.requireNonNull(commandPublisher);
    }

    @Override
    public void onCommand(final long sequence, final PutCommand<K, V> putCommand) {

        final int headerLength = messageHeaderEncoder.wrap(encodingBuffer, 0)
                .blockLength(PutCommandEncoder.BLOCK_LENGTH)
                .schemaId(PutCommandEncoder.SCHEMA_ID)
                .version(PutCommandEncoder.SCHEMA_VERSION)
                .templateId(PutCommandEncoder.TEMPLATE_ID)
                .mapId(putCommand.mapId())
                .encodedLength();

        final int keyDataLength = keySerialiser.serialise(putCommand.key(), entryEncodingBuffer, 0);

        putCommandEncoder.wrap(encodingBuffer, headerLength)
                .putKey(entryEncodingBuffer, 0, keyDataLength);

        final int valueDataLength = valueSerialiser.serialise(putCommand.value(), entryEncodingBuffer, 0);

        final int messageLength = putCommandEncoder.putValue(entryEncodingBuffer, 0, valueDataLength)
                .encodedLength();

        commandPublisher.publish(sourceId, sequence, encodingBuffer, 0, headerLength + messageLength);
    }

    @Override
    public void onCommand(final long sequence, final RemoveCommand<K, V> removeCommand) {
        final int headerLength = messageHeaderEncoder.wrap(encodingBuffer, 0)
                .blockLength(RemoveCommandEncoder.BLOCK_LENGTH)
                .schemaId(RemoveCommandEncoder.SCHEMA_ID)
                .version(RemoveCommandEncoder.SCHEMA_VERSION)
                .templateId(RemoveCommandEncoder.TEMPLATE_ID)
                .mapId(removeCommand.mapId())
                .encodedLength();

        final int keyDataLength = keySerialiser.serialise(removeCommand.key(), entryEncodingBuffer, 0);

        final int messageLength = removeCommandEncoder.wrap(encodingBuffer, headerLength)
                .putKey(entryEncodingBuffer, 0, keyDataLength)
                .encodedLength();

        commandPublisher.publish(sourceId, sequence, encodingBuffer, 0, headerLength + messageLength);
    }

    @Override
    public void onCommand(final long sequence, final PutAllCommand<K, V> putAllCommand) {
        final int headerLength = messageHeaderEncoder.wrap(encodingBuffer, 0)
                .blockLength(PutAllCommandEncoder.BLOCK_LENGTH)
                .schemaId(PutAllCommandEncoder.SCHEMA_ID)
                .version(PutAllCommandEncoder.SCHEMA_VERSION)
                .templateId(PutAllCommandEncoder.TEMPLATE_ID)
                .mapId(putAllCommand.mapId())
                .encodedLength();

        final PutAllCommandEncoder.EntriesEncoder entriesEncoder = putAllCommandEncoder
                .wrap(encodingBuffer, headerLength)
                .entriesCount(putAllCommand.values().size());

        for(final Map.Entry<? extends K, ? extends V> entry : putAllCommand.values().entrySet()) {

            final int keyDataLength = keySerialiser.serialise(entry.getKey(), entryEncodingBuffer, 0);

            entriesEncoder.next()
                   .putKey(entryEncodingBuffer, 0, keyDataLength);

            final int valueDataLength = valueSerialiser.serialise(entry.getValue(), entryEncodingBuffer, 0);

            entriesEncoder.putValue(entryEncodingBuffer, 0, valueDataLength);
        }
        final int messageLength = putAllCommandEncoder.encodedLength();
        commandPublisher.publish(sourceId, sequence, encodingBuffer, 0, headerLength + messageLength);
    }

    @Override
    public void onCommand(final long sequence, final ClearCommand clearCommand) {
        final int headerLength = messageHeaderEncoder.wrap(encodingBuffer, 0)
                .blockLength(ClearCommandEncoder.BLOCK_LENGTH)
                .schemaId(ClearCommandEncoder.SCHEMA_ID)
                .version(ClearCommandEncoder.SCHEMA_VERSION)
                .templateId(ClearCommandEncoder.TEMPLATE_ID)
                .mapId(clearCommand.mapId())
                .encodedLength();

        final int messageLength = clearCommandEncoder.wrap(encodingBuffer, headerLength)
                .encodedLength();

        commandPublisher.publish(sourceId, sequence, encodingBuffer, 0, headerLength + messageLength);
    }
}
