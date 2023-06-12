/*
 * Copyright 2023 Conduktor, Inc
 *
 * Licensed under the Conduktor Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * https://www.conduktor.io/conduktor-community-license-agreement-v1.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.conduktor.example.loggerinterceptor;

import io.conduktor.gateway.interceptor.Interceptor;
import io.conduktor.gateway.interceptor.InterceptorContext;
import io.conduktor.gateway.interceptor.InterceptorTools;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.requests.FetchRequest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Slf4j
public class FetchRequestLoggerInterceptor implements Interceptor<FetchRequest> {
    @Override
    public CompletionStage<FetchRequest> intercept(FetchRequest input, InterceptorContext interceptorContext, InterceptorTools interceptorTools) {
        var source = interceptorContext.clientAddress().getHostName();
        log.warn("Fetch was requested from {}", source);
        interceptorContext.inFlightInfo().put("source", source);
        return CompletableFuture.completedFuture(input);
    }
}