package com.fate.codesandbox.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.fate.codesandbox.config.SandboxProperties;
import com.fate.codesandbox.model.ExecuteRequest;
import com.fate.codesandbox.model.ExecuteResponse;
import com.fate.codesandbox.model.JudgeInfo;
import com.fate.codesandbox.service.CodeSandBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 基于 isolate 的真实沙箱执行器
 */
@Component
@Slf4j
public class IsolateSandboxExecutor implements CodeSandBox {

    private static final AtomicInteger BOX_ID_ALLOCATOR = new AtomicInteger(1);

    @Resource
    private SandboxProperties sandboxProperties;

    private final List<LanguageExecutor> languageExecutors;

    public IsolateSandboxExecutor(List<LanguageExecutor> languageExecutors) {
        this.languageExecutors = languageExecutors;
    }

    @Override
    public ExecuteResponse executeCode(ExecuteRequest executeRequest) {
        if (executeRequest == null || StrUtil.isBlank(executeRequest.getCode())) {
            return buildFailureResponse(JudgeStatus.COMPILE_ERROR, "代码不能为空", 0L, 0L);
        }
        LanguageExecutor languageExecutor = getLanguageExecutor(executeRequest.getLanguage());
        if (languageExecutor == null) {
            return buildFailureResponse(JudgeStatus.SYSTEM_ERROR, "不支持该语言: " + executeRequest.getLanguage(), 0L, 0L);
        }

        List<String> inputList = executeRequest.getInputList();
        if (inputList == null || inputList.isEmpty()) {
            inputList = Collections.singletonList("");
        }

        File submitDir = new File(sandboxProperties.getWorkDir(), UUID.randomUUID().toString());
        try {
            FileUtil.mkdir(submitDir);
            FileUtil.writeString(executeRequest.getCode(),
                    new File(submitDir, languageExecutor.getSourceFileName()), StandardCharsets.UTF_8);

            int compileBoxId = nextBoxId();
            IsolateCommandResult compileResult = runInIsolate(
                    compileBoxId,
                    submitDir,
                    languageExecutor.getCompileCommand(),
                    sandboxProperties.getCompileTimeLimit(),
                    sandboxProperties.getCompileMemoryLimit(),
                    sandboxProperties.getDefaultStackLimit(),
                    null);
            cleanupBox(compileBoxId);

            if (isTimeLimit(compileResult)) {
                return buildFailureResponse(JudgeStatus.COMPILE_ERROR, "编译超时", compileResult.getTimeMillis(), compileResult.getMemoryKb());
            }
            if (compileResult.getExitCode() != 0) {
                return buildFailureResponse(JudgeStatus.COMPILE_ERROR, firstNotBlank(compileResult.getStderr(), compileResult.getStdout()),
                        compileResult.getTimeMillis(), compileResult.getMemoryKb());
            }

            return runTestCases(executeRequest, languageExecutor, submitDir, inputList);
        } catch (Exception e) {
            log.error("沙箱执行失败", e);
            return buildFailureResponse(JudgeStatus.SYSTEM_ERROR, e.getMessage(), 0L, 0L);
        } finally {
            FileUtil.del(submitDir);
        }
    }

    private ExecuteResponse runTestCases(ExecuteRequest executeRequest, LanguageExecutor languageExecutor,
                                         File submitDir, List<String> inputList) throws IOException, InterruptedException {
        List<String> outputList = new ArrayList<>();
        long maxTime = 0L;
        long maxMemory = 0L;

        for (int i = 0; i < inputList.size(); i++) {
            File caseDir = new File(submitDir.getParentFile(), submitDir.getName() + "-case-" + i);
            copyDirectory(submitDir.toPath(), caseDir.toPath());
            File inputFile = new File(caseDir, "input.txt");
            FileUtil.writeString(inputList.get(i) == null ? "" : inputList.get(i), inputFile, StandardCharsets.UTF_8);

            int boxId = nextBoxId();
            IsolateCommandResult runResult;
            try {
                runResult = runInIsolate(
                        boxId,
                        caseDir,
                        languageExecutor.getRunCommand() + " < input.txt",
                        limitOrDefault(executeRequest.getTimeLimit(), sandboxProperties.getDefaultTimeLimit()),
                        limitOrDefault(executeRequest.getMemoryLimit(), sandboxProperties.getDefaultMemoryLimit()),
                        limitOrDefault(executeRequest.getStackLimit(), sandboxProperties.getDefaultStackLimit()),
                        new File(caseDir, "stdout.txt"));
            } finally {
                cleanupBox(boxId);
            }

            maxTime = Math.max(maxTime, runResult.getTimeMillis());
            maxMemory = Math.max(maxMemory, runResult.getMemoryKb());
            File stdoutFile = new File(caseDir, "stdout.txt");
            if (stdoutFile.exists() && stdoutFile.length() > sandboxProperties.getMaxOutputBytes()) {
                FileUtil.del(caseDir);
                return buildFailureResponse(JudgeStatus.OUTPUT_LIMIT_EXCEEDED, "输出超出限制", maxTime, maxMemory);
            }

            if (isTimeLimit(runResult)) {
                FileUtil.del(caseDir);
                return buildFailureResponse(JudgeStatus.TIME_LIMIT_EXCEEDED, "运行超时", maxTime, maxMemory);
            }
            if (isMemoryLimit(runResult)) {
                FileUtil.del(caseDir);
                return buildFailureResponse(JudgeStatus.MEMORY_LIMIT_EXCEEDED, "内存超出限制", maxTime, maxMemory);
            }
            if (runResult.getExitCode() != 0) {
                FileUtil.del(caseDir);
                return buildFailureResponse(JudgeStatus.RUNTIME_ERROR, firstNotBlank(runResult.getStderr(), runResult.getStdout()),
                        maxTime, maxMemory);
            }

            outputList.add(runResult.getStdout());
            FileUtil.del(caseDir);
        }

        ExecuteResponse executeResponse = new ExecuteResponse();
        executeResponse.setStatus(0);
        executeResponse.setMessage(JudgeStatus.ACCEPTED);
        executeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(JudgeStatus.ACCEPTED);
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);
        executeResponse.setJudgeInfo(judgeInfo);
        return executeResponse;
    }

    private IsolateCommandResult runInIsolate(int boxId, File workDir, String command, Long timeLimit,
                                              Long memoryLimit, Long stackLimit, File stdoutFile) throws IOException, InterruptedException {
        initBox(boxId);
        File metaFile = new File(workDir, "meta-" + UUID.randomUUID() + ".txt");
        File stderrFile = new File(workDir, "stderr-" + UUID.randomUUID() + ".txt");
        File actualStdoutFile = stdoutFile == null ? new File(workDir, "stdout-" + UUID.randomUUID() + ".txt") : stdoutFile;

        List<String> isolateCommand = new ArrayList<>();
        isolateCommand.add("isolate");
        isolateCommand.add("--cg");
        isolateCommand.add("--box-id=" + boxId);
        isolateCommand.add("--dir=" + workDir.getAbsolutePath() + "=/box:rw");
        isolateCommand.add("--chdir=/box");
        isolateCommand.add("--meta=" + metaFile.getAbsolutePath());
        isolateCommand.add("--time=" + toSeconds(timeLimit));
        isolateCommand.add("--wall-time=" + toSeconds(timeLimit + sandboxProperties.getWallTimeExtra()));
        isolateCommand.add("--mem=" + memoryLimit);
        isolateCommand.add("--stack=" + stackLimit);
        isolateCommand.add("--processes=" + sandboxProperties.getMaxProcesses());
        isolateCommand.add("--fsize=" + sandboxProperties.getMaxFileSize());
        isolateCommand.add("--stdout=" + actualStdoutFile.getAbsolutePath());
        isolateCommand.add("--stderr=" + stderrFile.getAbsolutePath());
        isolateCommand.add("--run");
        isolateCommand.add("--");
        isolateCommand.add("/bin/bash");
        isolateCommand.add("-lc");
        isolateCommand.add(command);

        Process process = new ProcessBuilder(isolateCommand).redirectErrorStream(false).start();
        boolean finished = process.waitFor((timeLimit + sandboxProperties.getWallTimeExtra() + 5000L), TimeUnit.MILLISECONDS);
        IsolateCommandResult result = new IsolateCommandResult();
        if (!finished) {
            result.setProcessTimeout(true);
            process.destroyForcibly();
        }
        result.setExitCode(finished ? process.exitValue() : 124);
        result.setStdout(readFile(actualStdoutFile));
        result.setStderr(readFile(stderrFile));
        result.setMeta(readMeta(metaFile));
        return result;
    }

    private void initBox(int boxId) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("isolate", "--cg", "--box-id=" + boxId, "--init").start();
        if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("isolate 初始化失败，请确认 Linux 环境已安装 isolate 并具备 cgroup 权限");
        }
    }

    private void cleanupBox(int boxId) {
        try {
            Process process = new ProcessBuilder("isolate", "--cg", "--box-id=" + boxId, "--cleanup").start();
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("清理 isolate box 失败, boxId={}", boxId, e);
        }
    }

    private LanguageExecutor getLanguageExecutor(String language) {
        for (LanguageExecutor languageExecutor : languageExecutors) {
            if (languageExecutor.supports(language)) {
                return languageExecutor;
            }
        }
        return null;
    }

    private ExecuteResponse buildFailureResponse(String status, String message, Long time, Long memory) {
        ExecuteResponse executeResponse = new ExecuteResponse();
        executeResponse.setStatus(1);
        executeResponse.setMessage(message);
        executeResponse.setOutputList(new ArrayList<>());
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(status);
        judgeInfo.setTime(time);
        judgeInfo.setMemory(memory);
        executeResponse.setJudgeInfo(judgeInfo);
        return executeResponse;
    }

    private boolean isTimeLimit(IsolateCommandResult result) {
        return result.isProcessTimeout() || "TO".equals(result.getIsolateStatus());
    }

    private boolean isMemoryLimit(IsolateCommandResult result) {
        return "SG".equals(result.getIsolateStatus()) && StrUtil.containsIgnoreCase(result.getMeta().get("message"), "memory");
    }

    private Long limitOrDefault(Long value, Long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private int nextBoxId() {
        int next = BOX_ID_ALLOCATOR.getAndIncrement();
        if (next > 20000) {
            BOX_ID_ALLOCATOR.compareAndSet(next + 1, 1);
        }
        return next;
    }

    private String toSeconds(Long millis) {
        return String.format(Locale.US, "%.3f", millis / 1000.0);
    }

    private String firstNotBlank(String first, String second) {
        if (StrUtil.isNotBlank(first)) {
            return first;
        }
        return second == null ? "" : second;
    }

    private String readFile(File file) throws IOException {
        if (file == null || !file.exists()) {
            return "";
        }
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
    }

    private Map<String, String> readMeta(File file) throws IOException {
        Map<String, String> meta = new HashMap<>();
        if (file == null || !file.exists()) {
            return meta;
        }
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        for (String line : lines) {
            int splitIndex = line.indexOf(':');
            if (splitIndex > 0) {
                meta.put(line.substring(0, splitIndex), line.substring(splitIndex + 1));
            }
        }
        return meta;
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    Path targetPath = target.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }
}
