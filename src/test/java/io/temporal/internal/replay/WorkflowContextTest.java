/*
 *  Copyright (C) 2020 Temporal Technologies, Inc. All Rights Reserved.
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.internal.replay;

import static junit.framework.TestCase.assertEquals;

import io.temporal.common.converter.DataConverter;
import io.temporal.common.v1.Payload;
import io.temporal.common.v1.SearchAttributes;
import io.temporal.history.v1.WorkflowExecutionStartedEventAttributes;
import io.temporal.internal.common.SearchAttributesUtil;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class WorkflowContextTest {

  @Test
  public void TestMergeSearchAttributes() {
    WorkflowExecutionStartedEventAttributes startAttr =
        WorkflowExecutionStartedEventAttributes.getDefaultInstance();
    WorkflowContext workflowContext = new WorkflowContext("namespace", null, startAttr, 0, null);

    DataConverter converter = DataConverter.getDefaultInstance();
    Map<String, Payload> indexedFields = new HashMap<>();
    indexedFields.put("CustomKeywordField", converter.toPayload("key").get());

    SearchAttributes searchAttributes =
        SearchAttributes.newBuilder().putAllIndexedFields(indexedFields).build();

    workflowContext.mergeSearchAttributes(searchAttributes);

    assertEquals(
        "key",
        SearchAttributesUtil.getValueFromSearchAttributes(
            workflowContext.getSearchAttributes(), "CustomKeywordField", String.class));
  }
}
