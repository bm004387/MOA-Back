#!/usr/bin/env bash

set -Eeuo pipefail

APP_NAME="moa-backend"
IMAGE_NAME="moa-backend:latest"
NETWORK_NAME="${DOCKER_NETWORK_NAME:-moa_default}"
ENV_FILE=".env.prod"
UPLOAD_DIR="${HOME}/moa-uploads"
UPLOAD_CONTAINER_PATH="/app/uploads"
MESSAGE_CACHE_URL="http://127.0.0.1:8080/api/msg/all"
MESSAGE_CACHE_RESPONSE=""
COMMON_CODE_CACHE_URL="http://127.0.0.1:8080/api/common-codes/refresh"
COMMON_CODE_CACHE_RESPONSE=""
DEPLOY_CURL_CONFIG=""
FIREBASE_CREDENTIALS_FILE="${FIREBASE_CREDENTIALS_FILE:-${HOME}/firebase/moa-firebase-service-account.json}"

log() {
    local level="$1"
    shift
    printf '%s | %-5s | %s\n' "$(date '+%Y-%m-%d %H:%M:%S%z')" "$level" "$*"
}

on_error() {
    local exit_code=$?
    log ERROR "배포 실패 (line ${BASH_LINENO[0]:-unknown}, exit=${exit_code})"
    if docker container inspect "$APP_NAME" >/dev/null 2>&1; then
        log ERROR "실패한 컨테이너의 최근 로그"
        docker logs --tail 80 "$APP_NAME" 2>&1 || true
    fi
    exit "$exit_code"
}

cleanup() {
    if [[ -n "$MESSAGE_CACHE_RESPONSE" ]]; then
        rm -f "$MESSAGE_CACHE_RESPONSE"
    fi
    if [[ -n "$COMMON_CODE_CACHE_RESPONSE" ]]; then
        rm -f "$COMMON_CODE_CACHE_RESPONSE"
    fi
    if [[ -n "$DEPLOY_CURL_CONFIG" ]]; then
        rm -f "$DEPLOY_CURL_CONFIG"
    fi
}

trap on_error ERR
trap cleanup EXIT

log INFO "MOA Backend 배포 시작"
log INFO "대상 컨테이너=$APP_NAME, 이미지=$IMAGE_NAME, 네트워크=$NETWORK_NAME"

log INFO "[1/9] main 브랜치 최신 코드 가져오기"
git pull --ff-only origin main
DEPLOY_COMMIT="$(git rev-parse --short HEAD)"
log INFO "배포 커밋=$DEPLOY_COMMIT"

log INFO "[2/9] 배포 환경 확인"
if [[ ! -f "$ENV_FILE" ]]; then
    log ERROR "환경 파일을 찾을 수 없습니다: $ENV_FILE"
    exit 1
fi
DEPLOY_API_TOKEN="$(sed -n 's/^DEPLOY_API_TOKEN=//p' "$ENV_FILE" | tail -n 1)"
DEPLOY_API_TOKEN="${DEPLOY_API_TOKEN%$'\r'}"
if [[ -z "$DEPLOY_API_TOKEN" ]]; then
    if ! command -v openssl >/dev/null 2>&1; then
        log ERROR "DEPLOY_API_TOKEN이 없고 임시 토큰을 만들 openssl도 찾을 수 없습니다."
        exit 1
    fi
    DEPLOY_API_TOKEN="$(openssl rand -hex 32)"
    log INFO "$ENV_FILE에 배포 토큰이 없어 이번 배포에서 사용할 임시 토큰을 생성했습니다."
elif [[ ! "$DEPLOY_API_TOKEN" =~ ^[[:xdigit:]]{64}$ ]]; then
    log ERROR "$ENV_FILE의 DEPLOY_API_TOKEN은 64자리 16진수여야 합니다."
    exit 1
fi
if ! docker network inspect "$NETWORK_NAME" >/dev/null 2>&1; then
    log ERROR "Docker 네트워크를 찾을 수 없습니다: $NETWORK_NAME"
    exit 1
fi

log INFO "[3/9] Gradle 빌드 및 검증"
./gradlew clean build

log INFO "[4/9] Docker 이미지 빌드"
docker build --pull -t "$IMAGE_NAME" .

log INFO "[5/9] 업로드 파일 저장소 준비"
mkdir -p "$UPLOAD_DIR"
UPLOAD_COUNT="$(find "$UPLOAD_DIR" -type f | wc -l | tr -d '[:space:]')"
log INFO "업로드 파일 저장 경로=$UPLOAD_DIR, 파일 수=$UPLOAD_COUNT"

log INFO "[6/9] 기존 애플리케이션 컨테이너 교체"
if docker container inspect "$APP_NAME" >/dev/null 2>&1; then
    docker rm -f "$APP_NAME" >/dev/null
    log INFO "기존 컨테이너를 제거했습니다."
else
    log INFO "기존 컨테이너가 없어 신규 배포로 진행합니다."
fi

log INFO "[7/9] 새 애플리케이션 컨테이너 시작"
DOCKER_RUN_ARGS=(
    -d
    --name "$APP_NAME"
    --network "$NETWORK_NAME"
    --env-file "$ENV_FILE"
    # API 요청 헤더와 백엔드 검증에 동일한 배포 토큰을 명시적으로 전달합니다.
    --env "DEPLOY_API_TOKEN=$DEPLOY_API_TOKEN"
    --env SPRING_PROFILES_ACTIVE=prod
    --env "FILE_STORAGE_ROOT=$UPLOAD_CONTAINER_PATH"
    --publish 127.0.0.1:8080:8080
    --volume "$UPLOAD_DIR:$UPLOAD_CONTAINER_PATH"
    --restart unless-stopped
    --label "com.moa.deploy.commit=$DEPLOY_COMMIT"
)

if grep -Eq '^[[:space:]]*APP_PUSH_ENABLED=true([[:space:]]|$)' "$ENV_FILE"; then
    if [[ ! -f "$FIREBASE_CREDENTIALS_FILE" ]]; then
        log ERROR "푸시 알림이 활성화됐지만 Firebase 서비스 계정 파일을 찾을 수 없습니다: $FIREBASE_CREDENTIALS_FILE"
        exit 1
    fi
    DOCKER_RUN_ARGS+=(
        --env GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/firebase-service-account.json
        --volume "$FIREBASE_CREDENTIALS_FILE:/run/secrets/firebase-service-account.json:ro"
    )
    log INFO "Firebase Admin 자격 증명을 읽기 전용으로 연결합니다."
fi

docker run "${DOCKER_RUN_ARGS[@]}" "$IMAGE_NAME" >/dev/null

log INFO "[8/9] 애플리케이션 기동 및 메시지·공통코드 캐시 갱신"
MESSAGE_CACHE_RESPONSE="$(mktemp)"
READY=0
for attempt in $(seq 1 30); do
    http_status="$(curl --silent --show-error --max-time 5 \
        --output "$MESSAGE_CACHE_RESPONSE" \
        --write-out '%{http_code}' \
        "$MESSAGE_CACHE_URL" 2>/dev/null || true)"
    response_compact="$(tr -d '[:space:]' < "$MESSAGE_CACHE_RESPONSE" 2>/dev/null || true)"

    if [[ "$http_status" == "200" && -n "$response_compact" && "$response_compact" != "{}" ]]; then
        READY=1
        break
    fi

    log WARN "기동 대기 중 (${attempt}/30, HTTP=${http_status:-연결 실패})"
    sleep 2
done

if [[ "$READY" -ne 1 ]]; then
    log ERROR "애플리케이션 준비 확인 또는 메시지 캐시 적재에 실패했습니다."
    exit 1
fi

# /api/msg/all은 DB 메시지로 MSG:ALL 해시를 다시 구성합니다.
# Redis 전체 flush는 로그인 세션 등 다른 Redis 데이터를 지우므로 수행하지 않습니다.
if command -v jq >/dev/null 2>&1; then
    MESSAGE_COUNT="$(jq 'length' "$MESSAGE_CACHE_RESPONSE" 2>/dev/null || printf '0')"
    if [[ ! "$MESSAGE_COUNT" =~ ^[1-9][0-9]*$ ]]; then
        log ERROR "메시지 API에서 DB 메시지를 받지 못했습니다."
        exit 1
    fi
else
    MESSAGE_COUNT="확인 생략(jq 미설치)"
fi
log INFO "메시지 캐시를 DB 기준으로 갱신했습니다 (항목=${MESSAGE_COUNT})."

log INFO "공통코드 Redis 캐시를 DB 기준으로 갱신합니다"
COMMON_CODE_CACHE_RESPONSE="$(mktemp)"
DEPLOY_CURL_CONFIG="$(mktemp)"
chmod 600 "$DEPLOY_CURL_CONFIG"
printf 'header = "X-Deployment-Token: %s"\n' "$DEPLOY_API_TOKEN" > "$DEPLOY_CURL_CONFIG"
COMMON_CODE_HTTP_STATUS="$(curl --silent --show-error --max-time 15 \
    --request POST \
    --config "$DEPLOY_CURL_CONFIG" \
    --output "$COMMON_CODE_CACHE_RESPONSE" \
    --write-out '%{http_code}' \
    "$COMMON_CODE_CACHE_URL" 2>/dev/null || true)"
COMMON_CODE_RESPONSE_COMPACT="$(tr -d '[:space:]' < "$COMMON_CODE_CACHE_RESPONSE" 2>/dev/null || true)"

if [[ "$COMMON_CODE_HTTP_STATUS" != "200" ]]; then
    COMMON_CODE_ERROR_BODY="$(head -c 500 "$COMMON_CODE_CACHE_RESPONSE" 2>/dev/null || true)"
    log ERROR "공통코드 캐시 갱신 요청 실패 (HTTP=${COMMON_CODE_HTTP_STATUS:-연결 실패}, 응답=${COMMON_CODE_ERROR_BODY:-본문 없음})"
    exit 1
fi

if command -v jq >/dev/null 2>&1; then
    COMMON_CODE_COUNT="$(jq 'if type == "array" then length else 0 end' \
        "$COMMON_CODE_CACHE_RESPONSE" 2>/dev/null || printf '0')"
    if [[ ! "$COMMON_CODE_COUNT" =~ ^[1-9][0-9]*$ ]]; then
        log ERROR "공통코드 API가 사용 중인 공통코드 목록을 반환하지 않았습니다."
        exit 1
    fi
else
    if [[ "$COMMON_CODE_RESPONSE_COMPACT" != \[*\] || "$COMMON_CODE_RESPONSE_COMPACT" == "[]" ]]; then
        log ERROR "공통코드 API 응답이 비어 있거나 목록 형식이 아닙니다."
        exit 1
    fi
    COMMON_CODE_COUNT="확인 생략(jq 미설치)"
fi
log INFO "공통코드 Redis 캐시 갱신 완료 (항목=${COMMON_CODE_COUNT})"

log INFO "[9/9] 배포 결과"
docker ps --filter "name=^/${APP_NAME}$" --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
log INFO "배포 완료 (commit=$DEPLOY_COMMIT)"
log INFO "애플리케이션 최근 로그"
docker logs --tail 30 "$APP_NAME" 2>&1 || true
