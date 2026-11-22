package io.github.kardx.tooling.internal.aggregate;

import io.github.kardx.tooling.ToolingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 집계 결과에서 프로젝트마다 문서를 담는 디렉터리 이름. */
public final class ProjectDirectories {
    private ProjectDirectories() {
    }

    /** {@code :services:member} → {@code services/member}, 루트 프로젝트는 {@code root}. */
    public static String directoryName(String projectPath) {
        String trimmed = projectPath.startsWith(":") ? projectPath.substring(1) : projectPath;

        return trimmed.isEmpty() ? "root" : trimmed.replace(':', '/');
    }

    /**
     * 두 프로젝트가 같은 디렉터리로 가면 실패한다 (루트 프로젝트와 {@code :root}). 한쪽 문서가 다른 쪽을 덮어쓴다.
     * {@code :services} 와 {@code :services:member} 처럼 한 디렉터리가 다른 디렉터리 안에 있는 것은 괜찮다.
     */
    public static void checkDistinct(List<String> projectPaths) {
        Map<String, String> owners = new HashMap<>();

        for (String path : projectPaths) {
            String directory = directoryName(path);
            String owner = owners.putIfAbsent(directory, path);

            if (owner != null) {
                throw new ToolingException("apiDocsAggregate: projects " + owner + " and " + path
                    + " both go to the directory " + directory + "/ of the aggregated docs. "
                    + "Aggregate only one of them.");
            }
        }
    }
}
