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

package io.temporal.internal.external;

import com.google.protobuf.ByteString;
import com.uber.m3.tally.Scope;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.client.ActivityCancelledException;
import io.temporal.client.ActivityCompletionFailureException;
import io.temporal.client.ActivityNotExistsException;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.v1.Payloads;
import io.temporal.common.v1.WorkflowExecution;
import io.temporal.failure.CanceledFailure;
import io.temporal.failure.FailureConverter;
import io.temporal.internal.common.GrpcRetryer;
import io.temporal.internal.common.OptionsUtils;
import io.temporal.internal.metrics.MetricsType;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.workflowservice.v1.RecordActivityTaskHeartbeatByIdRequest;
import io.temporal.workflowservice.v1.RecordActivityTaskHeartbeatByIdResponse;
import io.temporal.workflowservice.v1.RecordActivityTaskHeartbeatRequest;
import io.temporal.workflowservice.v1.RecordActivityTaskHeartbeatResponse;
import io.temporal.workflowservice.v1.RespondActivityTaskCanceledByIdRequest;
import io.temporal.workflowservice.v1.RespondActivityTaskCanceledRequest;
import io.temporal.workflowservice.v1.RespondActivityTaskCompletedByIdRequest;
import io.temporal.workflowservice.v1.RespondActivityTaskCompletedRequest;
import io.temporal.workflowservice.v1.RespondActivityTaskFailedByIdRequest;
import io.temporal.workflowservice.v1.RespondActivityTaskFailedRequest;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: service call retries
class ManualActivityCompletionClientImpl implements ManualActivityCompletionClient {

  private static final Logger log =
      LoggerFactory.getLogger(ManualActivityCompletionClientImpl.class);

  private final WorkflowServiceStubs service;

  private final byte[] taskToken;

  private final DataConverter dataConverter;
  private final String namespace;
  private final WorkflowExecution execution;
  private final String activityId;
  private final Scope metricsScope;

  ManualActivityCompletionClientImpl(
      WorkflowServiceStubs service,
      byte[] taskToken,
      DataConverter dataConverter,
      Scope metricsScope) {
    this.service = service;
    this.taskToken = taskToken;
    this.dataConverter = dataConverter;
    this.namespace = null;
    this.execution = null;
    this.activityId = null;
    this.metricsScope = metricsScope;
  }

  ManualActivityCompletionClientImpl(
      WorkflowServiceStubs service,
      String namespace,
      WorkflowExecution execution,
      String activityId,
      DataConverter dataConverter,
      Scope metricsScope) {
    this.service = service;
    this.taskToken = null;
    this.namespace = namespace;
    this.execution = execution;
    this.activityId = activityId;
    this.dataConverter = dataConverter;
    this.metricsScope = metricsScope;
  }

  @Override
  public void complete(Object result) {
    Optional<Payloads> convertedResult = dataConverter.toPayloads(result);
    if (taskToken != null) {
      RespondActivityTaskCompletedRequest.Builder request =
          RespondActivityTaskCompletedRequest.newBuilder()
              .setTaskToken(ByteString.copyFrom(taskToken));
      if (convertedResult.isPresent()) {
        request.setResult(convertedResult.get());
      }
      try {
        GrpcRetryer.retry(
            GrpcRetryer.DEFAULT_SERVICE_OPERATION_RETRY_OPTIONS,
            () -> service.blockingStub().respondActivityTaskCompleted(request.build()));
        metricsScope.counter(MetricsType.ACTIVITY_TASK_COMPLETED_COUNTER).inc(1);
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
          throw new ActivityNotExistsException(e);
        }
        throw new ActivityCompletionFailureException(e);
      } catch (Exception e) {
        throw new ActivityCompletionFailureException(e);
      }
    } else {
      if (activityId == null) {
        throw new IllegalArgumentException("Either activity id or task token are required");
      }
      RespondActivityTaskCompletedByIdRequest.Builder request =
          RespondActivityTaskCompletedByIdRequest.newBuilder()
              .setActivityId(activityId)
              .setNamespace(namespace)
              .setWorkflowId(execution.getWorkflowId())
              .setRunId(execution.getRunId());
      if (convertedResult.isPresent()) {
        request.setResult(convertedResult.get());
      }
      try {
        service.blockingStub().respondActivityTaskCompletedById(request.build());
        metricsScope.counter(MetricsType.ACTIVITY_TASK_COMPLETED_BY_ID_COUNTER).inc(1);
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
          throw new ActivityNotExistsException(activityId, e);
        }
        throw new ActivityCompletionFailureException(activityId, e);
      } catch (Exception e) {
        throw new ActivityCompletionFailureException(activityId, e);
      }
    }
  }

  @Override
  public void fail(Throwable exception) {
    if (exception == null) {
      throw new IllegalArgumentException("null exception");
    }
    // When converting failures reason is class name, details are serialized exception.
    if (taskToken != null) {
      RespondActivityTaskFailedRequest request =
          RespondActivityTaskFailedRequest.newBuilder()
              .setFailure(FailureConverter.exceptionToFailure(exception))
              .setTaskToken(ByteString.copyFrom(taskToken))
              .build();
      try {
        GrpcRetryer.retry(
            GrpcRetryer.DEFAULT_SERVICE_OPERATION_RETRY_OPTIONS,
            () -> service.blockingStub().respondActivityTaskFailed(request));
        metricsScope.counter(MetricsType.ACTIVITY_TASK_FAILED_COUNTER).inc(1);
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
          throw new ActivityNotExistsException(e);
        }
        throw new ActivityCompletionFailureException(e);
      } catch (Exception e) {
        throw new ActivityCompletionFailureException(e);
      }
    } else {
      if (activityId == null) {
        throw new IllegalArgumentException("Either activity id or task token are required");
      }
      RespondActivityTaskFailedByIdRequest request =
          RespondActivityTaskFailedByIdRequest.newBuilder()
              .setFailure(FailureConverter.exceptionToFailure(exception))
              .setNamespace(namespace)
              .setWorkflowId(execution.getWorkflowId())
              .setRunId(execution.getRunId())
              .setActivityId(activityId)
              .build();
      try {
        GrpcRetryer.retry(
            GrpcRetryer.DEFAULT_SERVICE_OPERATION_RETRY_OPTIONS,
            () -> service.blockingStub().respondActivityTaskFailedById(request));
        metricsScope.counter(MetricsType.ACTIVITY_TASK_FAILED_BY_ID_COUNTER).inc(1);
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
          throw new ActivityNotExistsException(activityId, e);
        }
        throw new ActivityCompletionFailureException(activityId, e);
      } catch (Exception e) {
        throw new ActivityCompletionFailureException(activityId, e);
      }
    }
  }

  @Override
  public void recordHeartbeat(Object details) throws CanceledFailure {
    Optional<Payloads> convertedDetails = dataConverter.toPayloads(details);
    if (taskToken != null) {
      RecordActivityTaskHeartbeatRequest.Builder request =
          RecordActivityTaskHeartbeatRequest.newBuilder()
              .setTaskToken(ByteString.copyFrom(taskToken));
      if (convertedDetails.isPresent()) {
        request.setDetails(convertedDetails.get());
      }
      RecordActivityTaskHeartbeatResponse status;
      try {
        status = service.blockingStub().recordActivityTaskHeartbeat(request.build());
        if (status.getCancelRequested()) {
          throw new ActivityCancelledException();
        }
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
          throw new ActivityNotExistsException(activityId, e);
        }
        throw new ActivityCompletionFailureException(activityId, e);
      }
    } else {
      if (activityId == null) {
        throw new IllegalArgumentException("Either activity id or task token are required");
      }
      RecordActivityTaskHeartbeatByIdRequest.Builder request =
          RecordActivityTaskHeartbeatByIdRequest.newBuilder()
              .setWorkflowId(execution.getWorkflowId())
              .setRunId(execution.getRunId())
              .setActivityId(activityId);
      if (convertedDetails.isPresent()) {
        request.setDetails(convertedDetails.get());
      }
      RecordActivityTaskHeartbeatByIdResponse status = null;
      try {
        status = service.blockingStub().recordActivityTaskHeartbeatById(request.build());
        if (status.getCancelRequested()) {
          throw new ActivityCancelledException();
        }
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
          throw new ActivityNotExistsException(activityId, e);
        }
        throw new ActivityCompletionFailureException(activityId, e);
      } catch (Exception e) {
        throw new ActivityCompletionFailureException(activityId, e);
      }
    }
  }

  @Override
  public void reportCancellation(Object details) {
    Optional<Payloads> convertedDetails = dataConverter.toPayloads(details);
    if (taskToken != null) {
      RespondActivityTaskCanceledRequest.Builder request =
          RespondActivityTaskCanceledRequest.newBuilder()
              .setTaskToken(ByteString.copyFrom(taskToken));
      if (convertedDetails.isPresent()) {
        request.setDetails(convertedDetails.get());
      }
      try {
        GrpcRetryer.retry(
            GrpcRetryer.DEFAULT_SERVICE_OPERATION_RETRY_OPTIONS,
            () -> service.blockingStub().respondActivityTaskCanceled(request.build()));
        metricsScope.counter(MetricsType.ACTIVITY_TASK_CANCELED_COUNTER).inc(1);
      } catch (Exception e) {
        // There is nothing that can be done at this point.
        // so let's just ignore.
        log.info("reportCancellation", e);
      }
    } else {
      if (activityId == null) {
        throw new IllegalArgumentException("Either activity id or task token are required");
      }
      RespondActivityTaskCanceledByIdRequest.Builder request =
          RespondActivityTaskCanceledByIdRequest.newBuilder()
              .setNamespace(namespace)
              .setWorkflowId(execution.getWorkflowId())
              .setRunId(OptionsUtils.safeGet(execution.getRunId()))
              .setActivityId(activityId);
      if (convertedDetails.isPresent()) {
        request.setDetails(convertedDetails.get());
      }
      try {
        GrpcRetryer.retry(
            GrpcRetryer.DEFAULT_SERVICE_OPERATION_RETRY_OPTIONS,
            () -> service.blockingStub().respondActivityTaskCanceledById(request.build()));
        metricsScope.counter(MetricsType.ACTIVITY_TASK_CANCELED_BY_ID_COUNTER).inc(1);
      } catch (Exception e) {
        // There is nothing that can be done at this point.
        // so let's just ignore.
        log.warn("reportCancellation", e);
      }
    }
  }
}
