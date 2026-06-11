package com.oj.model.dto;

/**
 * 代码即时执行请求
 */
public class CodeExecuteRequest {
    private String script;
    private String language;
    private String stdin = "";

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getStdin() {
        return stdin;
    }

    public void setStdin(String stdin) {
        this.stdin = stdin;
    }
}
