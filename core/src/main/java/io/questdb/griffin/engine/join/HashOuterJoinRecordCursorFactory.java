/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2019 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package io.questdb.griffin.engine.join;

import io.questdb.cairo.*;
import io.questdb.cairo.map.Map;
import io.questdb.cairo.map.MapFactory;
import io.questdb.cairo.map.MapKey;
import io.questdb.cairo.map.MapValue;
import io.questdb.cairo.sql.*;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.std.Misc;
import io.questdb.std.Transient;

public class HashOuterJoinRecordCursorFactory extends AbstractRecordCursorFactory {
    private final Map joinKeyMap;
    private final RecordChain slaveChain;
    private final RecordCursorFactory masterFactory;
    private final RecordCursorFactory slaveFactory;
    private final RecordSink masterSink;
    private final RecordSink slaveKeySink;
    private final HashOuterJoinRecordCursor cursor;

    public HashOuterJoinRecordCursorFactory(
            CairoConfiguration configuration,
            RecordMetadata metadata,
            RecordCursorFactory masterFactory,
            RecordCursorFactory slaveFactory,
            @Transient ColumnTypes joinColumnTypes,
            @Transient ColumnTypes valueTypes, // this expected to be just LONG, we store chain references in map
            RecordSink masterSink,
            RecordSink slaveKeySink,
            RecordSink slaveChainSink,
            int columnSplit

    ) {
        super(metadata);
        this.masterFactory = masterFactory;
        this.slaveFactory = slaveFactory;
        joinKeyMap = MapFactory.createMap(configuration, joinColumnTypes, valueTypes);
        slaveChain = new RecordChain(slaveFactory.getMetadata(), slaveChainSink, configuration.getSqlHashJoinValuePageSize());
        this.masterSink = masterSink;
        this.slaveKeySink = slaveKeySink;
        this.cursor = new HashOuterJoinRecordCursor(
                columnSplit,
                joinKeyMap,
                slaveChain,
                NullRecordFactory.getInstance(slaveFactory.getMetadata())
        );
    }

    static void buildMap(RecordCursor slaveCursor, Record record, Map joinKeyMap, RecordSink slaveKeySink, RecordChain slaveChain) {
        joinKeyMap.clear();
        slaveChain.clear();
        while (slaveCursor.hasNext()) {
            MapKey key = joinKeyMap.withKey();
            key.put(record, slaveKeySink);
            MapValue value = key.createValue();
            if (value.isNew()) {
                long offset = slaveChain.put(record, -1);
                value.putLong(0, offset);
                value.putLong(1, offset);
            } else {
                value.putLong(1, slaveChain.put(record, value.getLong(1)));
            }
        }
    }

    @Override
    public void close() {
        joinKeyMap.close();
        slaveChain.close();
        ((JoinRecordMetadata) getMetadata()).close();
        masterFactory.close();
        slaveFactory.close();
    }

    @Override
    public RecordCursor getCursor(SqlExecutionContext executionContext) {
        RecordCursor slaveCursor = slaveFactory.getCursor(executionContext);
        try {
            buildMapOfSlaveRecords(slaveCursor);
        } catch (CairoException e) {
            slaveCursor.close();
            throw e;
        }
        cursor.of(masterFactory.getCursor(executionContext), slaveCursor);
        return cursor;
    }

    @Override
    public boolean isRandomAccessCursor() {
        return false;
    }

    private void buildMapOfSlaveRecords(RecordCursor slaveCursor) {
        buildMap(slaveCursor, slaveCursor.getRecord(), joinKeyMap, slaveKeySink, slaveChain);
    }

    private class HashOuterJoinRecordCursor implements NoRandomAccessRecordCursor {
        private final OuterJoinRecord record;
        private final RecordChain slaveChain;
        private final Map joinKeyMap;
        private final int columnSplit;
        private RecordCursor masterCursor;
        private RecordCursor slaveCursor;
        private Record masterRecord;
        private boolean useSlaveCursor;

        public HashOuterJoinRecordCursor(int columnSplit, Map joinKeyMap, RecordChain slaveChain, Record nullRecord) {
            this.record = new OuterJoinRecord(columnSplit, nullRecord);
            this.joinKeyMap = joinKeyMap;
            this.slaveChain = slaveChain;
            this.columnSplit = columnSplit;
        }

        @Override
        public void close() {
            masterCursor = Misc.free(masterCursor);
            slaveCursor = Misc.free(slaveCursor);
        }

        @Override
        public long size() {
            return -1;
        }

        @Override
        public Record getRecord() {
            return record;
        }

        @Override
        public SymbolTable getSymbolTable(int columnIndex) {
            if (columnIndex < columnSplit) {
                return masterCursor.getSymbolTable(columnIndex);
            }
            return slaveCursor.getSymbolTable(columnIndex - columnSplit);
        }

        @Override
        public boolean hasNext() {
            if (useSlaveCursor && slaveChain.hasNext()) {
                return true;
            }

            if (masterCursor.hasNext()) {
                MapKey key = joinKeyMap.withKey();
                key.put(masterRecord, masterSink);
                MapValue value = key.findValue();
                if (value != null) {
                    slaveChain.of(value.getLong(0));
                    // we know cursor has values
                    // advance to get first value
                    slaveChain.hasNext();
                    useSlaveCursor = true;
                    record.hasSlave(true);
                } else {
                    useSlaveCursor = false;
                    record.hasSlave(false);
                }
                return true;
            }
            return false;
        }

        @Override
        public void toTop() {
            masterCursor.toTop();
            useSlaveCursor = false;
        }

        void of(RecordCursor masterCursor, RecordCursor slaveCursor) {
            this.masterCursor = masterCursor;
            this.slaveCursor = slaveCursor;
            this.masterRecord = masterCursor.getRecord();
            Record slaveRecord = slaveChain.getRecord();
            this.slaveChain.setSymbolTableResolver(slaveCursor);
            record.of(masterRecord, slaveRecord);
            useSlaveCursor = false;
        }
    }
}
