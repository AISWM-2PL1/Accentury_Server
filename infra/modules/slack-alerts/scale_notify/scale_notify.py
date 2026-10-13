"""AI 호스트 ASG 스케일 이벤트 -> #infra-alerts 알림 (KAN-273).

EventBridge 규칙이 Auto Scaling 이벤트(시작 성공, 종료 성공, 시작 실패)를 이 함수로 보낸다. 함수는 ASG의 현재 희망 용량을
조회해 제목에 "(현재: n대)"를 붙이고 AWS Chatbot 사용자 지정 알림 형식으로 SNS에 넣는다. Chatbot이 슬랙으로 옮긴다.

왜 Lambda인가 - 이벤트에는 변경 뒤 대수가 필드로 없다. Cause 문장 끝("... from 2 to 3")에만 있고 EventBridge 입력 변환기는
문장에서 숫자를 뽑지 못한다. 슬랙 배너에는 제목만 보이므로 대수는 제목에 있어야 한다 (2026-10-13 결정).

대수의 뜻은 ASG의 희망 용량(DesiredCapacity)이다. 시작 성공 시점에는 새 인스턴스가 아직 워밍업 중일 수 있어 InService 수와
다를 수 있지만, 사용자에게 "지금 몇 대 체제인가"를 답하는 값은 희망 용량이다 (티켓 본문 "변경 뒤 희망 용량").
조회가 실패하면 Cause 문장의 "to n"으로 대신하고, 그것도 없으면 대수 없이 보낸다 - 알림이 빠지는 것보다 낫다.
"""

from __future__ import annotations

import json
import os
import re

# detail-type -> (제목, 제목에 대수를 붙일지). 시작 실패에는 붙이지 않는다 - 희망 용량은 늘었지만 실제로 늘지 않았으므로
# "현재 n대"가 거짓이 된다. 원인 줄에 Cause가 그대로 나간다.
TITLES = {
    "EC2 Instance Launch Successful": ("AI 호스트 1대 늘었습니다", True),
    "EC2 Instance Terminate Successful": ("AI 호스트 1대 줄었습니다", True),
    "EC2 Instance Launch Unsuccessful": (":warning: AI 호스트 시작 실패", False),
}

# Cause 예: "... increasing the capacity from 2 to 3." / "... shrinking the capacity from 2 to 1."
_CAUSE_CAPACITY = re.compile(r"capacity from \d+ to (\d+)")


def capacity_from_cause(cause: str | None) -> int | None:
    found = _CAUSE_CAPACITY.findall(cause or "")
    return int(found[-1]) if found else None


def build_message(event: dict, env: str, capacity: int | None) -> dict:
    detail = event.get("detail") or {}
    detail_type = event.get("detail-type", "")
    title, with_capacity = TITLES.get(detail_type, (detail_type or "AI 호스트 스케일 이벤트", False))
    if with_capacity and capacity is not None:
        title = f"{title} (현재: {capacity}대)"
    description = "\n".join([
        f"*인스턴스* {detail.get('EC2InstanceId', '-')}",
        f"*시각(UTC)* {event.get('time', '-')}",
        f"*원인* {detail.get('Cause') or detail.get('StatusMessage') or '-'}",
    ])
    return {
        "version": "1.0",
        "source": "custom",
        "content": {
            "textType": "client-markdown",
            "title": f"[{env}] {title}",
            "description": description,
        },
    }


def wants_capacity(event: dict) -> bool:
    return TITLES.get(event.get("detail-type", ""), ("", False))[1]


def desired_capacity(autoscaling, asg_name: str) -> int | None:
    """ASG 희망 용량. 조회 실패는 알림을 막지 않는다 - None을 돌려 호출자가 Cause로 대신한다."""
    try:
        groups = autoscaling.describe_auto_scaling_groups(AutoScalingGroupNames=[asg_name])["AutoScalingGroups"]
        return int(groups[0]["DesiredCapacity"]) if groups else None
    except Exception as e:  # noqa: BLE001 - boto 예외 종류가 많고 어느 것이든 대응은 같다
        print(json.dumps({"describe_failed": str(e)}, ensure_ascii=False))
        return None


def handler(event, context):
    import boto3  # Lambda 런타임 기본 포함분. 단위 테스트는 이 함수를 부르지 않는다.

    env = os.environ["SLACK_ENV"]
    topic = os.environ["TOPIC_ARN"]
    asg_name = os.environ["ASG_NAME"]

    capacity = None
    if wants_capacity(event):
        # 조회는 선택 사항이다 - 기본 타임아웃(연결 60초 + 읽기 60초, 재시도 포함)은 Lambda 30초를 넘겨 조회가 멈추면
        # 알림 자체가 빠진다 (Codex 리뷰). 짧게 끊고 Cause로 넘어간다.
        from botocore.config import Config

        autoscaling = boto3.client("autoscaling",
                                   config=Config(connect_timeout=3, read_timeout=5, retries={"max_attempts": 2}))
        capacity = desired_capacity(autoscaling, asg_name)
        if capacity is None:
            capacity = capacity_from_cause((event.get("detail") or {}).get("Cause"))
    message = build_message(event, env, capacity)
    boto3.client("sns").publish(TopicArn=topic, Message=json.dumps(message, ensure_ascii=False))
    print(json.dumps({"published": message["content"]["title"]}, ensure_ascii=False))
    return {"title": message["content"]["title"]}
