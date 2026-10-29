# 관측성 지표와 대시보드 운영

"서버는 살아 있는데 파이프라인이 고장 났다"를 보는 층(KAN-38)의 인프라 쪽 구성, 지금 값, 확인 절차다.

관련 코드:

- `../../infra/modules/monitoring/main.tf` (경보), `../../infra/modules/monitoring/dashboard.tf` (대시보드 `accentury-{env}-ops`), `../../infra/modules/monitoring/outputs.tf`
- `../../infra/modules/ai-host/ai-health-metric.sh` (호스트 타이머, 1분), `../../infra/modules/ai-host/docker-compose.ai.yml` (awslogs 드라이버), `../../infra/modules/ai-host/main.tf` (로그 그룹 `/accentury/{env}/ai`)
- `../../ai/app/stages.py` (`STAGES`, 콜드 판정), `../../ai/app/track1.py` (`_stage_ms`), `../../ai/app/tempstore.py`
- `../../backend/src/main/java/app/accentury/backend/observability/ServiceMetrics.java` (지표 이름 정본), `../../backend/src/main/java/app/accentury/backend/common/CloudWatchMetricsConfig.java` (`accentury.*`만 내보냄)

설계 근거와 지표 카탈로그, correlationId로 세션 하나를 추적하는 절차의 정본은 [docs/wiki/observability.md](../wiki/observability.md)다.
여기는 무엇이 생기고 어떻게 확인하는지를 적는다. 실증 기록은 [docs/wiki/infra-history.md](../wiki/infra-history.md)의 KAN-203
로그 전송 차단 실증, KAN-204, KAN-293 항목에 있다.

죽음(서버 다운)은 사용자가 바로 알지만, 파이프라인 고장은 대기 화면이 길어지는 것으로만 드러나 아무도 신고하지 않는다. 무인
운영에서 조용히 나빠지는 구간이라 이 층을 따로 둔다. "서버가 죽었다"를 알리는 경보는 [alarms.md](alarms.md)에 있다.

## 무엇이 생기는가

| 리소스 | 이름 |
| --- | --- |
| 대시보드 1개 | `accentury-{env}-ops` (환경 루트의 `terraform output -raw dashboard_url`이 바로가기) |
| 관측성 경보 6종 | `ai-temp-residue`, `analysis-backlog-high`, `analysis-timeouts-high`, `analysis-unscorable-high`(KAN-272), `translation-quota-exhausted`(KAN-266), `result-wait-high`(KAN-284) |
| 로그 그룹 1개 | `/accentury/{env}/ai` (KAN-203, 아래 "ai 컨테이너 로그"). backend `/accentury/{env}/backend`는 fargate 모듈(KAN-165)이 만든다 |

경보는 "서버가 죽었다" 경보와 **같은 SNS 토픽**(`accentury-{env}-alerts`)으로 간다. 심각도별 채널을 나누지 않는다. 3인 팀에
채널이 여럿이면 어느 쪽도 보지 않게 된다. `result-wait-high`는 슬랙 `#infra-alerts`에도 간다 (KAN-284).

경보 개수 세는 법:

| 범위 | 개수 |
| --- | --- |
| monitoring 모듈이 만드는 경보 | 16개 |
| 모듈 출력 `alarm_names` | 15개 (`translation-quota-exhausted`가 빠져 있다) |
| `describe-alarms --alarm-name-prefix accentury-{env}-` | 18개 (모듈 16개 + ai-host의 `ai-scale-out`, `ai-scale-in`) |

backend 목표 추적 경보 2개는 이름이 `TargetTracking-`로 시작해 prefix 조회에 잡히지 않는다 (envs 출력 `backend_autoscaling_alarm_names`, 모듈 안에서는 fargate 출력 `autoscaling_alarm_names`).

### 관측성 경보 6종

| 경보 | 지표 | 조건 | 결측 처리 |
| --- | --- | --- | --- |
| `ai-temp-residue` | `accentury/ai` `TempFiles` (차원 env) | 5분 최대 >= 20이 2회 연속 | `notBreaching` |
| `analysis-backlog-high` | `accentury/backend` `accentury.analysis.processing.value` | 1분 최대 >= 12가 5회 연속 (`analysis_backlog_threshold` 기본 12) | `notBreaching` |
| `analysis-timeouts-high` | `accentury.analysis.timeouts.count`의 두 사유(stuck, lost) 합 | 5분 합계 > 5 | `notBreaching` |
| `analysis-unscorable-high` (KAN-272) | `accentury.analysis.judged.count`의 `reason=unscorable` | 5분 합계 > 5 | `notBreaching` |
| `translation-quota-exhausted` (KAN-266) | `accentury.translation.llm.calls.count`의 `outcome=rate_limited` | 5분 합계 > 0 (`translation_quota_exhausted_threshold` 기본 0, 한 번이라도 오면 운다) | `notBreaching` |
| `result-wait-high` (KAN-284) | `accentury.result.wait.percentile.value` (`phi=0.95`, 단위 ms) | 1분 최대 >= 30초가 3회 연속 (`result_wait_threshold_seconds`) | `notBreaching` |

`analysis-backlog-high`의 12는 폴링 혼잡 임계치(`congestion-threshold` 6, KAN-172)의 두 배다. 임계를 다시 보는 시점은
wiki의 "임계치를 언제 다시 보는가"에 있다.

`analysis-unscorable-high`가 울면: AI가 분석 결과에 NaN을 내는 경우(`ANALYSIS_UNSCORABLE`)는 계약대로 온 판정이라 회로에 실패로
세지 않고 사용자에게 재녹음만 열린다. 모델이나 참조 데이터 회귀로 모든 발화가 그렇게 되어도 다른 경보는 울지 않는다. AI 로그의
`채점 불가`(어느 값인지 `fields`, 어느 문장인지 `scriptKey`와 `testVersion`)와 backend 로그의 `채점 불가 판정`을 본다.

`translation-quota-exhausted`가 울면: Gemini 무료 등급이 429를 돌려줬다는 뜻이고 그동안의 번역 요청은 전부
503 `TRANSLATION_UNAVAILABLE`이다. 분당 한도(RPM)면 몇 분 뒤 풀리고, 일일 한도(RPD)면 태평양 시간 자정까지 풀리지 않는다.
backend 로그의 "Gemini 무료 한도 소진(429)" 빈도와 `accentury.translation.requests`로 가른다.

**6종 모두 결측을 장애로 세지 않는다.** backend가 죽어 지표가 끊기는 것은 `no-healthy-target`이, AI 호스트가 죽는 것은
`ai-unhealthy`가 이미 잡는다. 같은 사건에 메일을 여러 통 보내지 않는다.

## AI 호스트 지표가 올라오는 경로

`ai-health-metric.sh`(systemd 타이머, 1분)가 한 회차에 하는 일:

1. `GET /internal/v0/health` (토큰 불필요) -> `Healthy` 0|1. 같은 회차에 호스트의 `df /`와 `/proc/meminfo`에서
   `RootDiskUsedPercent`, `MemoryUsedPercent`도 올린다 (KAN-36 B단계, 토큰도 컨테이너도 필요 없다).
2. `GET /internal/v0/metrics` (**토큰 필요**) -> `TempFiles`, `TempOldestAge`, `TempScanFailures`.
3. `GET /internal/v0/metrics/stages` (**토큰 필요**, KAN-204) -> `StageDuration` (아래 절).

토큰이 필요한 쪽의 토큰은 SSM을 매분 다시 읽지 않고 `accentury-up.sh`가 기동 때 만들어 둔 `/run/accentury/ai.env`(root 전용
tmpfs)에서 가져온다. 컨테이너에 들어가는 것과 같은 값이라 IAM도 SSM 호출도 늘지 않는다. 그 파일이 없거나(첫 부팅, compose 기동
전) 조회에 실패하면 **임시파일 지표만 건너뛴다.** health는 그것과 무관하게 계속 나간다.

**값을 못 읽었을 때 0을 올리지 않는다.** 0은 "깨끗하다"라서, 조회가 막힌 바로 그 순간에 경보가 거꾸로 조용해진다
(`ai/app/tempstore.py`가 훑기 실패 시 잔존 값을 덮지 않는 것과 같은 판단이다).

호스트에서 직접 확인:

```bash
# ai 호스트 (SSM Session Manager)
sudo /opt/accentury/ai-health-metric.sh
# ai health=200 -> Healthy=1, rootDisk=41%, mem=38%, tempFiles=0, tempOldestAge=0, tempScanFailures=0, stageSeries=0 (accentury/ai env=staging)
```

못 읽은 값은 숫자 대신 `미확인`으로 찍힌다 (위 숫자는 예시다).

## AI 추론 단계별 지연 지표 (KAN-204, KAN-293)

문항당 처리 시간이 **어느 단계에 쓰였는지**를 같은 경로로 올린다. GPU 전환(KAN-57), MFA 상주화, 인스턴스 상향(KAN-36)의 기대
효과를 추정이 아니라 이 값으로 판단한다.

| 항목 | 값 |
| --- | --- |
| 지표 | `accentury/ai` `StageDuration` (단위 Milliseconds) |
| 차원 | `env`, `stage`, `warm` |
| `stage` | `lockWait`, `workerLoad`, `model`, `transcribe`, `gate`, `align`, `f0`, `scoring`, `total` |
| `warm` | `cold`(그 워커에서 MFA `align`이 처음 실제로 돈 요청까지), `warm` |
| 대시보드 | `accentury-{env}-ops` 4행 (P50 웜, P95 웜, 콜드 스타트와 대기) |

`stage` 이름의 정본은 `ai/app/stages.py`의 `STAGES`다. 바깥 넷(`lockWait`, `workerLoad`, `model`, `total`)은 어댑터가 잰다. 안쪽
다섯(`transcribe`~`scoring`)은 전달본 `Track1Scorer.score` 안쪽 구간이라 전달본이 봉투의 `detail.stageMs`로 싣고, 어댑터
(`app.track1._stage_ms`)가 그것을 읽는다. 옛 이름(`stt`, `mfa`, `score`)으로 온 값은 어댑터가 이 이름으로 옮기고, 모델 태그
`f77011c`부터는 전달본이 다섯 이름으로 직접 싣는다. 2026-10-24(KAN-293)부터 안쪽 다섯도 올라온다. `workerLoad`는 적재가 실제로
일어난 요청에만 있어 평소에는 비어 있는 것이 정상이다.

콜드 판정: 가중치는 워밍업에서 이미 올라와 있어도 MFA 첫 실행이 첫 채점에서 한 번 크게 걸린다. 그래서 어댑터는 그 워커에서
`align`이 처음 돈 요청까지를 `cold`로 두고, 게이트에서 탈락해 MFA가 돌지 않은 요청은 콜드를 소비하지 않는다
(`Track1Engine._score`).

**관측값을 낱개로 올린다**(`Value`가 아니라 `Values`). 평균이나 합만 올리면 CloudWatch가 p50과 p95를 계산할 수 없는데, 필요한
값이 바로 그 백분위다. backend 쪽 백분위가 "가장 나쁜 태스크의 P95"인 것과 달리 여기는 전체 표본에서 계산된 진짜 백분위다.

**`/internal/v0/metrics/stages`는 읽으면 비워진다.** 소비자가 이 타이머 하나뿐이라는 전제다. 그래서 그 회차의 `put-metric-data`가
실패하면 그 1분의 표본은 사라진다. 재시도 큐를 두지 않는 이유는 CloudWatch가 오래 막혔을 때 그 대가를 추론 프로세스가 메모리로
치르기 때문이다. 앱은 계열당 150개(= `Values` 하나의 API 상한)에서 오래된 표본부터 버린다.

## ai 컨테이너 로그 (KAN-203)

지표가 "무엇이 나빠졌는가"라면 이 로그는 **"왜 그렇게 됐는가"**다. 추론 실패의 원인(내용 게이트 판정 실패, 워커 500, 워커
재적재, 시간 초과 503)은 ai 컨테이너 로그에만 있다. CloudWatch로 보내기 때문에 ASG가 인스턴스를 교체해도(`ai-unhealthy`, instance
refresh) 원인이 인스턴스와 함께 사라지지 않는다.

| 항목 | 값 |
| --- | --- |
| 로그 그룹 | `/accentury/{env}/ai`. Terraform `modules/ai-host`가 만든다 |
| 보존 | 14일. backend `/accentury/{env}/backend`와 같은 값이고 두 환경이 같다 (`log_retention_days` 기본값, tfvars로 덮지 않는다) |
| 스트림 | `ai/<인스턴스 ID>`. 교체 전후가 갈리므로 **교체 뒤에도 이전 인스턴스의 로그가 남는다** |
| 드라이버 | compose `logging`의 `awslogs` + `mode: non-blocking` + `max-buffer-size: 4m` |
| 호스트 디스크 | `docker logs`용 사본만 남는다. 상한을 `cache-max-size: 10m` x `cache-max-file: 5` = 50MB로 고정한다 (**기본값은 20MB x 5 = 100MB**라 그냥 두면 디스크를 더 쓴다) |
| 권한 | 인스턴스 역할에 `logs:CreateLogStream`, `logs:PutLogEvents`뿐이다. 쓰기 전용이고 그룹 생성과 되읽기 권한은 없다 |

**`docker logs`는 그대로 읽힌다.** 도커의 이중 로깅 덕이다. 원격 드라이버를 쓰면 엔진이 `local` 드라이버로 사본을 하나 더
남기고 `docker logs`가 그것을 읽는다 (사본은 `/var/lib/docker/containers/<id>/container-cached.log`). 그래서 배포 파이프라인의
unhealthy 진단(`deploy.yml`의 `docker logs --tail 60`)도 그대로 쓴다.

한계: **원격 전송이 실패한 줄은 사본에도 안 쌓인다.** CloudWatch가 막힌 구간은 `docker logs`로도 안 보인다 (그 줄들은 드라이버
버퍼에 남아 복구 뒤 CloudWatch로 늦게 간다). 사본은 링 버퍼라 오래된 줄은 밀려 나간다. **교체를 넘겨 보존되는 것은 CloudWatch
쪽뿐이다.**

호스트나 로컬에서 CloudWatch로 직접 볼 때 (교체된 인스턴스의 로그도 이 경로다):

```bash
# 지금 이 인스턴스의 마지막 60줄
aws logs get-log-events --region ap-northeast-2 --log-group-name /accentury/staging/ai \
  --log-stream-name "ai/<인스턴스 ID>" --limit 60 --query 'events[].message' --output text | tr '\t' '\n'
```

correlationId 하나로 backend와 ai를 잇는 Logs Insights 질의는 wiki의 "실패한 세션 하나를 추적하는 절차"가 정본이다. 그룹
선택에서 접두사 `/accentury/{env}/` 한 번으로 두 그룹이 함께 걸리도록 이름을 맞췄다.

로그 전송이 막혔을 때의 성질:

- `mode: non-blocking`이라 버퍼(4MB)가 찰 때까지 컨테이너 프로세스는 로그 쓰기에 붙들리지 않고, 차면 **새 줄을 버린다.** 추론이
  밀리는 것보다 로그 몇 줄을 잃는 것이 낫다는 판단이다. 짧은 CloudWatch 장애로는 로그를 잃지 않고(버퍼 안이면 늦게 온다), 로그가
  못 나가는 것이 추론을 멈추지 않는다 (실측은 history의 KAN-203 로그 전송 차단 실증).
- 차단 중에 컨테이너를 다시 만들면 그 컨테이너 버퍼에 남아 있던 줄은 사라진다.
- 로그 드라이버는 컨테이너가 아니라 **호스트 dockerd**에서 나간다. 컨테이너 egress 가드(iptables `DOCKER-USER`, KAN-36)는 컨테이너
  브리지에서 시작되는 연결만 보므로 로그 전송과 무관하다.

### 로그 전송 차단 재현 절차

로깅 드라이버를 바꾸거나 `mode`를 건드릴 때 같은 절차로 "로그가 안 나가도 추론이 멈추지 않는가"를 다시 본다.

**`/etc/hosts`만 무효화해서는 막히지 않는다.** dockerd가 이미 맺어 둔 연결을 계속 쓰기 때문에 새 DNS 조회가 일어나지 않는다.
실제 도달 불가를 만들려면 호스트 `iptables OUTPUT`에서 그 IP의 443을 끊는다. **반드시 되돌린다. 안전망을 먼저 건다.**
**다른 서비스와 IP가 겹치면 SSM 세션까지 끊기므로 겹침 검사가 먼저다.**

```bash
# ai 호스트 (SSM Session Manager)
EP=logs.ap-northeast-2.amazonaws.com
getent ahostsv4 $EP | awk '{print $1}' | sort -u > /tmp/logs-ips
for svc in ssm ssmmessages ec2messages monitoring api.ecr; do
  getent ahostsv4 $svc.ap-northeast-2.amazonaws.com | awk '{print $1}'
done | sort -u > /tmp/other-ips
comm -12 /tmp/logs-ips /tmp/other-ips    # 출력이 있으면 하지 않는다

# 안전망: 세션이 끊겨도 300초 뒤 스스로 풀린다
setsid bash -c 'sleep 300; for ip in $(cat /tmp/logs-ips); do
  while iptables -C OUTPUT -d $ip -p tcp --dport 443 -j DROP 2>/dev/null; do
    iptables -D OUTPUT -d $ip -p tcp --dport 443 -j DROP; done; done' >/dev/null 2>&1 </dev/null &

for ip in $(cat /tmp/logs-ips); do iptables -I OUTPUT -d $ip -p tcp --dport 443 -j DROP; done
```

차단 중에 볼 것: `docker compose up -d --force-recreate`가 막히지 않는가, health UP까지 걸리는 시간, 분석 요청 지연, 401 경로
지연, 복구 뒤 차단 중의 줄이 늦게라도 도착하는가, 컨테이너 egress 가드(`DOCKER-USER`)가 그대로인가.

## 배포 뒤 확인 절차

```bash
ENV=staging
REGION=ap-northeast-2

# 1. 경보 18개(모듈 16 + ai-host 확장 2)가 다 있고 INSUFFICIENT_DATA를 벗어났는가
aws cloudwatch describe-alarms --region "$REGION" --alarm-name-prefix "accentury-$ENV-" \
  --query 'MetricAlarms[].[AlarmName,StateValue]' --output table

# 2. backend 지표가 실제로 올라오는가 (기동 뒤 2분 이상 기다린다 - 발행 주기가 1분이다)
aws cloudwatch list-metrics --region "$REGION" --namespace accentury/backend \
  --query 'Metrics[].MetricName' --output text | tr '\t' '\n' | sort -u

# 3. AI 지표도 올라오는가
aws cloudwatch list-metrics --region "$REGION" --namespace accentury/ai \
  --query 'Metrics[].MetricName' --output text | tr '\t' '\n' | sort -u

# 3-1. 단계별 지연이 올라오는가 (KAN-204). 분석을 한 건이라도 돌린 뒤에 본다 -
#      표본이 없는 계열은 지표 자체가 생기지 않는다. 안쪽 다섯(transcribe~scoring)도 보여야 한다 (KAN-293).
aws cloudwatch list-metrics --region "$REGION" --namespace accentury/ai \
  --metric-name StageDuration --query 'Metrics[].Dimensions[?Name==`stage`].Value' --output text

# 4. 대시보드가 값을 그리는가 - 스모크 한 바퀴(KAN-138) 뒤에 열어 본다
terraform output -raw dashboard_url

# 5. ai 컨테이너 로그가 오는가 (KAN-203). 스트림 이름의 인스턴스 ID가 지금 도는 인스턴스와 같아야 한다
aws logs describe-log-streams --region "$REGION" --log-group-name "$(terraform output -raw ai_log_group)" \
  --query 'logStreams[].[logStreamName,lastEventTimestamp]' --output table
```

2번에 `accentury.http.requests.percentile.value`가 없으면 백분위 게이지 등록이 빠진 것이다. CloudWatch 레지스트리는 Timer의
백분위를 스스로 내보내지 않으므로, 그 게이지가 없으면 대시보드의 P95만 영영 빈 채로 나머지는 멀쩡해 보인다
(`ServiceMetrics.registerPercentiles`).

그 P95는 **태스크마다 계산된 값을 `Maximum`으로 접은 것**이라 "가장 나쁜 태스크의 P95"다. NFR-PF-01 판단에 안전한 쪽을 택한 것이고,
표본이 작은 새 태스크에서 튈 수 있다는 읽는 법까지 wiki에 있다.

## 비용

커스텀 지표는 이름 x 차원 조합 하나에 월 0.30달러, 표준 경보는 지표 하나에 월 0.10달러다. 이 층이 쓰는 조합을 코드 기준으로
세면 아래와 같다 (청구서로 대조한 값은 아니다).

| 묶음 | 조합 수 | 근거 |
| --- | --- | --- |
| KAN-38 기본 (backend 미터 26 + `TempFiles` 등 3) | 29 | wiki "수집 경로" 표 |
| `RootDiskUsedPercent`, `MemoryUsedPercent` (KAN-36 B단계) | 2 | 호스트 타이머 |
| `accentury.analysis.judged` (KAN-272) | 3 | `reason` 3종 |
| `accentury.result.wait` (KAN-284) | 5 | Timer 4 + 백분위 1 |
| `StageDuration` (KAN-204, KAN-293) | 17 | 바깥 콜드/웜 7 + 안쪽 다섯 x 2 |
| `accentury.translation.requests`, `.llm.calls`, `.records` (KAN-266) | 최대 9 (staging 7) | `result` 4종, `outcome` 3종, `records`의 `result` 2종(기록 버킷이 있는 prod만) |
| 합계 | 약 65 (staging 약 63) | 환경당 월 약 19달러 |

경보 6종은 환경당 월 약 0.70달러다 (`analysis-timeouts-high`는 지표 2개를 세는 math 경보라 0.20달러). 대시보드는 계정당 3개까지
무료라 이 하나는 요금이 없다.

ai 컨테이너 로그(KAN-203)는 요금이 수집량에 붙는다. 이 컨테이너가 남기는 것은 기동 줄 몇 개와 요청당 종료 줄 한 줄(uvicorn 접근
로그 포함 두어 줄)이라, 하루 수백 문항 수준에서는 월 1MB대이고 수집 요금(GB당 약 0.76달러)과 14일 보관 요금 모두 반올림하면
0달러에 가깝다. 늘어날 자리는 로그 수준을 DEBUG로 내리거나(`ACCENTURY_AI_LOG_LEVEL`) 요청/응답 덤프를 켜는 변경이므로, 그때
이 줄을 다시 본다.

지표 수가 곧 요금이므로 **태그는 값이 다섯 이하로 닫힌 것만 쓴다.** 세션 ID나 IP를 태그로 쓰면 요금이 트래픽에 비례한다. 이
규칙은 backend의 `ServiceMetrics` javadoc에도 있고, `CloudWatchMetricsConfigTest`가 이름 목록이 내보내기 필터를 통과하는지까지
본다. AI 쪽은 `ai/app/stages.py`가 `STAGES`에 없는 단계 이름을 버린다.
