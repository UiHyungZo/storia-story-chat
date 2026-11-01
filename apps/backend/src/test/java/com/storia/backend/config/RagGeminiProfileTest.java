package com.storia.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

/** 개발 전용 {@code rag-gemini} 프로필 — 관리자 Wiki RAG만 바꾸고 기본값·캐릭터 RAG는 그대로인지 확인한다. */
class RagGeminiProfileTest {

    private static Binder binder(String... files) throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        // OS 환경변수(STORIA_RAG_PROVIDER 등)가 결과에 섞이지 않도록 뺀다.
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String file : files) {
            // 클래스패스에는 테스트용 application.yml이 먼저 있으므로 운영 설정 파일을 경로로 직접 읽는다.
            List<PropertySource<?>> sources = loader.load(file, new FileSystemResource("src/main/resources/" + file));
            sources.forEach(env.getPropertySources()::addFirst); // 뒤에 오는 파일이 우선(프로필 파일)
        }
        // application.yml의 ${STORIA_…:기본값} 자리표시자를 기본값으로 풀어 바인딩한다.
        return new Binder(ConfigurationPropertySources.get(env), new PropertySourcesPlaceholdersResolver(env));
    }

    @Test
    void defaultStaysLocalWithProviderDefaultThreshold() throws Exception {
        RagProperties rag = binder("application.yml").bind("storia.rag", RagProperties.class).get();
        assertThat(rag.useGemini()).isFalse();
        assertThat(rag.provider()).isEqualTo("local");
        assertThat(rag.minScore()).isNull();
    }

    @Test
    void profileSwitchesOnlyAdminRagToGeminiWithCalibratedThreshold() throws Exception {
        Binder b = binder("application.yml", "application-rag-gemini.yml");
        RagProperties rag = b.bind("storia.rag", RagProperties.class).get();
        assertThat(rag.useGemini()).isTrue();
        assertThat(rag.minScore()).isEqualTo(0.75);
        assertThat(rag.geminiModel()).isEqualTo("gemini-embedding-2");
        assertThat(rag.dimensions()).isEqualTo(768);
        assertThat(rag.cacheDir()).isEqualTo(".storia-rag-cache");

        CharacterRagProperties character = b.bind("storia.character-rag", CharacterRagProperties.class).get();
        assertThat(character.provider()).isEqualTo("local");
        assertThat(character.enabled()).isFalse();
        assertThat(character.minScore()).isNull();
    }
}
