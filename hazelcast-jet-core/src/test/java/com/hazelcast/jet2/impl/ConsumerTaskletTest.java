/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet2.impl;

import com.hazelcast.jet2.Consumer;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(QuickTest.class)
public class ConsumerTaskletTest {

    private List<Integer> list;
    private Map<String, QueueHead<? extends Integer>> inputMap;
    private ListConsumer<Integer> consumer;

    @Before
    public void setup() {
        this.list = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        this.consumer = new ListConsumer<>();
        this.inputMap = new HashMap<>();
    }

    @Test
    public void testSingleChunk_when_singleInput() throws Exception {
        TestQueueHead<Integer> input1 = new TestQueueHead<>(4, list);

        inputMap.put("input1", input1);
        Tasklet tasklet = createTasklet();

        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        assertEquals(Arrays.asList(0, 1, 2, 3), consumer.getList());
        assertFalse("isComplete", consumer.isComplete());

    }

    @Test
    public void testAllChunks_when_singleInput() throws Exception {
        TestQueueHead<Integer> input1 = new TestQueueHead<>(4, list);
        inputMap.put("input1", input1);
        Tasklet tasklet = createTasklet();

        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        assertEquals(TaskletResult.DONE, tasklet.call());

        assertEquals(list, consumer.getList());
        assertTrue("isComplete", consumer.isComplete());

    }

    @Test
    public void testProgress_when_singleInputNotComplete() throws Exception {
        TestQueueHead<Integer> input1 = new TestQueueHead<>(list.size(), list);
        inputMap.put("input1", input1);
        Tasklet tasklet = createTasklet();

        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        assertEquals(list, consumer.getList());
        assertFalse("isComplete", consumer.isComplete());

    }

    @Test
    public void testProgress_when_singleInputNewData() throws Exception {
        TestQueueHead<Integer> input1 = new TestQueueHead<>(list.size(), list);
        inputMap.put("input1", input1);
        Tasklet tasklet = createTasklet();

        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());

        input1.push(10, 11);

        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        assertEquals(TaskletResult.DONE, tasklet.call());

        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), consumer.getList());
        assertTrue("isComplete", consumer.isComplete());
    }

    @Test
    public void testProgress_when_singleInputNoProgress() throws Exception {
        TestQueueHead<Integer> input1 = new TestQueueHead<>(list.size(), list);
        inputMap.put("input1", input1);
        Tasklet tasklet = createTasklet();

        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        input1.pause();
        assertEquals(TaskletResult.NO_PROGRESS, tasklet.call());
        assertFalse("isComplete", consumer.isComplete());
    }

    @Test
    public void testProgress_when_multipleInput() throws Exception {
        TestQueueHead<Integer> input1 = new TestQueueHead<>(list.size(), list);
        TestQueueHead<Integer> input2 = new TestQueueHead<>(list.size(), list);
        inputMap.put("input1", input1);
        inputMap.put("input2", input2);
        Tasklet tasklet = createTasklet();

        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        assertEquals(TaskletResult.DONE, tasklet.call());

        assertEquals(list.size() * 2, consumer.getList().size());
        assertTrue("isComplete", consumer.isComplete());
    }

    @Test
    public void testProgress_when_multipleInput_oneFinishedEarlier() throws Exception {
        TestQueueHead<Integer> input1 = new TestQueueHead<>(2, Arrays.asList(1, 2));
        TestQueueHead<Integer> input2 = new TestQueueHead<>(list.size(), list);
        inputMap.put("input1", input1);
        inputMap.put("input2", input2);
        Tasklet tasklet = createTasklet();

        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        assertEquals(TaskletResult.DONE, tasklet.call());

        assertEquals(12, consumer.getList().size());
        assertTrue("isComplete", consumer.isComplete());
    }


    @Test
    public void testProgress_when_consumerYields() throws Exception {
        TestQueueHead<Integer> input1 = new TestQueueHead<>(10, list);
        inputMap.put("input1", input1);
        Tasklet tasklet = createTasklet();

        consumer.yieldOn(2);

        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        assertEquals(Arrays.asList(0, 1), consumer.getList());
        assertFalse("isComplete", consumer.isComplete());
    }

    @Test
    public void testProgress_when_consumerYieldsOnSameItem() throws Exception {
        TestQueueHead<Integer> input1 = new TestQueueHead<>(10, list);
        inputMap.put("input1", input1);
        Tasklet tasklet = createTasklet();

        consumer.yieldOn(2);
        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        consumer.yieldOn(2);
        assertEquals(TaskletResult.NO_PROGRESS, tasklet.call());
        assertEquals(Arrays.asList(0, 1), consumer.getList());
        assertFalse("isComplete", consumer.isComplete());
    }

    @Test
    public void testProgress_when_consumerYieldsAgain() throws Exception {
        TestQueueHead<Integer> input1 = new TestQueueHead<>(10, list);
        inputMap.put("input1", input1);
        Tasklet tasklet = createTasklet();

        consumer.yieldOn(2);

        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());

        consumer.yieldOn(4);
        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());

        assertEquals(Arrays.asList(0, 1, 2, 3), consumer.getList());

        assertEquals(TaskletResult.DONE, tasklet.call());

        assertEquals(list, consumer.getList());
        assertTrue("isComplete", consumer.isComplete());
    }

    @Test
    public void testProgress_when_consumerYieldsAndThenRuns() throws Exception {
        TestQueueHead<Integer> input1 = new TestQueueHead<>(10, list);
        inputMap.put("input1", input1);
        Tasklet tasklet = createTasklet();

        consumer.yieldOn(2);

        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());
        assertEquals(TaskletResult.DONE, tasklet.call());

        assertEquals(list, consumer.getList());
        assertTrue("isComplete", consumer.isComplete());
    }

    @Test
    public void testProgress_when_consumerYieldsAndNoInput() throws Exception {
        TestQueueHead<Integer> input1 = new TestQueueHead<>(3, list);
        inputMap.put("input1", input1);
        Tasklet tasklet = createTasklet();

        consumer.yieldOn(2);
        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());

        input1.pause();

        assertEquals(TaskletResult.MADE_PROGRESS, tasklet.call());

        assertEquals(Arrays.asList(0, 1, 2), consumer.getList());
        assertFalse("isComplete", consumer.isComplete());
    }

    @Test
    public void testIsBlocking() {
        inputMap.put("input1", new TestQueueHead<>(10, list));
        ConsumerTasklet<Integer> tasklet =
                new ConsumerTasklet<>(new Consumer<Integer>() {
            @Override
            public boolean consume(Integer item) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void complete() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isBlocking() {
                return true;
            }
        }, inputMap);
        assertTrue(tasklet.isBlocking());
    }

    private Tasklet createTasklet() {
        return new ConsumerTasklet<>(consumer, inputMap);
    }
}
