package com.am.common.amcommondata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for SecurityService.
 */
@ConfigurationProperties(prefix = "am.common.security")
public class SecurityServiceProperties {
    
    /**
     * Enable/disable the SecurityService auto-configuration.
     */
    private boolean enabled = true;
    
    /**
     * Maximum number of symbols to process in a single batch.
     */
    private int maxSymbolBatchSize = 100;
    
    /**
     * Enable/disable detailed logging for security operations.
     */
    private boolean detailedLogging = false;
    
    /**
     * Default timeout in milliseconds for security operations.
     */
    private int operationTimeoutMs = 5000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxSymbolBatchSize() {
        return maxSymbolBatchSize;
    }

    public void setMaxSymbolBatchSize(int maxSymbolBatchSize) {
        this.maxSymbolBatchSize = maxSymbolBatchSize;
    }

    public boolean isDetailedLogging() {
        return detailedLogging;
    }

    public void setDetailedLogging(boolean detailedLogging) {
        this.detailedLogging = detailedLogging;
    }

    public int getOperationTimeoutMs() {
        return operationTimeoutMs;
    }

    public void setOperationTimeoutMs(int operationTimeoutMs) {
        this.operationTimeoutMs = operationTimeoutMs;
    }
    
    @Override
    public String toString() {
        return "SecurityServiceProperties{" +
                "enabled=" + enabled +
                ", maxSymbolBatchSize=" + maxSymbolBatchSize +
                ", detailedLogging=" + detailedLogging +
                ", operationTimeoutMs=" + operationTimeoutMs +
                '}';
    }
}
