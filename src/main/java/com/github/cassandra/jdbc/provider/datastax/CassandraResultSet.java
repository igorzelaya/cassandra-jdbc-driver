/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package com.github.cassandra.jdbc.provider.datastax;

import com.datastax.driver.core.ColumnDefinitions.Definition;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.github.cassandra.jdbc.*;
import org.pmw.tinylog.Logger;

import java.nio.ByteBuffer;
import java.sql.Blob;
import java.sql.SQLException;

/**
 * This is a result set implementation built on top of DataStax Java driver.
 *
 * @author Zhichun Wu
 */
public class CassandraResultSet extends BaseCassandraResultSet {
    private Row _currentRow;
    private ResultSet _resultSet;

    protected CassandraResultSet(BaseCassandraStatement statement, ResultSet rs) {
        super(statement);

        if (rs != null) {
            for (Definition def : rs.getColumnDefinitions()) {
                CassandraColumnDefinition d = new CassandraColumnDefinition(
                        def.getKeyspace(), def.getTable(), def.getName(), def
                        .getType().getName().toString(), false);
                metadata.addColumnDefinition(d);
            }
        }

        _resultSet = rs;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getValue(int columnIndex, Class<T> clazz)
            throws SQLException {
        Logger.trace(new StringBuilder(
                "Trying to get value with inputs: line=").append(getRow())
                .append(", column=").append(columnIndex).append(", type=")
                .append(clazz).toString());

        Object rawValue = null;
        T result = null;
        if (_currentRow != null) {
            rawValue = _currentRow.getObject(columnIndex - 1);

            Logger.trace(new StringBuilder("Got raw value [")
                    .append(rawValue).append("] from line #")
                    .append(getRow()).toString());

            if (rawValue != null) {
                wasNull = false;
                if (String.class == clazz) {
                    result = (T) String.valueOf(rawValue);
                } else if (Object.class == clazz) {
                    result = (T) (rawValue instanceof ByteBuffer ? ((ByteBuffer) rawValue).array() : rawValue);
                } else if (Blob.class == clazz) {
                    result = (T) new CassandraBlob((ByteBuffer) rawValue);
                } else if (byte[].class == clazz) {
                    result = (T) ((ByteBuffer) rawValue).array();
                } else {
                    try {
                        result = clazz.cast(rawValue);
                    } catch (ClassCastException e) {
                        Logger.warn(new StringBuilder(
                                        "Not able to convert [").append(rawValue)
                                        .append("] to ").append(clazz).toString(),
                                e);

                        if (!quiet) {
                            throw new SQLException(e);
                        }
                    }
                }
            } else {
                wasNull = true;
            }
        }

        Logger.trace(new StringBuilder("Return value: raw=")
                .append(rawValue).append(", converted=").append(result)
                .toString());

        return result;
    }

    @Override
    protected boolean hasMore() {
        return _resultSet != null && !_resultSet.isExhausted();
    }

    @Override
    protected <T> void setValue(int columnIndex, T value) throws SQLException {
        throw CassandraErrors.notSupportedException();
    }

    @Override
    protected SQLException tryClose() {
        if (_resultSet != null) {
            _resultSet = null;
            _currentRow = null;
        }

        return null;
    }

    @Override
    protected boolean tryIterate() throws SQLException {
        boolean result = false;
        if (_resultSet != null) {
            try {
                _currentRow = _resultSet.one();
            } catch (Exception e) {
                throw new SQLException(e);
            }

            result = _currentRow != null;
        }

        return result;
    }

    @Override
    protected boolean tryMoveTo(int rows, boolean relativeIndex)
            throws SQLException {
        throw CassandraErrors.notSupportedException();
    }

    @Override
    protected Object unwrap() {
        return _resultSet;
    }
}
