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

import com.google.inject.AbstractModule;
import io.conduktor.gateway.config.GatewayConfiguration;
import io.conduktor.gateway.config.InterceptorConfigEntry;
import io.conduktor.gateway.config.InterceptorPluginConfig;
import io.conduktor.gateway.integration.BaseGatewayIntegrationTest;
import io.conduktor.gateway.interceptor.Plugin;
import io.conduktor.gateway.service.PluginLoader;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.requests.AbstractRequestResponse;
import org.apache.kafka.common.requests.AbstractResponse;
import org.apache.kafka.common.requests.FetchRequest;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.time.Duration;
import java.util.*;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggerInterceptorPluginIntegrationTest extends BaseGatewayIntegrationTest {

    public static final String SOME_KEY = "someKey";
    public static final String SOME_VALUE = "someValue";
    public static final String LOGGERINTERCEPTOR_PACKAGE = "io.conduktor.gateway.integration.interceptor";

    @Override
    protected AbstractModule mockGuiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(PluginLoader.class).toInstance(() -> List.of(new TestInterceptorPlugin()));
            }
        };
    }

    @Override
    protected void reconfigureGateway(GatewayConfiguration gatewayConfiguration) {
        gatewayConfiguration
                .setInterceptors(List.of(
                        new InterceptorPluginConfig(
                                "testInterceptor",
                                "io.conduktor.gateway.integration.interceptor.TestInterceptorPlugin",
                                100,
                                List.of(
                                        new InterceptorConfigEntry("loggingStyle", "obiWan")
                                )
                        )));
    }

    @Test
    @Tag("IntegrationTest")
    public void testLoadsInterceptors() {
        assertThat(getInterceptorPoolService().getAllInterceptors(FetchRequest.class).size())
                .isEqualTo(2);
        assertThat(getInterceptorPoolService().getAllInterceptors(AbstractRequestResponse.class).size())
                .isEqualTo(1);
        assertThat(getInterceptorPoolService().getAllInterceptors(AbstractResponse.class).size())
                .isEqualTo(1);
    }

    @Test
    @Tag("IntegrationTest")
    public void testInterceptsProduceMessages() throws ExecutionException, InterruptedException {

        var clientTopic = createTopic(clientFactory.kafkaAdmin(), 1, (short) 1);

        var logStream = new ByteArrayOutputStream();
        var logWriter = new PrintWriter(logStream);
        addAppender(logWriter, "testInterceptsProduceMessages");
        Configurator.setLevel(LOGGERINTERCEPTOR_PACKAGE, Level.DEBUG);

        try (var gatewayProducer = clientFactory.gatewayProducer()) {
            ProducerRecord<String, String> record1 = new ProducerRecord<>(clientTopic, SOME_KEY, SOME_VALUE);
            gatewayProducer.send(record1).get();
        }

        logWriter.flush();
        // global interceptor
        assertThat(logStream.toString()
                .contains("Hello there, a class org.apache.kafka.common.requests.ProduceResponse was sent/received"))
                .isEqualTo(true);
    }

    @Test
    @Tag("IntegrationTest")
    public void testInterceptsFetchMessages() throws ExecutionException, InterruptedException {

        var clientTopic = createTopic(clientFactory.kafkaAdmin(), 1, (short) 1);

        var logStream = new ByteArrayOutputStream();
        var logWriter = new PrintWriter(logStream);
        addAppender(logWriter, "testInterceptsFetchMessages");
        Configurator.setLevel(LOGGERINTERCEPTOR_PACKAGE, Level.DEBUG);

        try (var gatewayConsumer = clientFactory.gatewayConsumer(getGroup())) {
            gatewayConsumer.subscribe(Collections.singletonList(clientTopic));
            IntStream.range(0, 100).forEach(index ->
                    gatewayConsumer.poll(Duration.ofMillis(100)));
        }

        logWriter.flush();
        // global interceptor
        assertThat(logStream.toString()
                .contains("Hello there, a class org.apache.kafka.common.requests.LeaveGroupResponse was sent/received"))
                .isEqualTo(true);
        // fetch interceptor
        assertThat(logStream.toString()
                .contains("Fetch was requested from localhost"))
                .isEqualTo(true);
    }

    void addAppender(final Writer writer, final String writerName) {
        final LoggerContext context = LoggerContext.getContext(false);
        final Configuration config = context.getConfiguration();
        final PatternLayout layout = PatternLayout.createDefaultLayout(config);
        final Appender appender = WriterAppender.createAppender(layout, null, writer, writerName, false, true);
        appender.start();
        config.addAppender(appender);
        for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
            loggerConfig.addAppender(appender, null, null);
        }
        config.getRootLogger().addAppender(appender, null, null);
    }

    private String getGroup() {
        return "someGroup" + UUID.randomUUID();
    }

}