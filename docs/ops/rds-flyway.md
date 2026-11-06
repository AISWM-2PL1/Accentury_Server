# RDS 운영자 접속과 Flyway 재베이스라인

평시에는 사람이 붙을 길이 없는 RDS에 임시 통로를 열고 닫는 절차와, Flyway 이력을 재베이스라인할 때의 순서, 복구 경로, 함정이다.

관련 코드:

- `../../infra/modules/network/main.tf` (`rds_from_backend`, `accentury-{env}-rds-sg`, `accentury-{env}-ai-sg`)
- `../../infra/modules/data/main.tf` (RDS `accentury-{env}`, 사용자와 DB 이름 `accentury`, 관리형 마스터 시크릿)
- `../../infra/modules/ai-host/ai-egress-guard.sh` (컨테이너에만 걸리는 egress 가드)
- `../../infra/envs/{staging,prod}/outputs.tf` (`rds_endpoint`, `rds_master_user_secret_arn`, `ai_asg_name`)
- `../../backend/src/main/resources/application.yml` (`spring.flyway`, `baseline-on-migrate` 주석), `../../backend/src/main/resources/db/migration/`
- `../../.github/workflows/deploy.yml` (`rollback_backend`)

KAN-220 재베이스라인(2026-09-21~22)을 실제로 한 기록과 그때의 SQL은 [docs/wiki/infra-history.md](../wiki/infra-history.md)의
KAN-220 항목에 있다.

## 임시 통로가 필요한 이유

RDS SG는 backend-sg에서 오는 5432만 받고(`modules/network/main.tf`의 `rds_from_backend`), ECS Exec은 켜지 않으며(KAN-165), bastion도
없다. 그래서 Flyway 이력 테이블을 손보는 일 같은 것이 생기면 아래 임시 통로를 열었다가 닫는다. 통로는 AI 호스트를 경유하는 SSM
포트 포워딩이다. 호스트는 SSM 관리 대상이고 VPC 안에 있어 RDS에 닿을 수 있고, 호스트 iptables의 egress 가드는 컨테이너에만 걸려
있어(`ai-egress-guard.sh`) 호스트 자신의 5432 연결은 막히지 않는다. ai-sg의 egress는 전부 열려 있다.

staging이 꺼져 있으면 AI 호스트가 0대라 통로를 열 수 없다. 먼저 켠다 ([teardown.md](teardown.md)의 "staging 끄기와 켜기").

## 임시 통로 열기 (환경당)

1. Mac에 session-manager-plugin을 둔다. `aws ssm start-session`이 이 플러그인을 찾는다. `brew install --cask session-manager-plugin`은
   pkg 설치에 sudo를 요구하므로, sudo 없이 두려면 AWS 번들을 홈에 푼다. Intel Mac은 `mac_arm64` 대신 `mac`이다.

   ```
   curl -sSL -o /tmp/smp.zip https://s3.amazonaws.com/session-manager-downloads/plugin/latest/mac_arm64/sessionmanager-bundle.zip \
     && unzip -q -o /tmp/smp.zip -d ~/.local/lib \
     && ln -sf ~/.local/lib/sessionmanager-bundle/bin/session-manager-plugin ~/.local/bin/
   ```

2. 값을 모은다. `rds_endpoint` 출력은 `host:port` 형태라 뒤의 포트를 뗀다.

   ```
   ENV=staging   # 또는 prod
   cd infra/envs/$ENV
   RDS_HOST=$(terraform output -raw rds_endpoint); RDS_HOST=${RDS_HOST%:*}
   SECRET_ARN=$(terraform output -raw rds_master_user_secret_arn)
   ASG=$(terraform output -raw ai_asg_name)
   cd -
   RDS_SG=$(aws ec2 describe-security-groups --filters Name=group-name,Values=accentury-$ENV-rds-sg \
     --query 'SecurityGroups[0].GroupId' --output text)
   AI_SG=$(aws ec2 describe-security-groups --filters Name=group-name,Values=accentury-$ENV-ai-sg \
     --query 'SecurityGroups[0].GroupId' --output text)
   AI_ID=$(aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$ASG" \
     --query 'AutoScalingGroups[0].Instances[?LifecycleState==`InService`].InstanceId | [0]' --output text)
   ```

3. RDS SG에 ai-sg 출처 5432 인바운드를 임시로 더한다. 규칙에 이름 태그를 붙여 두면 닫을 때 찾기 쉽다. Terraform은 인바운드 규칙을
   개별 리소스(`aws_vpc_security_group_ingress_rule`)로 관리하므로 다음 apply가 이 규칙을 지워 주지 않는다. **반드시 손으로 닫는다.**

   ```
   aws ec2 authorize-security-group-ingress --group-id "$RDS_SG" --protocol tcp --port 5432 \
     --source-group "$AI_SG" \
     --tag-specifications 'ResourceType=security-group-rule,Tags=[{Key=Name,Value=temp-operator-psql}]'
   ```

4. 포트 포워딩을 연다. 이 명령은 터미널 하나를 점유하니 별도 창에서 띄운다.

   ```
   aws ssm start-session --target "$AI_ID" \
     --document-name AWS-StartPortForwardingSessionToRemoteHost \
     --parameters "host=$RDS_HOST,portNumber=5432,localPortNumber=15432"
   ```

5. 비밀번호는 Mac에서 관리형 시크릿을 읽는다. **SSM `send-command` 인자로 보내지 않는다.** 명령 이력에 30일 남는다. 사용자와 DB
   이름은 둘 다 `accentury`다 (`modules/data/main.tf`).

   ```
   PGPASSWORD=$(aws secretsmanager get-secret-value --secret-id "$SECRET_ARN" \
     --query SecretString --output text | python3 -c 'import json,sys; print(json.load(sys.stdin)["password"])')
   docker run --rm -it -e PGPASSWORD="$PGPASSWORD" postgres:16 \
     psql -h host.docker.internal -p 15432 -U accentury accentury
   ```

6. 닫기. 포트 포워딩 창을 끝내고 규칙을 지운 뒤 없어졌는지 본다.

   ```
   aws ec2 revoke-security-group-ingress --group-id "$RDS_SG" --protocol tcp --port 5432 --source-group "$AI_SG"
   aws ec2 describe-security-group-rules --filters Name=group-id,Values="$RDS_SG" \
     --query 'SecurityGroupRules[?!IsEgress].[ReferencedGroupInfo.GroupId,FromPort]' --output text
   ```

## Flyway 지금 상태

- 두 환경의 이력은 KAN-220 재베이스라인으로 `V1 | BASELINE` 한 행에서 다시 시작했고, 그 뒤 `V2`부터 쌓인다 (지금 레포에는
  `V1__baseline.sql`과 `V2`~`V17`이 있다).
- `application.yml`에 `baseline-on-migrate`가 **없다.** 재베이스라인 중인 이미지에만 `baseline-on-migrate: true`와
  `baseline-version: 1`을 켜고, 끝나면 다시 지운다. 남겨 두면 이력 없는 비어 있지 않은 DB를 조용히 V1 적용 상태로 간주하는
  부작용만 남는다 (Flyway 기본값은 실패).
- 적용된 마이그레이션 파일은 고치지 않는다. 체크섬이 어긋나 기동이 막힌다.

## Flyway 재베이스라인 절차 (다음에 할 때)

옛 V1~Vn을 새 `V1__baseline.sql` 하나로 합치는 작업이다. 새 파일은 기존 DB에서 실행되지 않는다. 이력 테이블의 이름을 바꾸면
`baseline-on-migrate: true` + `baseline-version: 1`이 버전 1의 baseline 행만 기록한다. 그래서 새 V1에 담지 않는 구 정의 행 삭제도
같은 SQL 세션에서 사람이 한다. 이력 표는 지우지 않고 이름을 바꿔 두므로, 실패해도 이름을 되돌리면 구 이미지가 다시 기동한다.

아래 SQL은 KAN-220 때의 형태를 일반화한 것이다. `<KEEP>`은 새 V1에 남기는 정의 버전, `<TAG>`는 이번 작업 티켓 이름(예: `kan999`)
이다. KAN-220 이후 V2~V17에서 테이블이 늘었으므로(계정, 학습, 번역, 기록 등) **다음 재베이스라인 전에 지울 행과 이름을 바꿀
제약과 인덱스 목록을 지금 스키마로 다시 확인한다.** 순서가 중요하다.

1. `PUT /admin/v0/active-version`으로 `<KEEP>`을 활성으로 바꾼다 (이미 활성이면 건너뛴다).
2. 구 버전을 참조하는 **미만료 세션이 0건**이 될 때까지 기다린다. 미완주 세션은 30분이면 만료 정리되지만, 완주 세션은 결과 조회를
   위해 완료 시점부터 24시간 산다(`analysis.retention: 24h`, 명세서 §5.5). 결과 조회(`ResultService`)와 상태 조회
   (`AnalysisStatusService`)가 세션의 버전으로 정의를 찾으므로, 그 세션이 살아 있는 동안 정의를 지우면 그 사용자의 결과 화면이
   404가 된다. 전환 직후에 완주가 있었으면 최대 24시간이다. 아래 조회가 빈 결과여야 한다.

   ```
   select test_version, count(*) from test_session
   where test_version <> '<KEEP>' and expires_at > now() group by 1;
   ```

3. (prod만) 수동 스냅샷을 하나 남긴다. 이름 되돌리기가 1순위 복구 경로라 스냅샷은 그것마저 못 쓸 때의 마지막 수단이고, staging은
   데이터가 버려도 되는 것이라 생략한다. 스냅샷이 `available`이 될 때까지 기다린 뒤 4단계로 간다 (수 분). RDS 식별자는
   `accentury-prod`다.

   ```
   SNAP="<TAG>-before-rebaseline-$(date +%Y%m%d%H%M)"
   aws rds create-db-snapshot --db-instance-identifier accentury-prod --db-snapshot-identifier "$SNAP"
   aws rds wait db-snapshot-available --db-snapshot-identifier "$SNAP"
   ```

4. 위 통로로 붙어 아래를 한 트랜잭션으로 넣는다. `select` 세 줄의 결과는 티켓 코멘트에 남긴다. 첫 `do` 블록은 2단계의 조건을 다시
   확인하는 안전장치다. 남은 세션이 있으면 예외로 트랜잭션이 중단되고 아무것도 지워지지 않는다. 이력 테이블은 **지우지 않고 이름을
   바꾼다.** Flyway는 `flyway_schema_history`라는 이름만 찾으므로 이름이 바뀐 표는 없는 것과 같고, 실패하면 이름을 되돌리는 것이
   곧 복구다. 지우는 정의 행도 같은 이유로 백업 표에 복사해 둔다. **표 이름을 바꿔도 제약과 인덱스 이름은 그대로 남으므로 그것도
   바꾼다.** 두지 않으면 새 이미지의 Flyway가 새 표를 만들 때 `relation flyway_schema_history_pk already exists`로 기동이 막힌다.

   ```
   begin;
   do $$ begin
     if exists (select 1 from test_session where test_version <> '<KEEP>' and expires_at > now()) then
       raise exception '구 버전을 참조하는 미만료 세션이 남아 있다 - 2단계로 돌아간다';
     end if;
   end $$;
   select test_version from test_definition order by published_at;
   select version, type, checksum from flyway_schema_history order by installed_rank;
   select test_version, previous_test_version from active_test_version;   -- <KEEP> 여야 한다
   update active_test_version set previous_test_version = null where id = 'CURRENT';
   create table test_definition_<TAG>_backup as
     select * from test_definition where test_version <> '<KEEP>';
   delete from test_definition where test_version <> '<KEEP>';
   alter table flyway_schema_history rename to flyway_schema_history_<TAG>_backup;
   alter table flyway_schema_history_<TAG>_backup rename constraint flyway_schema_history_pk to flyway_schema_history_<TAG>_backup_pk;
   alter index flyway_schema_history_s_idx rename to flyway_schema_history_<TAG>_backup_s_idx;
   commit;
   ```

5. 곧바로 `baseline-on-migrate`를 켠 새 이미지를 배포한다 (staging은 Dev 병합의 Image Deploy, prod는 Release 승격과 environment
   승인). 기동 로그에서 `Successfully baselined schema with version: 1`을 확인하고,
   `select version, type, success from flyway_schema_history`가 `1 | BASELINE | t` 한 행이면 성공이다.
6. 스모크(`scripts/e2e_smoke.py`)와 `GET /admin/v0/test-definitions`(`<KEEP>` 1건, 활성)로 확인한 뒤 백업 표 둘을 지우고 통로를
   닫는다. 백업 표를 남겨 두면 다음 재베이스라인이나 스키마 대조에서 정체 모를 표로 보인다.

   ```
   drop table flyway_schema_history_<TAG>_backup, test_definition_<TAG>_backup;
   ```

7. 두 환경 전환이 끝나면 `baseline-on-migrate`와 `baseline-version`을 다시 지운다.

## 새 이미지가 기동하지 못하면 (복구 경로)

1. 새 태스크 정의로 뜬 태스크가 healthy가 못 되는 동안 구 태스크는 그대로 돈다. 롤링 배포가 구 태스크를 먼저 내리지 않는다. 서비스는
   살아 있으니 서두르지 않는다. 다만 이 상태에서 `deploy.yml`의 `rollback_backend`(직전 태스크 정의로 update-service)가 돌거나 ECS가
   구 태스크를 교체하면, 구 이미지에는 `baseline-on-migrate`가 없어 "이력 없는 비어 있지 않은 스키마"에서 기동이 막힌다. 그래서
   **먼저 DB를 되돌린다.**
2. 같은 통로로 아래를 넣는다. 이름을 되돌리면 옛 이력이 그대로라 구 이미지가 다시 기동하고, 정의 행을 되돌리면 구 버전 세션 조회도
   돌아온다. 새 이미지의 baseline이 이미 기록됐다면(기동은 됐는데 스모크가 실패한 경우) 그 새 표부터 지운다.

   ```
   begin;
   drop table if exists flyway_schema_history;                       -- 새 이미지가 만든 baseline 표가 있을 때만
   alter table flyway_schema_history_<TAG>_backup rename to flyway_schema_history;
   alter table flyway_schema_history rename constraint flyway_schema_history_<TAG>_backup_pk to flyway_schema_history_pk;
   alter index flyway_schema_history_<TAG>_backup_s_idx rename to flyway_schema_history_s_idx;
   insert into test_definition select * from test_definition_<TAG>_backup;
   commit;
   ```

3. Image Deploy를 직전 이미지 태그로 다시 실행하거나 `rollback_backend`가 마저 돌게 둔다. 그 뒤 원인을 고쳐 4단계부터 다시 한다
   (백업 표는 그때 다시 만들어진다. 남아 있으면 `create table`이 실패하니 먼저 지운다).
4. 스냅샷 복원은 위가 전부 실패했을 때만 쓴다. 복원은 새 인스턴스를 만들어 엔드포인트가 바뀌므로 `SPRING_DATASOURCE_URL` SSM
   파라미터와 Terraform state까지 손봐야 하는 큰 작업이다.

## 함정

- 4단계의 이름 바꾸기부터 새 태스크가 healthy가 될 때까지(약 2분) **구 이미지는 재기동이 안 된다.** 이력 없는 비어 있지 않은 스키마를
  구 설정(`baseline-on-migrate` 없음)이 거부하기 때문이다. 서킷 브레이커 롤백(`deploy.yml`의 `rollback_backend`)도 구 이미지를 다시
  띄우려 하므로 같은 자리에서 막힌다. 그때는 위 복구 경로로 이름을 되돌린 뒤 롤백이 마저 돌게 둔다. **SQL은 배포 트리거 직전에만
  넣고 배포 중에는 태스크를 건드리지 않는다.** 지금 도는 태스크는 Flyway를 다시 돌리지 않으므로 영향이 없다.
- 구 정의를 지우면 `previous_test_version`이 null이라 활성 버전 롤백 호출이 409를 낸다. 되돌릴 정의도 없다. 최초 발행 때와 같은
  상태다.
- `baseline-on-migrate`는 이력 테이블이 없고 스키마가 비어 있지 않은 DB를 조용히 V1 적용 상태로 간주한다. 전환 중인 이미지에만 켜
  두고, 두 환경 전환이 끝나면 지운다.
- **V8 이후 구 이미지 롤백 금지(KAN-200, 재베이스라인 전 번호 기준)는 그대로다.** 재베이스라인 뒤에는 옛 마이그레이션을 아는 이미지도 이력이 없어 기동하지 못한다.
