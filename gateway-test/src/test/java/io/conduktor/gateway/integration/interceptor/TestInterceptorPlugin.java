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

package io.conduktor.gateway.integration.interceptor;

import io.conduktor.gateway.interceptor.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.requests.AbstractRequestResponse;
import org.apache.kafka.common.requests.FetchRequest;
import org.apache.kafka.common.requests.FetchResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class TestInterceptorPlugin implements Plugin {

    @SuppressWarnings("rawtypes")
    @Override
    public List<InterceptorProvider<?>> getInterceptors(Map<String, Object> config) {
        String prefix = "";
        var loggingStyle = config.get("loggingStyle");
        if (loggingStyle.equals("obiWan")) {
            prefix = "Hello there";
        }
        return List.of(
                new InterceptorProvider<>(AbstractRequestResponse.class, new AllLoggerInterceptor(prefix)),
                new InterceptorProvider<>(FetchRequest.class, new FetchRequestLoggerInterceptor())
        );
    }

    @Slf4j
    public static class AllLoggerInterceptor implements Interceptor<AbstractRequestResponse> {

        private final String prefix;

        public AllLoggerInterceptor(String prefix) {
            this.prefix = prefix;
        }
        @Override
        public CompletionStage<AbstractRequestResponse> intercept(AbstractRequestResponse input, InterceptorContext interceptorContext, InterceptorTools interceptorTools) {
            log.warn("{}, a {} was sent/received", prefix, input.getClass());
            return CompletableFuture.completedFuture(input);
        }

    }

    @Slf4j
    public static class FetchRequestLoggerInterceptor implements Interceptor<FetchRequest> {
        @Override
        public CompletionStage<FetchRequest> intercept(FetchRequest input, InterceptorContext interceptorContext, InterceptorTools interceptorTools) {
            var source = interceptorContext.clientAddress().getHostName();
            log.warn("Fetch was requested from {}", source);
            interceptorContext.inFlightInfo().put("source", source);
            return CompletableFuture.completedFuture(input);
        }
    }

    @Slf4j
    public static class FetchResponseLoggerInterceptor implements Interceptor<FetchResponse> {
        @Override
        public CompletionStage<FetchResponse> intercept(FetchResponse input, InterceptorContext interceptorContext, InterceptorTools interceptorTools) {
            log.warn("Fetch from client {} was responded to", interceptorContext.inFlightInfo().get("source"));
            return CompletableFuture.completedFuture(input);
        }
    }


}
