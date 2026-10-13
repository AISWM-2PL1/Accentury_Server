"""scale_notify 단위 테스트 (KAN-273). 실행: python3 -m unittest discover -s infra/modules/slack-alerts/scale_notify"""

import unittest

from scale_notify import build_message, capacity_from_cause, desired_capacity, wants_capacity

LAUNCH_CAUSE = ("At 2026-10-13T08:32:31Z a monitor alarm accentury-prod-ai-scale-out in state ALARM triggered policy "
                "accentury-prod-ai-scale-out changing the desired capacity from 2 to 3.  At 2026-10-13T08:32:41Z an instance "
                "was started in response to a difference between desired and actual capacity, increasing the capacity from 2 to 3.")
TERMINATE_CAUSE = ("At 2026-10-13T07:53:33Z a monitor alarm accentury-prod-ai-scale-in in state ALARM triggered policy "
                   "accentury-prod-ai-scale-in changing the desired capacity from 2 to 1.  At 2026-10-13T07:53:37Z an instance "
                   "was taken out of service in response to a difference between desired and actual capacity, shrinking the "
                   "capacity from 2 to 1.")


def event(detail_type, cause=LAUNCH_CAUSE, **detail):
    return {
        "detail-type": detail_type,
        "time": "2026-10-13T08:33:02Z",
        "detail": {"EC2InstanceId": "i-0abc", "AutoScalingGroupName": "accentury-prod-ai", "Cause": cause, **detail},
    }


class CapacityFromCauseTest(unittest.TestCase):
    def test_reads_last_capacity_change(self):
        self.assertEqual(capacity_from_cause(LAUNCH_CAUSE), 3)
        self.assertEqual(capacity_from_cause(TERMINATE_CAUSE), 1)

    def test_missing_or_unknown_text_is_none(self):
        self.assertIsNone(capacity_from_cause(None))
        self.assertIsNone(capacity_from_cause("instance refresh"))


class BuildMessageTest(unittest.TestCase):
    def test_launch_title_has_capacity_and_env(self):
        m = build_message(event("EC2 Instance Launch Successful"), "prod", 3)
        self.assertEqual(m["content"]["title"], "[prod] AI 호스트 1대 늘었습니다 (현재: 3대)")
        self.assertEqual(m["version"], "1.0")
        self.assertEqual(m["source"], "custom")
        self.assertEqual(m["content"]["textType"], "client-markdown")

    def test_terminate_title_has_capacity(self):
        m = build_message(event("EC2 Instance Terminate Successful", TERMINATE_CAUSE), "staging", 1)
        self.assertEqual(m["content"]["title"], "[staging] AI 호스트 1대 줄었습니다 (현재: 1대)")

    def test_launch_failure_has_no_capacity_even_when_known(self):
        m = build_message(event("EC2 Instance Launch Unsuccessful"), "prod", 3)
        self.assertEqual(m["content"]["title"], "[prod] :warning: AI 호스트 시작 실패")

    def test_unknown_capacity_sends_plain_title(self):
        m = build_message(event("EC2 Instance Launch Successful"), "prod", None)
        self.assertEqual(m["content"]["title"], "[prod] AI 호스트 1대 늘었습니다")

    def test_description_lines(self):
        m = build_message(event("EC2 Instance Launch Successful"), "prod", 3)
        lines = m["content"]["description"].split("\n")
        self.assertEqual(lines[0], "*인스턴스* i-0abc")
        self.assertEqual(lines[1], "*시각(UTC)* 2026-10-13T08:33:02Z")
        self.assertEqual(lines[2], f"*원인* {LAUNCH_CAUSE}")

    def test_failure_without_cause_uses_status_message(self):
        m = build_message(event("EC2 Instance Launch Unsuccessful", cause=None, StatusMessage="InsufficientInstanceCapacity"),
                          "prod", None)
        self.assertEqual(m["content"]["description"].split("\n")[2], "*원인* InsufficientInstanceCapacity")


class WantsCapacityTest(unittest.TestCase):
    def test_only_launch_and_terminate_look_up_capacity(self):
        self.assertTrue(wants_capacity(event("EC2 Instance Launch Successful")))
        self.assertTrue(wants_capacity(event("EC2 Instance Terminate Successful")))
        self.assertFalse(wants_capacity(event("EC2 Instance Launch Unsuccessful")))
        self.assertFalse(wants_capacity({}))


class FakeAutoscaling:
    def __init__(self, groups=None, error=None):
        self.groups, self.error, self.calls = groups, error, []

    def describe_auto_scaling_groups(self, **kwargs):
        self.calls.append(kwargs)
        if self.error:
            raise self.error
        return {"AutoScalingGroups": self.groups}


class DesiredCapacityTest(unittest.TestCase):
    def test_reads_desired_capacity_of_named_group(self):
        client = FakeAutoscaling(groups=[{"DesiredCapacity": 3}])
        self.assertEqual(desired_capacity(client, "accentury-prod-ai"), 3)
        self.assertEqual(client.calls, [{"AutoScalingGroupNames": ["accentury-prod-ai"]}])

    def test_missing_group_or_api_error_is_none(self):
        self.assertIsNone(desired_capacity(FakeAutoscaling(groups=[]), "x"))
        self.assertIsNone(desired_capacity(FakeAutoscaling(error=RuntimeError("throttled")), "x"))


if __name__ == "__main__":
    unittest.main()
