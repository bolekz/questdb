/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2022 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.cairo;

import io.questdb.griffin.AbstractGriffinTest;
import org.junit.Test;

/**
 * This class tests row skipping (in descending order) optimizations for tables with designated timestamps,
 * both un- and partitioned .
 * Sibling test of AscendingOrderRowSkippingTest.
 */
public class DescendingOrderRowSkippingTest extends AbstractGriffinTest {

    //empty table
    @Test
    public void test_emptyTable_select_all_returns_no_rows() throws Exception {
        create_empty_table();

        assertQuery("l\n", "select l from tab order by ts desc");
    }

    @Test
    public void test_emptyTable_select_first_N_returns_no_rows() throws Exception {
        create_empty_table();

        assertQuery("l\n", "select l from tab order by ts desc limit 3");
    }

    @Test
    public void test_emptyTable_select_middle_N_from_start_returns_no_rows() throws Exception {
        create_empty_table();

        assertQuery("l\n", "select l from tab order by ts desc limit 5,8");
    }


    @Test
    public void test_emptyTable_select_N_beyond_end_returns_empty_result() throws Exception {
        create_empty_table();

        assertQuery("l\n", "select l from tab order by ts desc limit 11,12");
    }

    @Test
    public void test_emptyTable_select_N_before_start_returns_empty_result() throws Exception {
        create_empty_table();

        assertQuery("l\n", "select l from tab order by ts desc limit -11,-15");
    }

    @Test
    public void test_emptyTable_select_N_intersecting_end_returns_empty_result() throws Exception {
        create_empty_table();

        assertQuery("l\n", "select l from tab order by ts desc limit 8,12");
    }

    @Test
    public void test_emptyTable_select_N_intersecting_start_returns_empty_result() throws Exception {
        create_empty_table();

        assertQuery("l\n", "select l from tab order by ts desc limit -12,-8");
    }

    @Test
    public void test_emptyTable_select_last_N_returns_no_rows() throws Exception {
        create_empty_table();

        assertQuery("l\n", "select l from tab order by ts desc limit -3");
    }

    @Test
    public void test_emptyTable_select_middle_N_from_end_returns_no_rows() throws Exception {
        create_empty_table();

        assertQuery("l\n", "select l from tab order by ts desc limit -8,-5");
    }

    @Test
    public void test_emptyTable_select_middle_N_from_both_directions() throws Exception {
        create_empty_table();

        assertQuery("l\n", "select l from tab order by ts desc limit 4,-4");
    }

    @Test
    public void test_emptyTable_select_first_N_with_same_lo_hi_returns_no_rows() throws Exception {
        create_empty_table();

        assertQuery("l\n", "select l from tab order by ts desc limit 8,8");
    }

    @Test
    public void test_emptyTable_select_last_N_with_same_lo_hi_returns_no_rows() throws Exception {
        create_empty_table();

        assertQuery("l\n", "select l from tab order by ts desc limit -8,-8");
    }

    private void create_empty_table() throws Exception {
        runQueries("CREATE TABLE tab(l long, ts TIMESTAMP) timestamp(ts);");
    }

    //regular table with one partition
    @Test
    public void test_normalTable_select_all() throws Exception {
        prepare_normal_table();

        assertQuery("l\n10\n9\n8\n7\n6\n5\n4\n3\n2\n1\n", "select l from tab order by ts desc");
    }

    @Test
    public void test_normalTable_select_first_N() throws Exception {
        prepare_normal_table();

        assertQuery("l\n10\n9\n8\n", "select l from tab order by ts desc limit 3");
    }

    @Test
    public void test_normalTable_select_middle_N_from_start() throws Exception {
        prepare_normal_table();

        assertQuery("l\n5\n4\n3\n", "select l from tab order by ts desc limit 5,8");
    }

    @Test
    public void test_normalTable_select_N_beyond_end_returns_empty_result() throws Exception {
        prepare_normal_table();

        assertQuery("l\n", "select l from tab order by ts desc limit 11,12");
    }

    @Test
    public void test_normalTable_select_N_before_start_returns_empty_result() throws Exception {
        prepare_normal_table();

        assertQuery("l\n", "select l from tab order by ts desc limit -11,-15");
    }

    @Test
    public void test_normalTable_select_last_N() throws Exception {
        prepare_normal_table();

        assertQuery("l\n3\n2\n1\n", "select l from tab order by ts desc limit -3");
    }

    @Test
    public void test_normalTable_select_middle_N_from_end() throws Exception {
        prepare_normal_table();

        assertQuery("l\n8\n7\n6\n", "select l from tab order by ts desc limit -8,-5");
    }

    @Test
    public void test_normalTable_select_middle_N_from_both_directions() throws Exception {
        prepare_normal_table();

        assertQuery("l\n6\n5\n", "select l from tab order by ts desc limit 4,-4");
    }

    @Test
    public void test_normalTable_select_first_N_with_same_lo_hi_returns_no_rows() throws Exception {
        prepare_normal_table();

        assertQuery("l\n", "select l from tab order by ts desc limit 8,8");
    }

    @Test
    public void test_normalTable_select_last_N_with_same_lo_hi_returns_no_rows() throws Exception {
        prepare_normal_table();

        assertQuery("l\n", "select l from tab order by ts desc limit -8,-8");
    }

    @Test
    public void test_normalTable_select_N_intersecting_end() throws Exception {
        prepare_normal_table();

        assertQuery("l\n2\n1\n", "select l from tab order by ts desc limit 8,12");
    }

    @Test
    public void test_normalTable_select_N_intersecting_start() throws Exception {
        prepare_normal_table();

        assertQuery("l\n10\n9\n", "select l from tab order by ts desc limit -12,-8");
    }

    private void prepare_normal_table() throws Exception {
        runQueries("CREATE TABLE tab(l long, ts TIMESTAMP) timestamp(ts);",
                "insert into tab " +
                        "  select x," +
                        "  timestamp_sequence(to_timestamp('2022-01-03T00:00:00', 'yyyy-MM-ddTHH:mm:ss'), 1000000) " +
                        "  from long_sequence(10);");
    }

    //partitionedTable with two partitions, 5 rows per partition
    @Test
    public void test_2partitions_select_all() throws Exception {
        prepare_2partitions_table();

        assertQuery("l\n10\n9\n8\n7\n6\n5\n4\n3\n2\n1\n", "select l from tab order by ts desc");
    }

    @Test
    public void test_2partitions_select_first_N() throws Exception {
        prepare_2partitions_table();

        assertQuery("l\n10\n9\n8\n", "select l from tab order by ts desc limit 3");
    }

    @Test
    public void test_2partitions_select_middle_N_from_start() throws Exception {
        prepare_2partitions_table();

        assertQuery("l\n5\n4\n3\n", "select l from tab order by ts desc limit 5,8");
    }

    @Test
    public void test_2partitions_select_N_beyond_end_returns_empty_result() throws Exception {
        prepare_2partitions_table();

        assertQuery("l\n", "select l from tab order by ts desc limit 11,12");
    }

    @Test
    public void test_2partitions_select_N_before_start_returns_empty_result() throws Exception {
        prepare_2partitions_table();

        assertQuery("l\n", "select l from tab order by ts desc limit -11,-15");
    }

    @Test
    public void test_2partitions_select_last_N() throws Exception {
        prepare_2partitions_table();

        assertQuery("l\n3\n2\n1\n", "select l from tab order by ts desc limit -3");
    }

    @Test
    public void test_2partitions_select_middle_N_from_end() throws Exception {
        prepare_2partitions_table();

        assertQuery("l\n8\n7\n6\n", "select l from tab order by ts desc limit -8,-5");
    }

    @Test
    public void test_2partitions_select_middle_N_from_both_directions() throws Exception {
        prepare_2partitions_table();

        assertQuery("l\n6\n5\n", "select l from tab order by ts desc limit 4,-4");
    }

    @Test
    public void test_2partitions_select_first_N_with_same_lo_hi_returns_no_rows() throws Exception {
        prepare_2partitions_table();

        assertQuery("l\n", "select l from tab order by ts desc limit 8,8");
    }

    @Test
    public void test_2partitions_select_last_N_with_same_lo_hi_returns_no_rows() throws Exception {
        prepare_2partitions_table();

        assertQuery("l\n", "select l from tab order by ts desc limit -8,-8");
    }

    @Test
    public void test_2partitions_select_N_intersecting_end() throws Exception {
        prepare_2partitions_table();

        assertQuery("l\n2\n1\n", "select l from tab order by ts desc limit 8,12");
    }

    @Test
    public void test_2partitions_select_N_intersecting_start() throws Exception {
        prepare_2partitions_table();

        assertQuery("l\n10\n9\n", "select l from tab order by ts desc limit -12,-8");
    }

    private void prepare_2partitions_table() throws Exception {
        runQueries("CREATE TABLE tab(l long, ts TIMESTAMP) timestamp(ts) partition by day;",
                "insert into tab " +
                        "  select x," +
                        "  timestamp_sequence(to_timestamp('2022-01-03T00:00:00', 'yyyy-MM-ddTHH:mm:ss'), 17280000000) " +
                        "  from long_sequence(10);");
    }

    //partitioned table with one row per partition
    @Test
    public void test_partitionPerRow_select_all() throws Exception {
        prepare_partitionPerRow_table();

        assertQuery("l\n10\n9\n8\n7\n6\n5\n4\n3\n2\n1\n", "select l from tab order by ts desc");
    }

    @Test
    public void test_partitionPerRow_select_first_N() throws Exception {
        prepare_partitionPerRow_table();

        assertQuery("l\n10\n9\n8\n", "select l from tab order by ts desc limit 3");
    }

    @Test
    public void test_partitionPerRow_select_middle_N_from_start() throws Exception {
        prepare_partitionPerRow_table();

        assertQuery("l\n5\n4\n3\n", "select l from tab order by ts desc limit 5,8");
    }

    @Test
    public void test_partitionPerRow_select_N_beyond_end_returns_empty_result() throws Exception {
        prepare_partitionPerRow_table();

        assertQuery("l\n", "select l from tab order by ts desc limit 11,12");
    }

    @Test
    public void test_partitionPerRow_select_N_before_start_returns_empty_result() throws Exception {
        prepare_partitionPerRow_table();

        assertQuery("l\n", "select l from tab order by ts desc limit -11,-15");
    }

    @Test
    public void test_partitionPerRow_select_last_N() throws Exception {
        prepare_partitionPerRow_table();

        assertQuery("l\n3\n2\n1\n", "select l from tab order by ts desc limit -3");
    }

    @Test
    public void test_partitionPerRow_select_middle_N_from_end() throws Exception {
        prepare_partitionPerRow_table();

        assertQuery("l\n8\n7\n6\n", "select l from tab order by ts desc limit -8,-5");
    }

    @Test
    public void test_partitionPerRow_select_middle_N_from_both_directions() throws Exception {
        prepare_partitionPerRow_table();

        assertQuery("l\n6\n5\n", "select l from tab order by ts desc limit 4,-4");
    }

    @Test
    public void test_partitionPerRow_select_first_N_with_same_lo_hi_returns_no_rows() throws Exception {
        prepare_partitionPerRow_table();

        assertQuery("l\n", "select l from tab order by ts desc limit 8,8");
    }

    @Test
    public void test_partitionPerRow_select_last_N_with_same_lo_hi_returns_no_rows() throws Exception {
        prepare_partitionPerRow_table();

        assertQuery("l\n", "select l from tab order by ts desc limit -8,-8");
    }

    @Test
    public void test_partitionPerRow_select_N_intersecting_end() throws Exception {
        prepare_partitionPerRow_table();

        assertQuery("l\n2\n1\n", "select l from tab order by ts desc limit 8,12");
    }

    @Test
    public void test_partitionPerRow_select_N_intersecting_start() throws Exception {
        prepare_partitionPerRow_table();

        assertQuery("l\n10\n9\n", "select l from tab order by ts desc limit -12,-8");
    }

    private void prepare_partitionPerRow_table() throws Exception {
        runQueries("CREATE TABLE tab(l long, ts TIMESTAMP) timestamp(ts) partition by day;",
                "insert into tab " +
                        "  select x," +
                        "  timestamp_sequence(to_timestamp('2022-01-03T00:00:00', 'yyyy-MM-ddTHH:mm:ss'), 100000000000) " +
                        "  from long_sequence(10);");
    }

    private void runQueries(String... queries) throws Exception {
        assertMemoryLeak(() -> {
            for (String query : queries) {
                compiler.compile(query, sqlExecutionContext);
            }
        });
    }

    private void assertQuery(String expected, String query) throws Exception {
        assertQuery(expected,
                query,
                null, null, true, false, true);
    }
}
