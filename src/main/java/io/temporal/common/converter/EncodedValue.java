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

package io.temporal.common.converter;

import io.temporal.common.v1.Payloads;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;

public final class EncodedValue implements Value {
  private Optional<Payloads> payloads;
  private DataConverter converter;
  private final Optional<Object> value;

  public EncodedValue(Optional<Payloads> payloads, DataConverter converter) {
    this.payloads = Objects.requireNonNull(payloads);
    this.converter = converter;
    this.value = null;
  }

  public <T> EncodedValue(T value) {
    this.value = Optional.ofNullable(value);
    this.payloads = null;
  }

  public Optional<Payloads> toPayloads() {
    if (payloads == null) {
      if (converter == null) {
        throw new IllegalStateException("converter not set");
      }
      payloads = value.isPresent() ? converter.toPayloads(value.get()) : Optional.empty();
    }
    return payloads;
  }

  public void setDataConverter(DataConverter converter) {
    this.converter = Objects.requireNonNull(converter);
  }

  @Override
  public <T> T get(Class<T> parameterType) throws DataConverterException {
    if (value != null) {
      @SuppressWarnings("unchecked")
      T result = (T) value.orElse(null);
      return result;
    } else {
      if (converter == null) {
        throw new IllegalStateException("converter not set");
      }
      return converter.fromPayloads(payloads, parameterType, parameterType);
    }
  }

  @Override
  public <T> T get(Class<T> parameterType, Type genericParameterType)
      throws DataConverterException {
    return converter.fromPayloads(payloads, parameterType, genericParameterType);
  }
}
