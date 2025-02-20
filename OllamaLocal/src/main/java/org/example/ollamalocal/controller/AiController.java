package org.example.ollamalocal.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring AI supports Spring Boot 3.2.x and 3.3.x
 * JDK 17
 * zhipuai
 */
@RestController
public class AiController {


    private final ChatClient chatClient;

    public AiController(ChatClient.Builder chatClientBuilder) {
        //自动注入ChatClient.Builder对象，并创建ChatClient对象。
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/ai")
    String generation(String message) {
        //用户输入的信息提交给大模型，使用的是ChatClient与大模型交互。
        return this.chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}

