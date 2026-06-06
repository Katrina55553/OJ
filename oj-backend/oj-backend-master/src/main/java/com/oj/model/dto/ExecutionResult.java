package com.oj.model.dto;

public class ExecutionResult {
    private String status;
    private String stdout;
    private String stderr;
    private long time;   // 毫秒
    private int memory;  // KB

    // Getters and Setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStdout() { return stdout; }
    public void setStdout(String stdout) { this.stdout = stdout; }

    public String getStderr() { return stderr; }
    public void setStderr(String stderr) { this.stderr = stderr; }

    public long getTime() { return time; }
    public void setTime(long time) { this.time = time; }

    public int getMemory() { return memory; }
    public void setMemory(int memory) { this.memory = memory; }
}