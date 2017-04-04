/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.persistent;

import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry.Entry;
import org.elasticsearch.xpack.persistent.CreatePersistentTaskAction.Request;
import org.elasticsearch.xpack.persistent.TestPersistentTasksPlugin.TestPersistentTasksExecutor;
import org.elasticsearch.xpack.persistent.TestPersistentTasksPlugin.TestRequest;
import org.elasticsearch.test.AbstractStreamableTestCase;

import java.util.Collections;

public class StartPersistentActionRequestTests extends AbstractStreamableTestCase<Request> {

    @Override
    protected Request createTestInstance() {
        TestRequest testRequest = new TestRequest();
        if (randomBoolean()) {
            testRequest.setTestParam(randomAlphaOfLengthBetween(1, 20));
        }
        if (randomBoolean()) {
            testRequest.setParentTask(randomAlphaOfLengthBetween(1, 20), randomLong());
        }
        if (randomBoolean()) {
            testRequest.setExecutorNodeAttr(randomAlphaOfLengthBetween(1, 20));
        }
        return new Request(randomAlphaOfLengthBetween(1, 20), new TestRequest());
    }

    @Override
    protected Request createBlankInstance() {
        return new Request();
    }

    @Override
    protected NamedWriteableRegistry getNamedWriteableRegistry() {
        return new NamedWriteableRegistry(Collections.singletonList(
                new Entry(PersistentTaskRequest.class, TestPersistentTasksExecutor.NAME, TestRequest::new)
        ));
    }
}