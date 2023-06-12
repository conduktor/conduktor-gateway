package io.conduktor.gateway.interceptor;

import lombok.Data;

import java.util.concurrent.ConcurrentLinkedQueue;

@Data
public class InterceptorLog {

    private final ConcurrentLinkedQueue<Object> events = new ConcurrentLinkedQueue<>();

    public void addLog(Object event) {
        events.add(event);
    }
}
