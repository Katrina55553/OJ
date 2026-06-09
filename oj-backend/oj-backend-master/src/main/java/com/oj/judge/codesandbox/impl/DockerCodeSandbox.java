package com.oj.judge.codesandbox.impl;

import com.oj.common.ErrorCode;
import com.oj.exception.BusinessException;
import com.oj.judge.codesandbox.CodeSandbox;
import com.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.oj.judge.codesandbox.model.JudgeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Docker 代码沙箱
 * 通过 Docker 容器执行用户代码，实现完全本地化的代码执行
 */
@Slf4j
@Component
public class DockerCodeSandbox implements CodeSandbox {

    @Value("${codesandbox.docker.timeout:10}")
    private int timeoutSeconds;

    @Value("${codesandbox.docker.memory:256m}")
    private String memoryLimit;

    @Value("${codesandbox.docker.cpu:1}")
    private String cpuLimit;

    private static final String SANDBOX_IMAGE_PREFIX = "oj-sandbox-";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        String language = request.getLanguage();
        String code = request.getCode();
        List<String> inputList = request.getInputList();

        // 1. 创建临时工作目录
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("oj-sandbox-");

            // 2. 写入代码文件
            String codeFileName = getCodeFileName(language);
            Files.write(workDir.resolve(codeFileName), code.getBytes(StandardCharsets.UTF_8));

            // 3. 构建 Docker 镜像
            String imageTag = SANDBOX_IMAGE_PREFIX + language + "-" + System.currentTimeMillis();
            String dockerfile = getDockerfileName(language);
            buildImage(workDir, dockerfile, imageTag);

            // 4. 执行测试用例
            List<String> outputList = new ArrayList<>();
            long totalTime = 0;
            long totalMemory = 0;

            for (int i = 0; i < inputList.size(); i++) {
                String input = inputList.get(i);
                long startTime = System.currentTimeMillis();

                ContainerResult result = runContainer(imageTag, input);

                long endTime = System.currentTimeMillis();
                long execTime = endTime - startTime;

                if (result.exitCode != 0) {
                    // 执行失败
                    String errorMsg = result.stderr.isEmpty() ? result.stdout : result.stderr;
                    outputList.add(errorMsg);

                    JudgeInfo judgeInfo = JudgeInfo.builder()
                            .message("Runtime Error")
                            .time(execTime)
                            .memory(0L)
                            .build();

                    return ExecuteCodeResponse.builder()
                            .outputList(outputList)
                            .judgeInfo(judgeInfo)
                            .status(2)
                            .message("Runtime Error")
                            .build();
                }

                outputList.add(result.stdout);
                totalTime += execTime;
                totalMemory += result.memoryUsage;
            }

            // 5. 构建返回结果
            JudgeInfo judgeInfo = JudgeInfo.builder()
                    .message("Accepted")
                    .time(totalTime)
                    .memory(totalMemory / 1024) // 转为 KB
                    .build();

            return ExecuteCodeResponse.builder()
                    .outputList(outputList)
                    .judgeInfo(judgeInfo)
                    .status(0)
                    .message("Success")
                    .build();

        } catch (Exception e) {
            log.error("Docker 沙箱执行失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码执行失败: " + e.getMessage());
        } finally {
            // 6. 清理临时目录
            if (workDir != null) {
                try {
                    deleteDirectory(workDir);
                } catch (IOException e) {
                    log.warn("清理临时目录失败: {}", workDir, e);
                }
            }
        }
    }

    /**
     * 构建 Docker 镜像
     */
    private void buildImage(Path workDir, String dockerfile, String imageTag) throws IOException, InterruptedException {
        // 复制 Dockerfile 到工作目录
        Path dockerfilePath = workDir.resolve("Dockerfile");
        Files.copy(getDockerfileResource(dockerfile), dockerfilePath, StandardCopyOption.REPLACE_EXISTING);

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "build", "-t", imageTag, "-f", dockerfilePath.toString(), workDir.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = readProcessOutput(process.getInputStream());
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Docker 镜像构建超时");
        }

        if (process.exitValue() != 0) {
            throw new IOException("Docker 镜像构建失败: " + output);
        }
    }

    /**
     * 运行 Docker 容器
     */
    private ContainerResult runContainer(String imageTag, String input) throws IOException, InterruptedException {
        // 生成唯一容器名，避免冲突
        String containerName = "oj-run-" + UUID.randomUUID().toString().substring(0, 8);

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--name=" + containerName);
        cmd.add("--memory=" + memoryLimit);
        cmd.add("--cpus=" + cpuLimit);
        cmd.add("--network=none");
        cmd.add("--pids-limit=50");
        cmd.add("--read-only");
        cmd.add("--user=nobody");
        cmd.add("-i"); // 保持 stdin 打开

        cmd.add(imageTag);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        // 写入输入
        if (input != null && !input.isEmpty()) {
            try (OutputStream os = process.getOutputStream()) {
                os.write(input.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }

        // 启动后台线程，在容器运行期间轮询采集内存使用
        AtomicLong maxMemoryBytes = new AtomicLong(0);
        Thread statsCollector = startMemoryStatsCollector(containerName, maxMemoryBytes);

        // 读取输出
        String stdout = readProcessOutput(process.getInputStream());
        String stderr = readProcessOutput(process.getErrorStream());

        // 等待执行完成（带超时）
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        // 停止内存采集
        stopMemoryStatsCollector(statsCollector);

        if (!finished) {
            process.destroyForcibly();
            removeContainer(containerName);
            return new ContainerResult(-1, "", "Time Limit Exceeded", maxMemoryBytes.get());
        }

        int exitCode = process.exitValue();
        long memoryUsage = maxMemoryBytes.get();

        removeContainer(containerName);

        return new ContainerResult(exitCode, stdout.trim(), stderr.trim(), memoryUsage);
    }

    /**
     * 启动后台线程轮询 docker stats 采集容器内存峰值
     */
    private Thread startMemoryStatsCollector(String containerName, AtomicLong maxMemoryBytes) {
        Thread thread = new Thread(() -> {
            try {
                // 等待容器启动（给 docker 一点时间初始化）
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    long mem = pollContainerMemory(containerName);
                    if (mem > 0) {
                        maxMemoryBytes.updateAndGet(current -> Math.max(current, mem));
                    }
                    Thread.sleep(500); // 每 500ms 采样一次
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    // 容器已停止或查询失败，终止轮询
                    break;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 停止内存采集线程
     */
    private void stopMemoryStatsCollector(Thread thread) {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            try {
                thread.join(2000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * 查询容器当前内存使用量（单次采样），容器停止则返回 -1
     */
    private long pollContainerMemory(String containerName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "stats", "--no-stream",
                    "--format={{.MemUsage}}",
                    containerName
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = readProcessOutput(process.getInputStream()).trim();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);

            // 容器已停止或不存在（finished=false 表示超时）
            if (!finished || output.isEmpty()) {
                return -1;
            }

            // 格式: "15.5MiB / 256MiB"，取斜杠前的实际使用值
            String usagePart = output.split("/")[0].trim();
            return parseMemoryToBytes(usagePart);
        } catch (Exception e) {
            return -1; // 查询失败，通知调用方终止轮询
        }
    }

    /**
     * 解析 docker 内存格式（如 "15.5MiB", "256KiB", "1.2GiB"）为字节数
     */
    private long parseMemoryToBytes(String memStr) {
        try {
            memStr = memStr.trim();
            double value;
            long multiplier;

            if (memStr.endsWith("GiB")) {
                value = Double.parseDouble(memStr.replace("GiB", "").trim());
                multiplier = 1024L * 1024 * 1024;
            } else if (memStr.endsWith("MiB")) {
                value = Double.parseDouble(memStr.replace("MiB", "").trim());
                multiplier = 1024L * 1024;
            } else if (memStr.endsWith("KiB")) {
                value = Double.parseDouble(memStr.replace("KiB", "").trim());
                multiplier = 1024L;
            } else if (memStr.endsWith("B")) {
                value = Double.parseDouble(memStr.replace("B", "").trim());
                multiplier = 1;
            } else {
                return 0;
            }
            return (long) (value * multiplier);
        } catch (NumberFormatException e) {
            log.warn("解析内存字符串失败: {}", memStr);
            return 0;
        }
    }

    /**
     * 删除容器（忽略错误，容器可能已不存在）
     */
    private void removeContainer(String containerName) {
        try {
            new ProcessBuilder("docker", "rm", "-f", containerName)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("清理容器失败（可能已自动删除）: containerName={}", containerName);
        }
    }

    /**
     * 读取进程输出
     */
    private String readProcessOutput(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 读取 Dockerfile 资源
     */
    private InputStream getDockerfileResource(String dockerfile) {
        InputStream is = getClass().getClassLoader().getResourceAsStream("sandbox/" + dockerfile);
        if (is == null) {
            // 如果资源不存在，使用外部文件
            try {
                return Files.newInputStream(Paths.get("sandbox", dockerfile));
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "找不到 Dockerfile: " + dockerfile);
            }
        }
        return is;
    }

    /**
     * 根据语言获取代码文件名
     */
    private String getCodeFileName(String language) {
        switch (language.toLowerCase()) {
            case "cpp": return "solution.cpp";
            case "java": return "Solution.java";
            case "python": return "solution.py";
            case "go": return "solution.go";
            case "javascript": return "solution.js";
            default: throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的语言: " + language);
        }
    }

    /**
     * 根据语言获取 Dockerfile 名称
     */
    private String getDockerfileName(String language) {
        switch (language.toLowerCase()) {
            case "cpp": return "Dockerfile.cpp";
            case "java": return "Dockerfile.java";
            case "python": return "Dockerfile.python";
            case "go": return "Dockerfile.go";
            case "javascript": return "Dockerfile.node";
            default: throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的语言: " + language);
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        log.warn("删除文件失败: {}", path, e);
                    }
                });
    }

    /**
     * 容器执行结果
     */
    private static class ContainerResult {
        final int exitCode;
        final String stdout;
        final String stderr;
        final long memoryUsage;

        ContainerResult(int exitCode, String stdout, String stderr, long memoryUsage) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.memoryUsage = memoryUsage;
        }
    }
}
