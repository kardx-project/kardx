#!/usr/bin/env bash
# build/staging-deploy (./gradlew publishToStaging 결과)를 묶어 Maven Central(Sonatype Central Portal)에 올린다.
#
#   scripts/central-upload.sh            묶고 검사한 뒤 업로드하고, 검증이 끝날 때까지 상태를 기다린다
#   scripts/central-upload.sh --dry-run  묶고 검사만 한다 (계정 불필요)
#
# 환경 변수: CENTRAL_USERNAME, CENTRAL_PASSWORD (Central Portal 의 사용자 토큰),
#           CENTRAL_PUBLISHING_TYPE (USER_MANAGED 기본: Portal 화면에서 Publish 를 누른다 / AUTOMATIC: 검증 뒤 바로 배포).
# 상태가 VALIDATED·PUBLISHED 면 성공, FAILED 면 오류를 출력하고 실패한다. AUTOMATIC 은 VALIDATED 뒤 배포가 이어진다.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
staging="$root/build/staging-deploy"
bundle="$root/build/central-bundle.zip"
api="https://central.sonatype.com/api/v1/publisher"
poll_interval=10
poll_attempts=90

dry_run=false
case "${1:-}" in
    "") ;;
    --dry-run) dry_run=true ;;
    *)
        echo "usage: $0 [--dry-run]" >&2
        exit 2
        ;;
esac

version="$(sed -n 's/^version=//p' "$root/gradle.properties")"
if [[ -z "$version" ]]; then
    echo "error: no version in gradle.properties" >&2
    exit 1
fi

if [[ ! -d "$staging/io" ]]; then
    echo "error: $staging/io not found. Run ./gradlew publishToStaging first" >&2
    exit 1
fi

# 아티팩트마다 서명(.asc)과 체크섬(.md5, .sha1)이 옆에 있어야 Central 이 받는다
missing=()
artifacts=0
while IFS= read -r -d '' file; do
    artifacts=$((artifacts + 1))
    for suffix in asc md5 sha1; do
        [[ -f "$file.$suffix" ]] || missing+=("${file#"$staging"/}.$suffix")
    done
done < <(find "$staging/io" -type f \( -name '*.jar' -o -name '*.pom' -o -name '*.module' \) -print0)

if [[ $artifacts -eq 0 ]]; then
    echo "error: no .jar/.pom/.module files under $staging/io" >&2
    exit 1
fi

if [[ ${#missing[@]} -gt 0 ]]; then
    echo "error: ${#missing[@]} signature/checksum file(s) missing (was signingInMemoryKey set?):" >&2
    printf '  %s\n' "${missing[@]}" >&2
    exit 1
fi

# zip 의 최상위는 io/... (Maven 저장소 배치). maven-metadata*.xml 은 Central 이 만들므로 뺀다
rm -f "$bundle"
(cd "$staging" && zip -qr "$bundle" io -x '*maven-metadata*.xml*')
echo "bundle: $bundle ($artifacts artifacts, $(zipinfo -1 "$bundle" | grep -cv '/$') files)"

if [[ "$dry_run" == true ]]; then
    echo "dry run: not uploaded"
    exit 0
fi

: "${CENTRAL_USERNAME:?CENTRAL_USERNAME is not set}"
: "${CENTRAL_PASSWORD:?CENTRAL_PASSWORD is not set}"
publishing_type="${CENTRAL_PUBLISHING_TYPE:-USER_MANAGED}"
# 리눅스 base64 는 76자마다 줄을 바꾸므로 줄바꿈을 지운다. 토큰은 출력하지 않는다
token="$(printf '%s:%s' "$CENTRAL_USERNAME" "$CENTRAL_PASSWORD" | base64 | tr -d '\n')"

deployment_id="$(curl -fsS -X POST \
    -H "Authorization: Bearer $token" \
    -F "bundle=@$bundle" \
    "$api/upload?name=kardx-$version&publishingType=$publishing_type")"
echo "deployment id: $deployment_id ($publishing_type)"

for ((attempt = 1; attempt <= poll_attempts; attempt++)); do
    status="$(curl -fsS -X POST -H "Authorization: Bearer $token" "$api/status?id=$deployment_id")"
    state="$(printf '%s' "$status" | python3 -c 'import json, sys; print(json.load(sys.stdin)["deploymentState"])')"
    echo "state: $state"

    case "$state" in
        VALIDATED)
            if [[ "$publishing_type" == USER_MANAGED ]]; then
                echo "validated. Press Publish at https://central.sonatype.com/publishing/deployments"
            fi
            exit 0
            ;;
        PUBLISHED)
            exit 0
            ;;
        FAILED)
            echo "error: deployment $deployment_id failed:" >&2
            printf '%s' "$status" | python3 -c 'import json, sys; print(json.dumps(json.load(sys.stdin).get("errors"), indent=2))' >&2
            exit 1
            ;;
    esac

    sleep "$poll_interval"
done

echo "error: deployment $deployment_id still $state after $((poll_interval * poll_attempts)) s" >&2
exit 1
