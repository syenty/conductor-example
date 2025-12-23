package com.example.conductor.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "orchestrator")
public class ConductorWorkerProperties {

    private String conductorBaseUrl;
    private String workerId;
    private int threadCount = 4;

    public String getConductorBaseUrl() {
        return conductorBaseUrl;
    }

    public void setConductorBaseUrl(String conductorBaseUrl) {
        this.conductorBaseUrl = conductorBaseUrl;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }
}
