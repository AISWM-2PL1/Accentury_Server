"""prod 응시 현황 2시간 리포트 (KAN-273).

음성 버킷의 객체 키와 LastModified만 보고 세션을 센다 - 객체 본문은 읽지 않는다 (역할에 GetObject가 없다).
결과는 AWS Chatbot 사용자 지정 알림 형식으로 SNS에 넣고, Chatbot이 슬랙 #daily-user로 옮긴다.

키 규약 (backend TrainingSample.keyPrefix, VocabAnswerSample, KAN-269/274/276):

    <env>/<region>/<version>/<sessionId>/<itemId>/<jobId>.wav|.json             동의 세션의 음성 문항
    <env>/_no-audio/<region>/<version>/<sessionId>/<itemId>/<id>.json           미동의 세션의 음성 라벨, 모든 세션의 단어 정오

그 밖의 밑줄 조각(_learning 등, 학습 기능의 음성)은 레벨테스트 세션이 아니라 세지 않는다.

판정:
    동의       세션에 WAV가 하나라도 있다
    미동의     WAV가 없다 (_no-audio 아래에만 있다)
    완주       서로 다른 itemId 수가 그 버전의 기록 문항 수 이상
    진행 중    완주 전이고 마지막 객체가 30분(세션 TTL) 안
    이탈       완주 전이고 마지막 객체가 30분보다 오래됐다
    구간 소속  세션의 첫 객체 시각
"""

from __future__ import annotations

import json
import os
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

KST = timezone(timedelta(hours=9))
NO_AUDIO = "_no-audio"
SESSION_TTL = timedelta(minutes=30)

# 버전마다 S3에 남는 문항 수. 7문항(음성 3 + 단어 4)이어도 단어 정오 기록(KAN-276)은 gn-2026.10.2부터라
# gn-2026.10.1 세션은 음성 3개만 남는다. gn-2026.09.4는 5문항 시절이다. 표에 없는 버전은 DEFAULT_ITEMS다.
DEFAULT_ITEMS_PER_VERSION = {"gn-2026.09.4": 5, "gn-2026.10.1": 3}
DEFAULT_ITEMS = 7


@dataclass
class Session:
    region: str
    version: str
    first: datetime
    last: datetime
    items: set[str] = field(default_factory=set)
    consented: bool = False
    wav: int = 0
    json: int = 0


def parse_key(key: str, prefix: str):
    """(region, version, sessionId, itemId, no_audio) 또는 세지 않는 키면 None."""
    if not key.startswith(prefix + "/"):
        return None
    parts = key[len(prefix) + 1:].split("/")
    no_audio = parts[0] == NO_AUDIO
    if no_audio:
        parts = parts[1:]
    elif parts[0].startswith("_"):
        return None
    if len(parts) != 5 or not parts[2].startswith("s_"):
        return None
    region, version, session_id, item_id, _ = parts
    return region, version, session_id, item_id, no_audio


def collect(objects, prefix: str) -> dict[str, Session]:
    """objects: (key, LastModified) 순회 가능 객체."""
    sessions: dict[str, Session] = {}
    for key, modified in objects:
        parsed = parse_key(key, prefix)
        if parsed is None:
            continue
        region, version, session_id, item_id, no_audio = parsed
        s = sessions.get(session_id)
        if s is None:
            s = sessions[session_id] = Session(region, version, modified, modified)
        s.first = min(s.first, modified)
        s.last = max(s.last, modified)
        s.items.add(item_id)
        if key.endswith(".wav"):
            s.wav += 1
            if not no_audio:
                s.consented = True
        elif key.endswith(".json"):
            s.json += 1
    return sessions


@dataclass
class Stats:
    sessions: int = 0
    consented: int = 0
    not_consented: int = 0
    completed: int = 0
    abandoned: int = 0
    in_progress: int = 0
    wav: int = 0
    json: int = 0
    regions: Counter = field(default_factory=Counter)

    @property
    def completion_rate(self) -> float | None:
        decided = self.completed + self.abandoned
        return None if decided == 0 else self.completed / decided


def summarize(sessions, now: datetime, items_per_version: dict[str, int], default_items: int) -> Stats:
    st = Stats()
    for s in sessions:
        st.sessions += 1
        if s.consented:
            st.consented += 1
        else:
            st.not_consented += 1
        if len(s.items) >= items_per_version.get(s.version, default_items):
            st.completed += 1
        elif now - s.last < SESSION_TTL:
            st.in_progress += 1
        else:
            st.abandoned += 1
        st.wav += s.wav
        st.json += s.json
        st.regions[s.region] += 1
    return st


def windows(now: datetime):
    """발송 시각(정각으로 반올림)에서 (구간 시작, 구간 끝, 오늘 시작)을 정한다.

    08시 리포트는 밤사이(00~08시)를 담고, 그 밖은 직전 2시간이다. 00시(24시) 리포트의 '오늘'은 막 끝난 그날이다.
    Scheduler가 정각보다 조금 늦거나 이르게 불러도 같은 구간이 되도록 가장 가까운 정각으로 맞춘다.
    """
    local = now.astimezone(KST)
    end = (local + timedelta(minutes=30)).replace(minute=0, second=0, microsecond=0)
    start = end.replace(hour=0) if end.hour == 8 else end - timedelta(hours=2)
    day_start = (end - timedelta(days=1) if end.hour == 0 else end).replace(hour=0)
    return start, end, day_start


def _rate(st: Stats) -> str:
    r = st.completion_rate
    return "-" if r is None else f"{r * 100:.0f}%"


def _line(label: str, st: Stats) -> str:
    # 표로 맞추지 않는다 - 슬랙 고정폭 글꼴에서 한글은 두 칸이라 f-string 정렬이 어긋난다.
    return (f"*{label}* 세션 {st.sessions} (동의 {st.consented}, 미동의 {st.not_consented})"
            f" / 완주 {st.completed}, 이탈 {st.abandoned}, 진행 중 {st.in_progress}"
            f" / 완주율 {_rate(st)} / WAV {st.wav}, JSON {st.json}")


def _clock(day: datetime, t: datetime) -> str:
    """day 기준의 시각 표기 - 그날이 끝나는 자정은 다음 날 00:00이 아니라 24:00으로 쓴다."""
    return "24:00" if t.date() > day.date() else t.strftime("%H:%M")


def build_message(env: str, recent: Stats, today: Stats, total: Stats,
                  start: datetime, end: datetime, day_start: datetime) -> dict:
    span = f"{start.strftime('%H:%M')}~{_clock(start, end)}"
    regions = ", ".join(f"{r} {n}" for r, n in sorted(recent.regions.items(), key=lambda x: (-x[1], x[0]))) or "없음"
    description = "\n".join([
        _line(f"{'밤사이' if end.hour == 8 else '최근 2시간'} ({span})", recent),
        f"    지역: {regions}",
        _line(f"오늘 누적 ({day_start.strftime('%m-%d')} 00:00~{_clock(day_start, end)})", today),
        _line("전체 누적", total),
        "",
        "동의는 WAV가 있는 세션, 미동의는 JSON만 있는 세션입니다. 완주율 = 완주 / (완주 + 이탈)이고 진행 중은 뺍니다.",
    ])
    return {
        "version": "1.0",
        "source": "custom",
        "content": {
            "textType": "client-markdown",
            "title": (f"[{env}] 응시 현황 {day_start.strftime('%m-%d')} {_clock(day_start, end)}"
                      f" - 최근 {recent.sessions}건, 오늘 {today.sessions}건"),
            "description": description,
        },
    }


def report(objects, env: str, prefix: str, now: datetime,
           items_per_version: dict[str, int] | None = None, default_items: int = DEFAULT_ITEMS) -> dict:
    items_per_version = DEFAULT_ITEMS_PER_VERSION if items_per_version is None else items_per_version
    sessions = list(collect(objects, prefix).values())
    start, end, day_start = windows(now)

    def within(lo, hi):
        return [s for s in sessions if lo <= s.first < hi]

    now_local = now.astimezone(KST)
    recent = summarize(within(start, end), now_local, items_per_version, default_items)
    today = summarize(within(day_start, end), now_local, items_per_version, default_items)
    total = summarize([s for s in sessions if s.first < end], now_local, items_per_version, default_items)
    return build_message(env, recent, today, total, start, end, day_start)


def _list_objects(s3, bucket: str, prefix: str):
    paginator = s3.get_paginator("list_objects_v2")
    for page in paginator.paginate(Bucket=bucket, Prefix=prefix + "/"):
        for obj in page.get("Contents", []):
            yield obj["Key"], obj["LastModified"].astimezone(KST)


def handler(event, context):
    import boto3  # Lambda 런타임 기본 포함분. 단위 테스트는 이 함수를 부르지 않는다.

    env = os.environ["REPORT_ENV"]
    bucket = os.environ["VOICE_BUCKET"]
    topic = os.environ["TOPIC_ARN"]
    items = json.loads(os.environ.get("ITEMS_PER_VERSION") or "null")
    default_items = int(os.environ.get("DEFAULT_ITEMS") or DEFAULT_ITEMS)

    now = datetime.now(KST)
    message = report(_list_objects(boto3.client("s3"), bucket, env), env, env, now, items, default_items)
    boto3.client("sns").publish(TopicArn=topic, Message=json.dumps(message, ensure_ascii=False))
    print(json.dumps({"published": message["content"]["title"]}, ensure_ascii=False))
    return {"title": message["content"]["title"]}
