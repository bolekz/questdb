/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2020 QuestDB
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

import io.questdb.mp.SOCountDownLatch;
import io.questdb.std.Os;
import io.questdb.std.str.Path;
import io.questdb.std.str.StringSink;
import io.questdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;

import static io.questdb.cairo.TxnScoreboard.*;

public class TxnScoreboardTest {

    @BeforeClass
    public static void setUp() throws Exception {
        Os.init();
    }

    @Test
    public void testLimits() {
        try (final Path shmPath = new Path()) {
            int expect = 4096;
            long p2 = TxnScoreboard.create(shmPath, 4, 5, "hello");
            try {
                long p1 = TxnScoreboard.create(shmPath, 4, 5, "hello");
                try {
                    init(p1, 125);
                    // we should successfully acquire expected number of entries
                    for (int i = 0; i < expect; i++) {
                        Assert.assertTrue(TxnScoreboard.acquire(p1, i + 134));
                    }
                    // scoreboard capacity should be exhausted
                    // and we should be refused to acquire any more slots
                    Assert.assertFalse(TxnScoreboard.acquire(p1, expect + 134));

                    // now we release middle slot, this does not free any more slots
                    TxnScoreboard.release(p1, 11 + 134);
                    Assert.assertEquals(134, TxnScoreboard.getMin(p1));
                    // we should NOT be able to allocate more slots
                    Assert.assertFalse(TxnScoreboard.acquire(p1, expect + 134));

                    // now that we release "head" slot we should be able to acquire more
                    TxnScoreboard.release(p1, 134);
                    Assert.assertEquals(135, TxnScoreboard.getMin(p1));
                    // and we should be able to allocate another one
                    Assert.assertTrue(TxnScoreboard.acquire(p1, expect + 134));

                    // now check that all counts are intact
                    for (int i = 1; i <= expect; i++) {
                        if (i != 11) {
                            Assert.assertEquals(1, TxnScoreboard.getCount(p1, i + 134));
                        } else {
                            Assert.assertEquals(0, TxnScoreboard.getCount(p1, i + 134));
                        }
                    }
                    System.out.println("done");
                } finally {
                    TxnScoreboard.close(p1);
                }
            } finally {
                // now check that all counts are available via another memory space
                for (int i = 1; i <= expect; i++) {
                    if (i != 11) {
                        Assert.assertEquals(1, TxnScoreboard.getCount(p2, i + 134));
                    } else {
                        Assert.assertEquals(0, TxnScoreboard.getCount(p2, i + 134));
                    }
                }
            }
        }
    }

    @Test
    public void testNameLimit() {
        try (final Path shmPath = new Path()) {
            StringSink name = new StringSink();
            for (int i = 0; i < 255; i++) {
                name.put('a');
            }
            final long p = TxnScoreboard.create(shmPath, 4, 5, name);
            Assert.assertEquals(0, p);
        }
    }

    @Test
    @Ignore
    public void testNewRefBetweenFastOpenClose() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            AtomicLong ref = new AtomicLong();
            try (Path path = new Path()) {
                for (int i = 0; i < 100; i++) {
                    ref.set(0);
                    CyclicBarrier barrier = new CyclicBarrier(2);
                    SOCountDownLatch haltLatch = new SOCountDownLatch(1);
                    new Thread(() -> {
                        try {
                            barrier.await();
                            long p = TxnScoreboard.create(path, 0, 0, "test");
                            ref.set(p);
                            long val = TxnScoreboard.newRef(p);
                            if (val != 0) {
                                TxnScoreboard.close(val);
                            }
                            haltLatch.countDown();
                        } catch (InterruptedException | BrokenBarrierException e) {
                            e.printStackTrace();
                        }
                    }).start();

                    barrier.await();
                    long val;
                    while ((val = ref.get()) == 0) ;
                    TxnScoreboard.close(val);
                    haltLatch.await();
                }
            }
        });
    }

    @Test
    public void testUtf8Name() {
        try (final Path shmPath = new Path()) {
            long p = TxnScoreboard.create(shmPath, 4, 5, "бункера");
            Assert.assertNotEquals(0, p);
            TxnScoreboard.close(p);
        }
    }

    @Test
    public void testVanilla() {
        try (final Path shmPath = new Path()) {
            long p2 = TxnScoreboard.create(shmPath, 4, 5, "tab1");
            try {
                long p1 = TxnScoreboard.create(shmPath, 4, 5, "tab1");
                try {
                    init(p1, 55);
                    Assert.assertTrue(acquire(p1, 67));
                    Assert.assertTrue(acquire(p1, 68));
                    Assert.assertTrue(acquire(p1, 68));
                    Assert.assertTrue(acquire(p1, 69));
                    Assert.assertTrue(acquire(p1, 70));
                    Assert.assertTrue(acquire(p1, 71));

                    release(p1, 68);
                    Assert.assertEquals(67, getMin(p1));
                    release(p1, 68);
                    Assert.assertEquals(67, getMin(p1));
                    release(p1, 67);
                    Assert.assertEquals(69, getMin(p1));

                    release(p1, 69);
                    Assert.assertEquals(70, getMin(p1));
                    release(p1, 71);
                    Assert.assertEquals(70, getMin(p1));
                    release(p1, 70);
                    Assert.assertEquals(71, getMin(p1));

                    Assert.assertTrue(acquire(p1, 72));
                } finally {
                    TxnScoreboard.close(p1);
                }
                Assert.assertTrue(acquire(p2, 72));
                Assert.assertEquals(2, TxnScoreboard.getCount(p2, 72));
            } finally {
                TxnScoreboard.close(p2);
            }
        }
    }
}