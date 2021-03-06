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

package io.temporal.internal.common;

import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DataConverterException;
import io.temporal.common.v1.Header;
import io.temporal.common.v1.Payload;
import java.util.HashMap;
import java.util.Map;

public class HeaderUtils {

  public static Header toHeaderGrpc(Map<String, Payload> headers) {
    if (headers == null || headers.isEmpty()) {
      return null;
    }
    Header.Builder builder = Header.newBuilder();
    for (Map.Entry<String, Payload> item : headers.entrySet()) {
      builder.putFields(item.getKey(), item.getValue());
    }
    return builder.build();
  }

  public static Map<String, Payload> convertMapFromObjectToBytes(
      Map<String, Object> map, DataConverter dataConverter) {
    if (map == null) {
      return null;
    }
    Map<String, Payload> result = new HashMap<>();
    for (Map.Entry<String, Object> item : map.entrySet()) {
      try {
        result.put(item.getKey(), dataConverter.toPayload(item.getValue()).get());
      } catch (DataConverterException e) {
        throw new DataConverterException("Cannot serialize key " + item.getKey(), e.getCause());
      }
    }
    return result;
  }

  private HeaderUtils() {}
}
