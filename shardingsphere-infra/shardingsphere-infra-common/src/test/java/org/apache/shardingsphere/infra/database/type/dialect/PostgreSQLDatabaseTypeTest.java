/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.database.type.dialect;

import org.apache.shardingsphere.infra.database.metadata.dialect.PostgreSQLDataSourceMetaData;
import org.apache.shardingsphere.sql.parser.sql.common.constant.QuoteCharacter;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class PostgreSQLDatabaseTypeTest {
    
    @Test
    public void assertGetName() {
        assertThat(new PostgreSQLDatabaseType().getName(), is("PostgreSQL"));
    }
    
    @Test
    public void assertGetJdbcUrlPrefixes() {
        assertThat(new PostgreSQLDatabaseType().getJdbcUrlPrefixes(), is(Collections.singleton("jdbc:postgresql:")));
    }
    
    @Test
    public void assertGetDataSourceMetaData() {
        assertThat(new PostgreSQLDatabaseType().getDataSourceMetaData("jdbc:postgresql://localhost:5432/demo_ds_0", "postgres"), instanceOf(PostgreSQLDataSourceMetaData.class));
    }
    
    @Test
    public void assertGetSchema() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn("ds");
        assertThat(new PostgreSQLDatabaseType().getSchema(connection), is("ds"));
    }
    
    @Test
    public void assertFormatTableNamePattern() {
        assertThat(new PostgreSQLDatabaseType().formatTableNamePattern("tbl"), is("tbl"));
    }
    
    @Test
    public void assertGetQuoteCharacter() {
        QuoteCharacter actual = new PostgreSQLDatabaseType().getQuoteCharacter();
        assertThat(actual.getStartDelimiter(), is("\""));
        assertThat(actual.getEndDelimiter(), is("\""));
    }
    
    @Test
    public void assertContainsSystemSchema() {
        assertTrue(new PostgreSQLDatabaseType().containsSystemSchema("information_schema"));
        assertTrue(new PostgreSQLDatabaseType().containsSystemSchema("pg_catalog"));
        assertFalse(new MySQLDatabaseType().containsSystemSchema("postgres"));
        assertFalse(new MySQLDatabaseType().containsSystemSchema("sharding_db"));
    }
}
