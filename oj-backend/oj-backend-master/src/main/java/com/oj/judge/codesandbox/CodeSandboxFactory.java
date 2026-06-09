package com.oj.judge.codesandbox;

import com.oj.judge.codesandbox.impl.DockerCodeSandbox;
import com.oj.judge.codesandbox.impl.ExampleCodeSandbox;
import com.oj.judge.codesandbox.impl.RemoteCodeSandbox;
import com.oj.judge.codesandbox.impl.ThirdPartyCodeSandbox;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 代码沙箱工厂
 * 从 Spring 容器获取 Bean，确保 @Value 等 DI 注解生效
 */
@Component
public class CodeSandboxFactory {

    @Resource
    private DockerCodeSandbox dockerCodeSandbox;

    @Resource
    private RemoteCodeSandbox remoteCodeSandbox;

    @Resource
    private ThirdPartyCodeSandbox thirdPartyCodeSandbox;

    @Resource
    private ExampleCodeSandbox exampleCodeSandbox;

    /**
     * 根据类型获取代码沙箱实例（Spring 管理的 Bean）
     *
     * @param type 沙箱类型: example / remote / thirdParty / docker
     */
    public CodeSandbox getSandbox(String type) {
        switch (type) {
            case "example":
                return exampleCodeSandbox;
            case "remote":
                return remoteCodeSandbox;
            case "thirdParty":
                return thirdPartyCodeSandbox;
            case "docker":
            default:
                return dockerCodeSandbox;
        }
    }
}
