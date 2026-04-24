package com.airtribe.chronos.execution.handler;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskHandlerRegistry {
    private final Map<String, TaskHandler> handlers = new HashMap<>();

    public TaskHandlerRegistry(List<TaskHandler> handlerBeans) {
        handlerBeans.forEach(h -> handlers.put(h.taskType(), h));
    }

    public TaskHandler handlerFor(String taskType) {
        TaskHandler h = handlers.get(taskType);
        if (h == null) {
            throw new IllegalArgumentException("No handler registered for task type: " + taskType);
        }
        return h;
    }
}
