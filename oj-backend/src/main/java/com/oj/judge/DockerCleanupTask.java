package com.oj.judge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Docker 容器与镜像定期清理任务
 * 清理已停止的容器、未打标签的构建镜像，避免磁盘空间泄漏
 */
@Slf4j
@Component
public class DockerCleanupTask {

    /**
     * 每小时执行一次：清理已停止的容器
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupContainers() {
        log.info("Docker 容器清理任务开始");
        try {
            // 清理已停止的容器（排除运行中的）
            ProcessBuilder pb = new ProcessBuilder("docker", "container", "prune", "-f");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = readProcessOutput(process.getInputStream());
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                log.info("Docker 容器清理完成: {}", output.trim());
            } else {
                log.warn("Docker 容器清理失败: {}", output.trim());
            }
        } catch (Exception e) {
            log.error("Docker 容器清理异常", e);
        }
    }

    /**
     * 每6小时执行一次：清理未打标签的镜像
     */
    @Scheduled(fixedRate = 21600000)
    public void cleanupImages() {
        log.info("Docker 镜像清理任务开始");
        try {
            // 清理悬空镜像（dangling images，即 tag 为 <none> 的）
            ProcessBuilder pb = new ProcessBuilder("docker", "image", "prune", "-f");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = readProcessOutput(process.getInputStream());
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                log.info("Docker 镜像清理完成: {}", output.trim());
            } else {
                log.warn("Docker 镜像清理失败: {}", output.trim());
            }
        } catch (Exception e) {
            log.error("Docker 镜像清理异常", e);
        }
    }

    /**
     * 每天执行一次：清理 oj-sandbox-* 构建产物镜像
     * （仅清理以 oj-sandbox- 为前缀的临时构建镜像，保留 oj-base-* 基础镜像）
     */
    @Scheduled(fixedRate = 86400000)
    public void cleanupSandboxImages() {
        log.info("Docker 临时镜像清理任务开始");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "images", "--format", "{{.Repository}}:{{.Tag}}"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = readProcessOutput(process.getInputStream());
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            if (!finished || process.exitValue() != 0) {
                log.warn("获取镜像列表失败");
                return;
            }

            String[] lines = output.trim().split("\n");
            for (String image : lines) {
                if (image.startsWith("oj-sandbox-")) {
                    try {
                        ProcessBuilder rm = new ProcessBuilder("docker", "rmi", "-f", image);
                        rm.redirectErrorStream(true);
                        Process rmProcess = rm.start();
                        rmProcess.waitFor(30, TimeUnit.SECONDS);
                        log.info("删除临时镜像: {}", image);
                    } catch (Exception e) {
                        log.warn("删除镜像失败: {}", image);
                    }
                }
            }
            log.info("Docker 临时镜像清理任务完成");
        } catch (Exception e) {
            log.error("Docker 临时镜像清理异常", e);
        }
    }

    private String readProcessOutput(java.io.InputStream is) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
