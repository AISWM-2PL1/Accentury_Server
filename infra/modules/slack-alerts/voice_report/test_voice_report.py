"""voice_report 단위 테스트 (KAN-273). 실행: python3 -m unittest discover -s infra/modules/slack-alerts/voice_report"""

import json
import unittest
from datetime import datetime

from voice_report import KST, collect, parse_key, report, windows


def t(day, hh, mm=0):
    return datetime(2026, 10, day, hh, mm, tzinfo=KST)


def consented(session, items, at, region="SEOUL", version="gn-2026.10.2"):
    """동의 세션 - 음성 문항은 WAV + JSON, 단어 문항은 _no-audio JSON."""
    keys = []
    for i, item in enumerate(items):
        if item.startswith("v"):
            base = f"prod/{region}/{version}/{session}/{item}/a_{i}"
            keys += [(base + ".wav", at), (base + ".json", at)]
        else:
            keys.append((f"prod/_no-audio/{region}/{version}/{session}/{item}/a_{i}.json", at))
    return keys


def not_consented(session, items, at, region="SEOUL", version="gn-2026.10.2"):
    return [(f"prod/_no-audio/{region}/{version}/{session}/{item}/a_{i}.json", at) for i, item in enumerate(items)]


SEVEN = ["v1", "v2", "v3", "w1", "w2", "w3", "w4"]


class ParseKeyTest(unittest.TestCase):
    def test_audio_and_no_audio_trees(self):
        self.assertEqual(parse_key("prod/SEOUL/gn/s_1/v1/a.wav", "prod"), ("SEOUL", "gn", "s_1", "v1", False))
        self.assertEqual(parse_key("prod/_no-audio/SEOUL/gn/s_1/w1/a.json", "prod"), ("SEOUL", "gn", "s_1", "w1", True))

    def test_other_env_learning_and_malformed_keys_are_skipped(self):
        self.assertIsNone(parse_key("staging/SEOUL/gn/s_1/v1/a.wav", "prod"))
        self.assertIsNone(parse_key("prod/_learning/SEOUL/gn/s_1/v1/a.wav", "prod"))
        self.assertIsNone(parse_key("prod/SEOUL/gn/s_1/a.wav", "prod"))
        self.assertIsNone(parse_key("prod/SEOUL/gn/x_1/v1/a.wav", "prod"))


class CollectTest(unittest.TestCase):
    def test_consent_is_decided_by_wav_and_items_are_distinct(self):
        objs = consented("s_a", SEVEN, t(13, 10)) + not_consented("s_b", SEVEN, t(13, 10))
        # 재녹음 - 같은 문항에 작업만 하나 더
        objs += [("prod/SEOUL/gn-2026.10.2/s_a/v1/a_retry.wav", t(13, 10, 5))]
        s = collect(objs, "prod")
        self.assertTrue(s["s_a"].consented)
        self.assertFalse(s["s_b"].consented)
        self.assertEqual(len(s["s_a"].items), 7)
        self.assertEqual(s["s_a"].wav, 4)


class WindowsTest(unittest.TestCase):
    def test_regular_slot_is_previous_two_hours(self):
        self.assertEqual(windows(t(13, 14)), (t(13, 12), t(13, 14), t(13, 0)))

    def test_eight_oclock_covers_the_night(self):
        self.assertEqual(windows(t(13, 8)), (t(13, 0), t(13, 8), t(13, 0)))

    def test_midnight_reports_the_day_that_just_ended(self):
        self.assertEqual(windows(t(14, 0)), (t(13, 22), t(14, 0), t(13, 0)))

    def test_slightly_late_or_early_invocation_snaps_to_the_hour(self):
        self.assertEqual(windows(t(13, 14, 1))[1], t(13, 14))
        self.assertEqual(windows(t(13, 13, 59))[1], t(13, 14))


class ReportTest(unittest.TestCase):
    def setUp(self):
        self.objs = (
            consented("s_done", SEVEN, t(13, 12, 30), region="GYEONGNAM")       # 구간 안 완주, 동의
            + not_consented("s_nc", SEVEN, t(13, 13), region="SEOUL")           # 구간 안 완주, 미동의
            + consented("s_quit", ["v1"], t(13, 12, 10))                         # 구간 안 이탈
            + not_consented("s_live", ["v1", "w1"], t(13, 13, 50))              # 구간 안 진행 중
            + consented("s_morning", SEVEN, t(13, 9))                            # 오늘, 구간 밖
            + consented("s_old", ["v1", "v2", "v3"], t(7, 15), version="gn-2026.10.1")  # 옛 버전은 3개면 완주
            + consented("s_later", SEVEN, t(13, 14, 0, ))                        # 발송 시각 이후 시작 - 어디에도 안 셈
            + [("staging/SEOUL/gn-2026.10.2/s_stg/v1/a.wav", t(13, 13))]
        )
        self.msg = report(self.objs, "prod", "prod", t(13, 14, 0))
        self.text = self.msg["content"]["description"]

    def test_chatbot_custom_notification_shape(self):
        self.assertEqual(self.msg["version"], "1.0")
        self.assertEqual(self.msg["source"], "custom")
        self.assertEqual(self.msg["content"]["textType"], "client-markdown")
        self.assertLessEqual(len(self.text), 8000)
        self.assertIn("최근 4건, 오늘 5건", self.msg["content"]["title"])

    def test_counts_per_range(self):
        lines = self.text.splitlines()
        self.assertIn("세션 4 (동의 2, 미동의 2) / 완주 2, 이탈 1, 진행 중 1 / 완주율 67%", lines[0])
        self.assertIn("지역: SEOUL 3, GYEONGNAM 1", lines[1])
        self.assertIn("세션 5 (동의 3, 미동의 2) / 완주 3, 이탈 1, 진행 중 1 / 완주율 75%", lines[2])
        self.assertIn("세션 6 (동의 4, 미동의 2) / 완주 4, 이탈 1, 진행 중 1 / 완주율 80%", lines[3])

    def test_zero_sessions_still_reports(self):
        msg = report([], "prod", "prod", t(13, 10))
        self.assertIn("세션 0 (동의 0, 미동의 0)", msg["content"]["description"])
        self.assertIn("완주율 -", msg["content"]["description"])
        self.assertIn("최근 0건, 오늘 0건", msg["content"]["title"])

    def test_midnight_label_is_24(self):
        msg = report([], "prod", "prod", t(14, 0))
        self.assertIn("10-13 24:00", msg["content"]["title"])
        self.assertIn("22:00~24:00", msg["content"]["description"])

    def test_no_identifiers_leak(self):
        body = json.dumps(self.msg, ensure_ascii=False)
        for needle in ("s_done", "s_nc", "s_quit", "a_0", "v1", "w1"):
            self.assertNotIn(needle, body)


if __name__ == "__main__":
    unittest.main()
