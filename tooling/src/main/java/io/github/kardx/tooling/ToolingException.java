package io.github.kardx.tooling;

/**
 * 도구(OpenAPI 후처리, 검사, 보고서 등)가 사용자 설정이나 입력 파일 때문에 일을 끝낼 수 없을 때 던진다.
 * 메시지는 사용자에게 그대로 보이므로 원인과 고치는 방법을 담는다. Gradle 플러그인에서는 태스크 실패로,
 * Maven 플러그인에서는 {@code MojoFailureException} 으로, 명령줄에서는 종료 코드 1 로 보인다.
 */
public class ToolingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ToolingException(String message) {
        super(message);
    }

    public ToolingException(String message, Throwable cause) {
        super(message, cause);
    }
}
