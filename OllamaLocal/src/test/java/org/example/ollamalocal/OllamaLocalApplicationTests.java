package org.example.ollamalocal;

import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.ollama.OllamaEmbeddingProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest(classes = OllamaLocalApplication.class)
public class OllamaLocalApplicationTests {

    @Autowired
    private OllamaChatModel ollamaChatModel;

    @Autowired
    private OllamaEmbeddingModel embeddingModel;

    @Test
    public void testChatModel() {
        String prompt = """
                你是一个精通中文和英文的翻译大师。如果我给你英文就翻译成中文，给你中文就翻译成英文。
                """;
        String message = """
                Ollama now supports tool calling with popular models such as Llama 3.1.
                This enables a model to answer a given prompt using tool(s) it knows about,
                making it possible for models to perform more complex tasks or interact with the outside world.
                """;

        String result = ollamaChatModel.call(prompt + ":" + message);

        System.out.println(result);
    }


    @Test
    public void testEmbeddingModel() {
        // 例 1：将文本转换为 Embedding
        float[] embeddings1 = embeddingModel.embed("I like Spring Boot");

        System.out.println(embeddings1);
    }
}

