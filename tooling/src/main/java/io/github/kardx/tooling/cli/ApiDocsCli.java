package io.github.kardx.tooling.cli;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.io.FileTrees;
import io.github.kardx.tooling.internal.io.ToolLog;
import io.github.kardx.tooling.internal.pipeline.ApiDocsConfig;
import io.github.kardx.tooling.internal.pipeline.ApiDocsPipeline;
import io.github.kardx.tooling.internal.pipeline.ConfigMaps;
import io.github.kardx.tooling.internal.sourcedocs.SourceDocsOptions;
import io.github.kardx.tooling.internal.steps.SourceDocsStep;
import java.io.File;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 빌드 도구 없이 문서 후처리를 하는 명령줄. Maven({@code exec-maven-plugin}), Bazel, Gradle 플러그인 없이 쓰는 빌드가 테스트 뒤에
 * 부른다. Gradle 플러그인의 {@code apiDocs} 와 같은 코드로 같은 결과를 만든다.
 *
 * <pre>
 * java -jar kardx-tooling-&lt;version&gt;-all.jar api-docs --config restdocs-tooling.json
 * java -jar … openapi --snippets target/generated-snippets --output target/api-docs --title "Orders API"
 * java -jar … check --output target/api-docs --baseline docs/openapi/openapi3.yaml
 * </pre>
 *
 * 종료 코드: 0 성공, 1 실패(검사 실패, 잘못된 설정·입력), 2 잘못된 사용법.
 */
public final class ApiDocsCli {
    private static final String USAGE = String.join("\n",
        "Usage: java -jar kardx-tooling-<version>-all.jar <command> [options]",
        "",
        "Commands (run after the documentation tests):",
        "  api-docs     everything the config enables: OpenAPI spec, viewer, coverage, HTTP Client, Postman",
        "  openapi      OpenAPI 3 spec from the snippets (epages generator + this library's refinements)",
        "  viewer       static Redoc/Scalar page for the spec (run openapi first)",
        "  coverage     documentation coverage report (coverage/coverage.md, coverage.json)",
        "  http         IntelliJ HTTP Client file (http-client/api.http; run openapi first)",
        "  postman      Postman collection (postman-collection.json)",
        "  check        compare the spec with the committed baseline (fails on changes, see check.failOn)",
        "  baseline     save the spec as the baseline",
        "  source-docs  KDoc/Javadoc description index for the tests (experimental)",
        "",
        "Options:",
        "  --config <file>      JSON settings (same keys as the Maven plugin's configFile)",
        "  --base-dir <dir>     directory relative paths are resolved against (default: current directory)",
        "  --snippets <dir>     snippets written by the tests (default: target/generated-snippets)",
        "  --output <dir>       docs directory (default: target/api-docs); for source-docs: the index file",
        "  --work <dir>         intermediate files (default: kardx next to the snippets directory)",
        "  --language <en|ko>   language of generated texts",
        "  --title, --version, --server <url> (repeatable), --format <yaml|json>   OpenAPI info and servers",
        "  --baseline <file>, --fail-on <any|breaking|none>   for check and baseline",
        "  --source <dir> (repeatable), --method-docs   for source-docs",
        "  --verbose            more output");
    private static final Set<String> VALUED = Set.of("config", "base-dir", "snippets", "output", "work", "language",
        "title", "version", "server", "format", "baseline", "fail-on", "source");
    private static final Set<String> SWITCHES = Set.of("verbose", "method-docs", "help");
    private static final String OPEN_API = "openApi";
    private static final String CHECK = "check";

    private ApiDocsCli() {
    }

    /** 명령을 실행하고 종료 코드로 끝낸다. */
    public static void main(String[] args) {
        int status = run(System.out, System.err, args);

        if (status != 0) {
            System.exit(status);
        }
    }

    /**
     * 명령을 실행한다. 프로세스를 끝내지 않으므로 다른 프로그램 안에서 부를 수 있다.
     *
     * @return 종료 코드: 0 성공, 1 실패, 2 잘못된 사용법
     */
    public static int run(PrintStream out, PrintStream err, String... args) {
        if (args.length == 0 || List.of("help", "--help", "-h").contains(args[0])) {
            out.println(USAGE);

            return args.length == 0 ? 2 : 0;
        }

        try {
            CliArguments arguments = CliArguments.parse(List.of(args), VALUED, SWITCHES);
            ToolLog log = new ConsoleLog(out, err, arguments.flag("verbose"));

            execute(arguments, log);

            return 0;
        } catch (CliArguments.UsageException e) {
            err.println("error: " + e.getMessage());
            err.println("Run with --help for usage.");

            return 2;
        } catch (ToolingException | UncheckedIOException e) {
            err.println("error: " + e.getMessage());

            return 1;
        }
    }

    private static void execute(CliArguments arguments, ToolLog log) {
        File baseDir = new File(valueOr(arguments.value("base-dir"), "."));

        if ("source-docs".equals(arguments.command())) {
            sourceDocs(arguments, baseDir, log);

            return;
        }

        ApiDocsConfig config = ApiDocsConfig.of(settings(arguments, baseDir), baseDir);

        switch (arguments.command()) {
            case "api-docs" -> ApiDocsPipeline.run(config, ApiDocsPipeline.Hints.CLI, log);
            case "openapi" -> ApiDocsPipeline.openApi(config, log);
            case "viewer" -> ApiDocsPipeline.viewer(config, log);
            case "coverage" -> ApiDocsPipeline.coverage(config, ApiDocsPipeline.Hints.CLI, log);
            case "http" -> ApiDocsPipeline.httpClient(config, log);
            case "postman" -> ApiDocsPipeline.postman(config, log);
            case CHECK -> ApiDocsPipeline.check(config, ApiDocsPipeline.Hints.CLI, log);
            case "baseline" -> ApiDocsPipeline.baseline(config, log);
            default -> throw new CliArguments.UsageException("Unknown command '" + arguments.command() + "'");
        }
    }

    /** 설정 파일(있으면) 위에 명령줄 옵션을 덮는다. 모양은 {@link ApiDocsConfig} 의 JSON 과 같다. */
    private static Map<String, Object> settings(CliArguments arguments, File baseDir) {
        String config = arguments.value("config");
        Map<String, Object> settings = config == null ? new LinkedHashMap<>()
            : ConfigMaps.read(resolve(baseDir, config));

        ConfigMaps.put(settings, List.of("snippets"), arguments.value("snippets"));
        ConfigMaps.put(settings, List.of("output"), arguments.value("output"));
        ConfigMaps.put(settings, List.of("work"), arguments.value("work"));
        ConfigMaps.put(settings, List.of("language"), arguments.value("language"));
        ConfigMaps.put(settings, List.of(OPEN_API, "title"), arguments.value("title"));
        ConfigMaps.put(settings, List.of(OPEN_API, "version"), arguments.value("version"));
        ConfigMaps.put(settings, List.of(OPEN_API, "format"), arguments.value("format"));
        ConfigMaps.put(settings, List.of(CHECK, "baseline"), arguments.value("baseline"));
        ConfigMaps.put(settings, List.of(CHECK, "failOn"), arguments.value("fail-on"));

        if (!arguments.values("server").isEmpty()) {
            ConfigMaps.put(settings, List.of(OPEN_API, "servers"), new ArrayList<>(arguments.values("server")));
        }

        return settings;
    }

    private static void sourceDocs(CliArguments arguments, File baseDir, ToolLog log) {
        String output = arguments.value("output");

        if (output == null || arguments.values("source").isEmpty()) {
            throw new CliArguments.UsageException("source-docs needs --output <file> and at least one --source <dir>");
        }

        List<File> sources = new ArrayList<>();

        for (String source : arguments.values("source")) {
            sources.addAll(FileTrees.find(resolve(baseDir, source), List.of("**/*.kt", "**/*.java")));
        }

        SourceDocsOptions options = new SourceDocsOptions(true, true, true, true, arguments.flag("method-docs"));
        File file = resolve(baseDir, output);

        SourceDocsStep.run(sources, options, file, log);
        log.lifecycle("Source docs: {} (pass it to the tests as -D{}={})", file, "kardx.source-docs", file);
    }

    private static File resolve(File baseDir, String path) {
        File file = new File(path);

        return file.isAbsolute() ? file : new File(baseDir, path);
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
