package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import java.util.List;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.JavaForkOptions;

/**
 * {@code kardx.language} 를 태스크에 넘긴다. 값은 쓰일 때 확인하므로 지원하지 않는 값이면 그 값을 쓰는 태스크가
 * 실패한다 ({@link DocsLanguage#of}).
 */
final class DocsLanguageSetup {
    /** 언어를 넣는 태스크 입력 이름. 바꾸면 그 태스크가 다시 실행된다. */
    static final String INPUT = "language";

    private DocsLanguageSetup() {
    }

    /** 사용자가 정한 언어 코드 (소문자). 정하지 않았으면 값이 없다. */
    static Provider<String> configured(KardxExtension extension) {
        return extension.getLanguage()
            .map(value -> DocsLanguage.of(value).code());
    }

    /** 플러그인 문구에 쓰는 언어 코드. 정하지 않았으면 {@link DocsLanguage#DEFAULT}. */
    static Provider<String> effective(KardxExtension extension) {
        return configured(extension)
            .orElse(DocsLanguage.DEFAULT.code());
    }

    /**
     * 테스트 JVM·core CLI 에 정한 언어를 시스템 속성 {@value PluginConstants#LANGUAGE_PROPERTY} 로 넘기고 태스크 입력으로 둔다.
     * 정하지 않았으면 넘기지 않는다: 그때는 테스트 설정(application.yml)의 값이 정한다.
     */
    static <T extends Task & JavaForkOptions> void passTo(T task, KardxExtension extension) {
        Provider<String> language = configured(extension);

        task.getInputs()
            .property(INPUT, language)
            .optional(true);
        task.getJvmArgumentProviders().add(() -> language.isPresent()
            ? List.of("-D" + PluginConstants.LANGUAGE_PROPERTY + "=" + language.get())
            : List.of());
    }

    /**
     * 테스트 JVM 에 플러그인 문구의 언어({@link #effective})를 시스템 속성 {@value PluginConstants#PLUGIN_LANGUAGE_PROPERTY} 로
     * 알린다. 문서 언어는 바꾸지 않는다: core 는 이 값과 자기가 정한 언어(yml, {@code configure})가 다를 때 경고만 한다.
     */
    static void announceTo(Test task, KardxExtension extension) {
        Provider<String> language = effective(extension);

        task.getJvmArgumentProviders().add(() -> List.of(
            "-D" + PluginConstants.PLUGIN_LANGUAGE_PROPERTY + "=" + language.get()));
    }
}
