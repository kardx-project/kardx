package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.DESCRIPTION;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.i18n.DocsText;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code openApiInfo { }} 값({@link OpenApiInfoSettings})을 문서에 넣는다: {@code info} 의 description·termsOfService·
 * contact·license, 최상위 {@code externalDocs}, 태그 설명·{@code externalDocs}·순서, {@code x-tagGroups}.
 * 태그는 operation 이 쓰는 것만 다룬다 (operation 없는 태그는 뷰어에 빈 절이 되므로 경고하고 넣지 않는다).
 */
final class OpenApiInfoMetadata {
    private static final String INFO = "info";
    private static final String TAGS = "tags";
    private static final String NAME = "name";
    private static final String URL = "url";
    private static final String EXTERNAL_DOCS = "externalDocs";
    private static final String TAG_GROUPS = "x-tagGroups";
    private static final String URL_SUFFIX = ".url";
    private static final String DESCRIPTION_SUFFIX = ".description";

    private OpenApiInfoMetadata() {
    }

    /** @return 넣거나 바꾼 항목 수 */
    static int transform(
        Map<String, Object> document, OpenApiInfoSettings settings, DocsLanguage language, List<String> warnings) {
        Map<String, Object> info = SpecMaps.child(document, INFO);
        int changed = put(info, DESCRIPTION, settings.description()) + put(info, "termsOfService",
            settings.termsOfService());
        Map<String, Object> contact = present(settings.contact(), List.of(NAME, URL, "email"));

        if (!contact.isEmpty()) {
            info.put("contact", contact);
            changed++;
        }

        changed += named(info, "license", present(settings.license(), List.of(NAME, URL)), NAME, "license",
            warnings);
        changed += named(document, EXTERNAL_DOCS, externalDocs(settings.externalDocs()), URL, EXTERNAL_DOCS,
            warnings);

        return changed + tags(document, settings, language, warnings);
    }

    /** 태그 설명·externalDocs 를 넣고 순서를 맞춘 뒤 x-tagGroups 를 만든다. */
    private static int tags(
        Map<String, Object> document, OpenApiInfoSettings settings, DocsLanguage language, List<String> warnings) {
        List<Map<String, Object>> tags = SpecMaps.maps(document.get(TAGS));
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        Set<String> unknown = new LinkedHashSet<>();
        int changed = 0;

        tags.forEach(tag -> byName.putIfAbsent(String.valueOf(tag.get(NAME)), tag));

        for (String name : configuredTags(settings)) {
            Map<String, Object> tag = byName.get(name);

            if (tag == null) {
                unknown.add(name);
                continue;
            }

            changed += put(tag, DESCRIPTION, settings.tagDescriptions().get(name));
            changed += named(tag, EXTERNAL_DOCS, externalDocs(tagExternalDocs(settings, name)), URL,
                "tag '" + name + "' externalDocs", warnings);
        }

        if (!byName.isEmpty() && !settings.tagOrder().isEmpty()) {
            document.put(TAGS, new ArrayList<Object>(ordered(byName, settings.tagOrder())));
            changed++;
        }

        changed += tagGroups(document, byName.keySet(), settings.tagGroups(), language, unknown);
        unknown.forEach(name -> warnings.add("tag '" + name + "' is not used by any documented operation, so it "
            + "was left out. Tags come from the test class @DisplayName or openApi { tag(...) }"));

        return changed;
    }

    /** 설정에 나온 태그 이름 (순서, 설명, externalDocs). */
    private static Set<String> configuredTags(OpenApiInfoSettings settings) {
        Set<String> names = new LinkedHashSet<>(settings.tagOrder());

        names.addAll(settings.tagDescriptions().keySet());
        settings.tagExternalDocs()
            .keySet()
            .forEach(key -> names.add(key.substring(0, key.lastIndexOf('.'))));

        return names;
    }

    private static Map<String, String> tagExternalDocs(OpenApiInfoSettings settings, String name) {
        Map<String, String> docs = new LinkedHashMap<>();

        docs.put(URL, settings.tagExternalDocs().get(name + URL_SUFFIX));
        docs.put(DESCRIPTION, settings.tagExternalDocs().get(name + DESCRIPTION_SUFFIX));

        return docs;
    }

    /** {@code order} 에 적은 태그를 그 순서로 앞에, 나머지는 원래 순서로 뒤에. */
    private static List<Map<String, Object>> ordered(Map<String, Map<String, Object>> byName, List<String> order) {
        List<Map<String, Object>> sorted = new ArrayList<>();

        order.stream()
            .distinct()
            .filter(byName::containsKey)
            .forEach(name -> sorted.add(byName.get(name)));
        byName.values()
            .stream()
            .filter(tag -> !sorted.contains(tag))
            .forEach(sorted::add);

        return sorted;
    }

    /** 정한 그룹을 넣고, 어느 그룹에도 없는 태그는 마지막 그룹 {@link DocsText#TAG_GROUP_OTHER} 에 모은다. */
    private static int tagGroups(Map<String, Object> document, Set<String> used, Map<String, List<String>> groups,
        DocsLanguage language, Set<String> unknown) {
        if (groups.isEmpty()) {
            return 0;
        }

        List<Object> result = new ArrayList<>();
        Set<String> grouped = new LinkedHashSet<>();

        groups.forEach((name, tags) -> {
            List<String> kept = new ArrayList<>();

            for (String tag : tags) {
                if (!used.contains(tag)) {
                    unknown.add(tag);
                } else if (grouped.add(tag)) {
                    kept.add(tag);
                }
            }

            if (!kept.isEmpty()) {
                result.add(group(name, kept));
            }
        });

        List<String> rest = used.stream()
            .filter(tag -> !grouped.contains(tag))
            .toList();

        if (!rest.isEmpty()) {
            result.add(group(DocsText.TAG_GROUP_OTHER.in(language), rest));
        }

        document.put(TAG_GROUPS, result);

        return result.size();
    }

    private static Map<String, Object> group(String name, List<String> tags) {
        Map<String, Object> group = new LinkedHashMap<>();

        group.put(NAME, name);
        group.put(TAGS, new ArrayList<Object>(tags));

        return group;
    }

    /** {@code externalDocs} 는 OpenAPI 에서 description 이 url 앞에 온다. */
    private static Map<String, Object> externalDocs(Map<String, String> values) {
        return present(values, List.of(DESCRIPTION, URL));
    }

    /**
     * [values] 에 [required] 키가 있으면 [parent][key] 로 넣는다. 다른 값만 있으면 넣을 수 없으므로 [label] 로 경고한다.
     *
     * @return 넣었으면 1
     */
    private static int named(Map<String, Object> parent, String key, Map<String, Object> values, String required,
        String label, List<String> warnings) {
        if (values.isEmpty()) {
            return 0;
        }

        if (!values.containsKey(required)) {
            warnings.add(label + " needs a " + required + " (OpenAPI requires it), so it was left out: " + values);

            return 0;
        }

        parent.put(key, values);

        return 1;
    }

    /** [keys] 순서로 비어 있지 않은 값만. */
    private static Map<String, Object> present(Map<String, String> values, List<String> keys) {
        Map<String, Object> present = new LinkedHashMap<>();

        for (String key : keys) {
            String value = values.get(key);

            if (value != null && !value.isBlank()) {
                present.put(key, value);
            }
        }

        return present;
    }

    /** @return 넣었으면 1 (빈 값은 넣지 않는다) */
    private static int put(Map<String, Object> parent, String key, String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        parent.put(key, value);

        return 1;
    }
}
