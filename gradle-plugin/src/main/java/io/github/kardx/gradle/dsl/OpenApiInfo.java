package io.github.kardx.gradle.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/**
 * OpenAPI 스펙의 최상위 메타데이터({@code info}, {@code externalDocs}, {@code tags}, {@code x-tagGroups}). epages 가 정하지
 * 못하는 값을 {@code openapi3} 후처리에서 넣는다. 정하지 않은 항목은 epages 결과 그대로 둔다. 버전별 스펙
 * ({@code apiVersioning { splitSpecs = true }})에도 같이 들어간다.
 *
 * <pre>{@code
 * openApiInfo {
 *     descriptionFile = file("src/docs/openapi-intro.md")   // 또는 description = "..." (Markdown)
 *     termsOfService = "https://example.com/terms"
 *     contact {
 *         name = "API team"
 *         email = "api@example.com"
 *     }
 *     license {
 *         name = "Apache 2.0"
 *         url = "https://www.apache.org/licenses/LICENSE-2.0"
 *     }
 *     externalDocs {
 *         url = "https://wiki.example.com/api"
 *         description = "Integration guide"
 *     }
 *     tag("Users") {
 *         description = "Sign-up and profile"
 *     }
 *     tag("Orders")                         // 순서만 정한다
 *     tagGroup("Accounts", "Users")         // x-tagGroups (Redoc, Scalar)
 *     tagGroup("Shopping", "Orders", "Cart")
 * }
 * }</pre>
 */
public abstract class OpenApiInfo {

    /** 태그 하나의 설정을 받을 객체를 만든다. */
    @Inject
    protected abstract ObjectFactory getObjects();

    /**
     * {@code info.description} (Markdown). 기본값 없음 (epages 값 그대로). {@link #getDescriptionFile()} 과 함께 정하면
     * 빌드가 실패한다.
     */
    public abstract Property<String> getDescription();

    /**
     * {@code info.description} 으로 쓸 파일 (UTF-8 Markdown). 파일 내용이 바뀌면 스펙을 다시 만든다. 파일이 없으면 빌드가
     * 실패한다. 기본값 없음
     */
    public abstract RegularFileProperty getDescriptionFile();

    /** {@code info.termsOfService} (URL). 기본값 없음 */
    public abstract Property<String> getTermsOfService();

    /** {@code info.contact}. {@link #contact(Action)} 블록으로 바꾼다. 아무 값도 정하지 않으면 넣지 않는다. */
    @Nested
    public abstract Contact getContact();

    /** {@code info.license}. {@link #license(Action)} 블록으로 바꾼다. {@code name} 을 정해야 넣는다. */
    @Nested
    public abstract License getLicense();

    /** 문서 최상위 {@code externalDocs}. {@link #externalDocs(Action)} 블록으로 바꾼다. {@code url} 을 정해야 넣는다. */
    @Nested
    public abstract ExternalDocs getExternalDocs();

    /**
     * 최상위 {@code tags} 의 순서. 여기 적은 태그가 이 순서로 앞에 오고, 나머지는 epages 가 만든 순서대로 뒤에 온다.
     * {@link #tag(String)} 이 차례로 더한다.
     */
    public abstract ListProperty<String> getTagOrder();

    /**
     * 태그 이름 → 설명. 테스트 클래스 {@code @AsciiDescription} 보다 앞선다. 빈 값은 설명을 바꾸지 않는다.
     * {@link #tag(String, Action)} 이 더한다.
     */
    public abstract MapProperty<String, String> getTagDescriptions();

    /**
     * 태그의 {@code externalDocs}. 키는 {@code <태그>.url}, {@code <태그>.description}. 빈 값은 넣지 않는다.
     * {@link #tag(String, Action)} 이 더한다.
     */
    public abstract MapProperty<String, String> getTagExternalDocs();

    /** {@code x-tagGroups}: 그룹 이름 → 태그 이름. 정한 순서대로 넣는다. {@link #tagGroup(String, String...)} 이 더한다. */
    public abstract MapProperty<String, List<String>> getTagGroups();

    /** {@code info.contact} 설정. */
    public void contact(Action<? super Contact> action) {
        action.execute(getContact());
    }

    /** {@code info.license} 설정. */
    public void license(Action<? super License> action) {
        action.execute(getLicense());
    }

    /** 최상위 {@code externalDocs} 설정. */
    public void externalDocs(Action<? super ExternalDocs> action) {
        action.execute(getExternalDocs());
    }

    /** 태그를 {@link #getTagOrder()} 의 다음 자리에 둔다. 이미 있으면 그대로 둔다. */
    public void tag(String name) {
        requireName(name, "tag");

        if (!getTagOrder().get().contains(name)) {
            getTagOrder().add(name);
        }
    }

    /** 태그를 순서에 더하고 설명·{@code externalDocs} 를 정한다. 값은 늦게 정한 provider 여도 된다. */
    public void tag(String name, Action<? super Tag> action) {
        Tag tag = getObjects().newInstance(Tag.class);

        tag(name);
        action.execute(tag);
        getTagDescriptions().put(name, tag.getDescription().orElse(""));
        getTagExternalDocs().put(name + ".url", tag.getExternalDocs().getUrl().orElse(""));
        getTagExternalDocs().put(name + ".description", tag.getExternalDocs().getDescription().orElse(""));
    }

    /**
     * {@code x-tagGroups} 에 그룹을 더한다. Redoc 은 그룹을 쓰는 문서에서 어느 그룹에도 없는 태그를 보이지 않으므로, 그런 태그는
     * 마지막 그룹 {@code Other}(한국어 문서는 {@code 기타})에 모은다.
     */
    public void tagGroup(String name, String... tags) {
        requireName(name, "tagGroup");

        if (tags.length == 0) {
            throw new GradleException("kardx.openApiInfo.tagGroup(\"" + name + "\") needs at least one tag");
        }

        // configuration cache 가 값을 저장하므로 JDK 불변 목록(List.of) 대신 ArrayList 로 둔다
        getTagGroups().put(name, new ArrayList<>(Arrays.asList(tags)));
    }

    private static void requireName(String name, String method) {
        if (name == null || name.isBlank()) {
            throw new GradleException("kardx.openApiInfo." + method + "(name) needs a name");
        }
    }

    /** {@code info.contact}. */
    public abstract static class Contact {
        /** 담당자·팀 이름. */
        public abstract Property<String> getName();

        /** 연락처 URL. */
        public abstract Property<String> getUrl();

        /** 연락처 이메일. */
        public abstract Property<String> getEmail();
    }

    /** {@code info.license}. */
    public abstract static class License {
        /** 라이선스 이름. 정해야 {@code license} 가 들어간다. */
        public abstract Property<String> getName();

        /** 라이선스 URL. */
        public abstract Property<String> getUrl();
    }

    /** {@code externalDocs}. */
    public abstract static class ExternalDocs {
        /** 문서 URL. 정해야 {@code externalDocs} 가 들어간다. */
        public abstract Property<String> getUrl();

        /** 링크 설명. */
        public abstract Property<String> getDescription();
    }

    /** 태그 하나의 설정 ({@link OpenApiInfo#tag(String, Action)}). */
    public abstract static class Tag {
        /** 태그 설명 (Markdown). */
        public abstract Property<String> getDescription();

        /** 태그의 {@code externalDocs}. */
        @Nested
        public abstract ExternalDocs getExternalDocs();

        /** 태그의 {@code externalDocs} 설정. */
        public void externalDocs(Action<? super ExternalDocs> action) {
            action.execute(getExternalDocs());
        }
    }
}
