package com.fate.codesandbox.sandbox;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * isolate 命令执行结果
 */
@Data
public class IsolateCommandResult {

    private int exitCode;

    private String stdout = "";

    private String stderr = "";

    private Map<String, String> meta = new HashMap<>();

    private boolean processTimeout;

    public Long getTimeMillis() {
        String time = meta.get("time");
        if (time == null) {
            time = meta.get("time-wall");
        }
        if (time == null) {
            return 0L;
        }
        return (long) (Double.parseDouble(time) * 1000);
    }

    public Long getMemoryKb() {
        String memory = meta.get("max-rss");
        if (memory == null) {
            return 0L;
        }
        return Long.parseLong(memory);
    }

    public String getIsolateStatus() {
        return meta.get("status");
    }
}
