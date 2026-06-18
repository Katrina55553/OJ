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

import javax.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
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
    private static final String BASE_IMAGE_PREFIX = "oj-base-";

    /**
     * 内存采集线程池：复用线程避免每次提交都 new Thread()，降低高并发下线程创建开销
     */
    private static final ScheduledExecutorService MEMORY_STATS_EXECUTOR =
            Executors.newScheduledThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                    r -> {
                        Thread t = new Thread(r, "oj-sandbox-mem-stats");
                        t.setDaemon(true);
                        return t;
                    });

    @PreDestroy
    public void shutdown() {
        MEMORY_STATS_EXECUTOR.shutdownNow();
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        String language = request.getLanguage();
        String code = request.getCode();
        List<String> inputList = request.getInputList();

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("oj-sandbox-");

            String codeFileName = getCodeFileName(language);
            Files.write(workDir.resolve(codeFileName), code.getBytes(StandardCharsets.UTF_8));

            String baseImage = BASE_IMAGE_PREFIX + language;
            ensureBaseImage(language, baseImage);

            if (needsCompilation(language)) {
                compileCodeInContainer(baseImage, workDir, language);
            }

            List<String> outputList = new ArrayList<>();
            long totalTime = 0;
            long totalMemory = 0;

            for (int i = 0; i < inputList.size(); i++) {
                String input = inputList.get(i);
                long startTime = System.currentTimeMillis();

                ContainerResult result = runContainerWithMount(baseImage, workDir, language, input);

                long endTime = System.currentTimeMillis();
                long execTime = endTime - startTime;

                if (result.exitCode != 0) {
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

            JudgeInfo judgeInfo = JudgeInfo.builder()
                    .message("Accepted")
                    .time(totalTime)
                    .memory(totalMemory / 1024)
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
            if (workDir != null) {
                try {
                    deleteDirectory(workDir);
                } catch (IOException e) {
                    log.warn("清理临时目录失败: {}", workDir, e);
                }
            }
        }
    }

    private void ensureBaseImage(String language, String baseImage) throws IOException, InterruptedException {
        ProcessBuilder check = new ProcessBuilder("docker", "image", "inspect", baseImage);
        check.redirectErrorStream(true);
        Process checkProcess = check.start();
        boolean checkFinished = checkProcess.waitFor(5, TimeUnit.SECONDS);
        if (checkFinished && checkProcess.exitValue() == 0) {
            return;
        }

        String dockerfile = "Dockerfile.base." + language;
        Path tempDir = Files.createTempDirectory("oj-base-build-");
        try {
            Files.copy(getDockerfileResource(dockerfile), tempDir.resolve("Dockerfile"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.delete(tempDir);
            throw new IOException("复制 base Dockerfile 失败: " + dockerfile, e);
        }

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "build", "-t", baseImage, "-f", tempDir.resolve("Dockerfile").toString(), tempDir.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = readProcessOutput(process.getInputStream());
        boolean finished = process.waitFor(300, TimeUnit.SECONDS);

        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}

        if (!finished) {
            process.destroyForcibly();
            throw new IOException("基础镜像构建超时: " + baseImage);
        }
        if (process.exitValue() != 0) {
            throw new IOException("基础镜像构建失败: " + baseImage + "\n" + output);
        }
        log.info("基础镜像构建完成: {}", baseImage);
    }

    private boolean needsCompilation(String language) {
        switch (language.toLowerCase()) {
            case "cpp":
            case "java":
            case "go":
                return true;
            default:
                return false;
        }
    }

    private void compileCodeInContainer(String baseImage, Path workDir, String language) throws IOException, InterruptedException {
        String compileCmd = getCompileCommand(language);
        String containerName = "oj-compile-" + UUID.randomUUID().toString().substring(0, 8);

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--name=" + containerName);
        cmd.add("--memory=" + memoryLimit);
        cmd.add("--cpus=" + cpuLimit);
        cmd.add("--network=none");
        cmd.add("-v");
        cmd.add(workDir.toString() + ":/code");
        cmd.add(baseImage);
        cmd.add("bash");
        cmd.add("-c");
        cmd.add(compileCmd);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = readProcessOutput(process.getInputStream());
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);

        removeContainer(containerName);

        if (!finished) {
            process.destroyForcibly();
            throw new IOException("代码编译超时");
        }
        if (process.exitValue() != 0) {
            throw new IOException("代码编译错误:\n" + output);
        }
    }

    private String getCompileCommand(String language) {
        switch (language.toLowerCase()) {
            case "cpp":
                return "g++ -o solution solution.cpp -std=c++17 -O2 -Wall 2>&1";
            case "java":
                return "javac Solution.java 2>&1";
            case "go":
                return "go build -o solution solution.go 2>&1";
            default:
                return "";
        }
    }

    private ContainerResult runContainerWithMount(String baseImage, Path workDir, String language, String input) throws IOException, InterruptedException {
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
        cmd.add("-v");
        cmd.add(workDir.toString() + ":/code:ro");
        cmd.add("-i");
        cmd.add(baseImage);
        cmd.add("bash");
        cmd.add("-c");
        cmd.add(getRunCommand(language));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        if (input != null && !input.isEmpty()) {
            try (OutputStream os = process.getOutputStream()) {
                os.write(input.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }

        AtomicLong maxMemoryBytes = new AtomicLong(0);
        ScheduledFuture<?> statsTask = startMemoryStatsCollector(containerName, maxMemoryBytes);

        String stdout = readProcessOutput(process.getInputStream());
        String stderr = readProcessOutput(process.getErrorStream());

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        stopMemoryStatsCollector(statsTask);

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

    private String getRunCommand(String language) {
        switch (language.toLowerCase()) {
            case "cpp":
                return "./solution";
            case "java":
                return "java Solution";
            case "python":
                return "python3 solution.py";
            case "go":
                return "./solution";
            case "javascript":
                return "node solution.js";
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的语言: " + language);
        }
    }

    /**
     * 启动后台任务轮询 docker stats 采集容器内存峰值
     * 使用 ScheduledExecutorService 复用线程，避免每次提交都创建新线程
     * 容器停止后（pollContainerMemory 返回 -1）自动终止任务
     *
     * @return ScheduledFuture，可用于取消任务
     */
    private ScheduledFuture<?> startMemoryStatsCollector(String containerName, AtomicLong maxMemoryBytes) {
        return MEMORY_STATS_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                long mem = pollContainerMemory(containerName);
                if (mem > 0) {
                    maxMemoryBytes.updateAndGet(current -> Math.max(current, mem));
                } else if (mem < 0) {
                    // 容器已停止或不可访问，终止本任务（用 CancellationException 跳出）
                    throw new CancellationException("container stopped");
                }
            } catch (CancellationException e) {
                throw e;
            } catch (Exception e) {
                // 静默忽略单次采样错误，下次继续
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止内存采集任务
     */
    private void stopMemoryStatsCollector(ScheduledFuture<?> task) {
        if (task == null) {
            return;
        }
        task.cancel(false);
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
