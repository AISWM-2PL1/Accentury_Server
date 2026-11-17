-- KAN-345: 단어 학습 발행본 wd-gn-2026.11.1 - 구간 5개 x 세트 4개 x 문항 3개 = 60문항.
--
-- 이 파일은 손으로 쓰지 않는다 - tools/content/build_app_curriculum.py가 만든다. 순서의 근거는
-- tools/content/item_difficulty.py의 집계다 (레벨테스트 gn-2026.10.* 실사용 기록, 2026-11-15 기준).
-- level은 앱 학습 탭의 구간 번호(1부터 5)다. 구간 1이 가장 쉽고, 구간 안에서도 seq가 클수록 어렵다.
--
-- 발행 후 불변이다 (§5.4와 같은 규칙). 순서나 구성이 바뀌면 이 행을 UPDATE하지 않고 새 contentVersion으로
-- INSERT한다. 활성 전환 행은 없다 - 서버가 발행 시각이 가장 늦은 발행본을 목록과 상세에 쓴다. 그래서 이
-- 마이그레이션이 적용되면 앞선 발행본의 진도는 목록에서 빠지고(기록은 남는다) 새 발행본의 진도가 0부터다.
--
-- 원천은 검수한 레벨테스트 어휘 72개(curation_gn_2026_10_2.py)다. 정답률이 높은 순으로 세워 같은 간격으로 60개를
-- 뽑았다. 문항은 표준어 → 사투리 한 방향이고 오답은 같은 분류에서 contentVersion을 시드로 뽑는다 (명세서 §3.16).
insert into word_learning_definition (content_version, dialect, body, published_at)
values ('wd-gn-2026.11.1', 'GYEONGNAM', $definition${
  "contentVersion": "wd-gn-2026.11.1",
  "dialect": "GYEONGNAM",
  "sets": [
    {
      "setId": "ws-26111-01",
      "seq": 1,
      "level": 1,
      "category": "종합",
      "title": "코스 1 단어 1",
      "cards": [
        {
          "cardId": "ws-26111-01c01",
          "standard": "얼른",
          "dialect": "퍼뜩"
        },
        {
          "cardId": "ws-26111-01c02",
          "standard": "조그맣다",
          "dialect": "쪼맨하다"
        },
        {
          "cardId": "ws-26111-01c03",
          "standard": "어지간히",
          "dialect": "엥가이"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-01q01",
          "seq": 1,
          "cardId": "ws-26111-01c01",
          "prompt": "'얼른'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-01q01a",
              "text": "엥가이"
            },
            {
              "choiceId": "ws-26111-01q01b",
              "text": "마이"
            },
            {
              "choiceId": "ws-26111-01q01c",
              "text": "퍼뜩"
            },
            {
              "choiceId": "ws-26111-01q01d",
              "text": "내나"
            }
          ],
          "correctChoiceId": "ws-26111-01q01c",
          "explanation": "'퍼뜩'은 경남에서 '얼른'을 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-01q02",
          "seq": 2,
          "cardId": "ws-26111-01c02",
          "prompt": "'조그맣다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-01q02a",
              "text": "쪼맨하다"
            },
            {
              "choiceId": "ws-26111-01q02b",
              "text": "애럽다"
            },
            {
              "choiceId": "ws-26111-01q02c",
              "text": "따시다"
            },
            {
              "choiceId": "ws-26111-01q02d",
              "text": "잠오다"
            }
          ],
          "correctChoiceId": "ws-26111-01q02a",
          "explanation": "'쪼맨하다'는 경남에서 '조그맣다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-01q03",
          "seq": 3,
          "cardId": "ws-26111-01c03",
          "prompt": "'어지간히'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-01q03a",
              "text": "억수로"
            },
            {
              "choiceId": "ws-26111-01q03b",
              "text": "엥가이"
            },
            {
              "choiceId": "ws-26111-01q03c",
              "text": "천지빼까리더라"
            },
            {
              "choiceId": "ws-26111-01q03d",
              "text": "쪼매"
            }
          ],
          "correctChoiceId": "ws-26111-01q03b",
          "explanation": "'엥가이'는 경남에서 '어지간히'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-02",
      "seq": 2,
      "level": 1,
      "category": "종합",
      "title": "코스 1 단어 2",
      "cards": [
        {
          "cardId": "ws-26111-02c01",
          "standard": "졸리다",
          "dialect": "잠오다"
        },
        {
          "cardId": "ws-26111-02c02",
          "standard": "왜",
          "dialect": "와"
        },
        {
          "cardId": "ws-26111-02c03",
          "standard": "조금",
          "dialect": "쪼매"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-02q01",
          "seq": 1,
          "cardId": "ws-26111-02c01",
          "prompt": "'졸리다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-02q01a",
              "text": "잠오다"
            },
            {
              "choiceId": "ws-26111-02q01b",
              "text": "파이다"
            },
            {
              "choiceId": "ws-26111-02q01c",
              "text": "애럽다"
            },
            {
              "choiceId": "ws-26111-02q01d",
              "text": "대끼리다"
            }
          ],
          "correctChoiceId": "ws-26111-02q01a",
          "explanation": "'잠오다'는 경남에서 '졸리다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-02q02",
          "seq": 2,
          "cardId": "ws-26111-02c02",
          "prompt": "'왜'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-02q02a",
              "text": "오야"
            },
            {
              "choiceId": "ws-26111-02q02b",
              "text": "와"
            },
            {
              "choiceId": "ws-26111-02q02c",
              "text": "아인교"
            },
            {
              "choiceId": "ws-26111-02q02d",
              "text": "우예"
            }
          ],
          "correctChoiceId": "ws-26111-02q02b",
          "explanation": "'와'는 경남에서 '왜'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-02q03",
          "seq": 3,
          "cardId": "ws-26111-02c03",
          "prompt": "'조금'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-02q03a",
              "text": "마이"
            },
            {
              "choiceId": "ws-26111-02q03b",
              "text": "쪼매"
            },
            {
              "choiceId": "ws-26111-02q03c",
              "text": "단디"
            },
            {
              "choiceId": "ws-26111-02q03d",
              "text": "엥가이"
            }
          ],
          "correctChoiceId": "ws-26111-02q03b",
          "explanation": "'쪼매'는 경남에서 '조금'을 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-03",
      "seq": 3,
      "level": 1,
      "category": "종합",
      "title": "코스 1 단어 3",
      "cards": [
        {
          "cardId": "ws-26111-03c01",
          "standard": "여자아이",
          "dialect": "가시나"
        },
        {
          "cardId": "ws-26111-03c02",
          "standard": "많이",
          "dialect": "마이"
        },
        {
          "cardId": "ws-26111-03c03",
          "standard": "야",
          "dialect": "마"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-03q01",
          "seq": 1,
          "cardId": "ws-26111-03c01",
          "prompt": "'여자아이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-03q01a",
              "text": "너거"
            },
            {
              "choiceId": "ws-26111-03q01b",
              "text": "가시나"
            },
            {
              "choiceId": "ws-26111-03q01c",
              "text": "머스마"
            },
            {
              "choiceId": "ws-26111-03q01d",
              "text": "얼라"
            }
          ],
          "correctChoiceId": "ws-26111-03q01b",
          "explanation": "'가시나'는 경남에서 '여자아이'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-03q02",
          "seq": 2,
          "cardId": "ws-26111-03c02",
          "prompt": "'많이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-03q02a",
              "text": "단디"
            },
            {
              "choiceId": "ws-26111-03q02b",
              "text": "마이"
            },
            {
              "choiceId": "ws-26111-03q02c",
              "text": "만날천날"
            },
            {
              "choiceId": "ws-26111-03q02d",
              "text": "똑띠"
            }
          ],
          "correctChoiceId": "ws-26111-03q02b",
          "explanation": "'마이'는 경남에서 '많이'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-03q03",
          "seq": 3,
          "cardId": "ws-26111-03c03",
          "prompt": "'야'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-03q03a",
              "text": "우예"
            },
            {
              "choiceId": "ws-26111-03q03b",
              "text": "하모"
            },
            {
              "choiceId": "ws-26111-03q03c",
              "text": "마"
            },
            {
              "choiceId": "ws-26111-03q03d",
              "text": "고마"
            }
          ],
          "correctChoiceId": "ws-26111-03q03c",
          "explanation": "'마'는 경남에서 '야'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-04",
      "seq": 4,
      "level": 1,
      "category": "종합",
      "title": "코스 1 단어 4",
      "cards": [
        {
          "cardId": "ws-26111-04c01",
          "standard": "꿰매다",
          "dialect": "꼬매다"
        },
        {
          "cardId": "ws-26111-04c02",
          "standard": "드러누워 자라",
          "dialect": "디비자라"
        },
        {
          "cardId": "ws-26111-04c03",
          "standard": "부추",
          "dialect": "정구지"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-04q01",
          "seq": 1,
          "cardId": "ws-26111-04c01",
          "prompt": "'꿰매다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-04q01a",
              "text": "옇어라"
            },
            {
              "choiceId": "ws-26111-04q01b",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws-26111-04q01c",
              "text": "데빌고 와라"
            },
            {
              "choiceId": "ws-26111-04q01d",
              "text": "쌔리다"
            }
          ],
          "correctChoiceId": "ws-26111-04q01b",
          "explanation": "'꼬매다'는 경남에서 '꿰매다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-04q02",
          "seq": 2,
          "cardId": "ws-26111-04c02",
          "prompt": "'드러누워 자라'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-04q02a",
              "text": "디비자라"
            },
            {
              "choiceId": "ws-26111-04q02b",
              "text": "옇어라"
            },
            {
              "choiceId": "ws-26111-04q02c",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws-26111-04q02d",
              "text": "일나다"
            }
          ],
          "correctChoiceId": "ws-26111-04q02a",
          "explanation": "'디비자라'는 경남에서 '드러누워 자라'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-04q03",
          "seq": 3,
          "cardId": "ws-26111-04c03",
          "prompt": "'부추'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-04q03a",
              "text": "정구지"
            },
            {
              "choiceId": "ws-26111-04q03b",
              "text": "무시"
            },
            {
              "choiceId": "ws-26111-04q03c",
              "text": "나락"
            },
            {
              "choiceId": "ws-26111-04q03d",
              "text": "짐치"
            }
          ],
          "correctChoiceId": "ws-26111-04q03a",
          "explanation": "'정구지'는 경남에서 '부추'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-05",
      "seq": 5,
      "level": 2,
      "category": "종합",
      "title": "코스 2 단어 1",
      "cards": [
        {
          "cardId": "ws-26111-05c01",
          "standard": "문지르다",
          "dialect": "문때다"
        },
        {
          "cardId": "ws-26111-05c02",
          "standard": "아닙니까",
          "dialect": "아인교"
        },
        {
          "cardId": "ws-26111-05c03",
          "standard": "엄청 많더라",
          "dialect": "천지빼까리더라"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-05q01",
          "seq": 1,
          "cardId": "ws-26111-05c01",
          "prompt": "'문지르다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-05q01a",
              "text": "주아무라"
            },
            {
              "choiceId": "ws-26111-05q01b",
              "text": "문때다"
            },
            {
              "choiceId": "ws-26111-05q01c",
              "text": "널찌다"
            },
            {
              "choiceId": "ws-26111-05q01d",
              "text": "널쭈다"
            }
          ],
          "correctChoiceId": "ws-26111-05q01b",
          "explanation": "'문때다'는 경남에서 '문지르다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-05q02",
          "seq": 2,
          "cardId": "ws-26111-05c02",
          "prompt": "'아닙니까'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-05q02a",
              "text": "아인교"
            },
            {
              "choiceId": "ws-26111-05q02b",
              "text": "와"
            },
            {
              "choiceId": "ws-26111-05q02c",
              "text": "어데"
            },
            {
              "choiceId": "ws-26111-05q02d",
              "text": "오야"
            }
          ],
          "correctChoiceId": "ws-26111-05q02a",
          "explanation": "'아인교'는 경남에서 '아닙니까'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-05q03",
          "seq": 3,
          "cardId": "ws-26111-05c03",
          "prompt": "'엄청 많더라'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-05q03a",
              "text": "오만때만"
            },
            {
              "choiceId": "ws-26111-05q03b",
              "text": "천지빼까리더라"
            },
            {
              "choiceId": "ws-26111-05q03c",
              "text": "마이"
            },
            {
              "choiceId": "ws-26111-05q03d",
              "text": "내나"
            }
          ],
          "correctChoiceId": "ws-26111-05q03b",
          "explanation": "'천지빼까리더라'는 경남에서 '엄청 많더라'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-06",
      "seq": 6,
      "level": 2,
      "category": "종합",
      "title": "코스 2 단어 2",
      "cards": [
        {
          "cardId": "ws-26111-06c01",
          "standard": "어디",
          "dialect": "어데"
        },
        {
          "cardId": "ws-26111-06c02",
          "standard": "국수",
          "dialect": "국시"
        },
        {
          "cardId": "ws-26111-06c03",
          "standard": "별로다",
          "dialect": "파이다"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-06q01",
          "seq": 1,
          "cardId": "ws-26111-06c01",
          "prompt": "'어디'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-06q01a",
              "text": "어데"
            },
            {
              "choiceId": "ws-26111-06q01b",
              "text": "와"
            },
            {
              "choiceId": "ws-26111-06q01c",
              "text": "아인교"
            },
            {
              "choiceId": "ws-26111-06q01d",
              "text": "우야든동"
            }
          ],
          "correctChoiceId": "ws-26111-06q01a",
          "explanation": "'어데'는 경남에서 '어디'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-06q02",
          "seq": 2,
          "cardId": "ws-26111-06c02",
          "prompt": "'국수'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-06q02a",
              "text": "나락"
            },
            {
              "choiceId": "ws-26111-06q02b",
              "text": "게기"
            },
            {
              "choiceId": "ws-26111-06q02c",
              "text": "찌짐"
            },
            {
              "choiceId": "ws-26111-06q02d",
              "text": "국시"
            }
          ],
          "correctChoiceId": "ws-26111-06q02d",
          "explanation": "'국시'는 경남에서 '국수'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-06q03",
          "seq": 3,
          "cardId": "ws-26111-06c03",
          "prompt": "'별로다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-06q03a",
              "text": "대끼리다"
            },
            {
              "choiceId": "ws-26111-06q03b",
              "text": "쪼맨하다"
            },
            {
              "choiceId": "ws-26111-06q03c",
              "text": "애비다"
            },
            {
              "choiceId": "ws-26111-06q03d",
              "text": "파이다"
            }
          ],
          "correctChoiceId": "ws-26111-06q03d",
          "explanation": "'파이다'는 경남에서 '별로다'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-07",
      "seq": 7,
      "level": 2,
      "category": "종합",
      "title": "코스 2 단어 3",
      "cards": [
        {
          "cardId": "ws-26111-07c01",
          "standard": "데우다",
          "dialect": "데피다"
        },
        {
          "cardId": "ws-26111-07c02",
          "standard": "토끼",
          "dialect": "토깨이"
        },
        {
          "cardId": "ws-26111-07c03",
          "standard": "그러면",
          "dialect": "그라모"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-07q01",
          "seq": 1,
          "cardId": "ws-26111-07c01",
          "prompt": "'데우다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-07q01a",
              "text": "일나다"
            },
            {
              "choiceId": "ws-26111-07q01b",
              "text": "널찌다"
            },
            {
              "choiceId": "ws-26111-07q01c",
              "text": "낑가라"
            },
            {
              "choiceId": "ws-26111-07q01d",
              "text": "데피다"
            }
          ],
          "correctChoiceId": "ws-26111-07q01d",
          "explanation": "'데피다'는 경남에서 '데우다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-07q02",
          "seq": 2,
          "cardId": "ws-26111-07c02",
          "prompt": "'토끼'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-07q02a",
              "text": "강생이"
            },
            {
              "choiceId": "ws-26111-07q02b",
              "text": "토깨이"
            },
            {
              "choiceId": "ws-26111-07q02c",
              "text": "야시"
            },
            {
              "choiceId": "ws-26111-07q02d",
              "text": "놀갱이"
            }
          ],
          "correctChoiceId": "ws-26111-07q02b",
          "explanation": "'토깨이'는 경남에서 '토끼'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-07q03",
          "seq": 3,
          "cardId": "ws-26111-07c03",
          "prompt": "'그러면'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-07q03a",
              "text": "어데"
            },
            {
              "choiceId": "ws-26111-07q03b",
              "text": "그라모"
            },
            {
              "choiceId": "ws-26111-07q03c",
              "text": "마"
            },
            {
              "choiceId": "ws-26111-07q03d",
              "text": "오야"
            }
          ],
          "correctChoiceId": "ws-26111-07q03b",
          "explanation": "'그라모'는 경남에서 '그러면'을 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-08",
      "seq": 8,
      "level": 2,
      "category": "종합",
      "title": "코스 2 단어 4",
      "cards": [
        {
          "cardId": "ws-26111-08c01",
          "standard": "멸치",
          "dialect": "미르치"
        },
        {
          "cardId": "ws-26111-08c02",
          "standard": "가르치다",
          "dialect": "갈차주다"
        },
        {
          "cardId": "ws-26111-08c03",
          "standard": "멋 부림",
          "dialect": "깔롱"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-08q01",
          "seq": 1,
          "cardId": "ws-26111-08c01",
          "prompt": "'멸치'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-08q01a",
              "text": "무시"
            },
            {
              "choiceId": "ws-26111-08q01b",
              "text": "미르치"
            },
            {
              "choiceId": "ws-26111-08q01c",
              "text": "게기"
            },
            {
              "choiceId": "ws-26111-08q01d",
              "text": "정구지"
            }
          ],
          "correctChoiceId": "ws-26111-08q01b",
          "explanation": "'미르치'는 경남에서 '멸치'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-08q02",
          "seq": 2,
          "cardId": "ws-26111-08c02",
          "prompt": "'가르치다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-08q02a",
              "text": "갈차주다"
            },
            {
              "choiceId": "ws-26111-08q02b",
              "text": "문때다"
            },
            {
              "choiceId": "ws-26111-08q02c",
              "text": "데피다"
            },
            {
              "choiceId": "ws-26111-08q02d",
              "text": "묵다"
            }
          ],
          "correctChoiceId": "ws-26111-08q02a",
          "explanation": "'갈차주다'는 경남에서 '가르치다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-08q03",
          "seq": 3,
          "cardId": "ws-26111-08c03",
          "prompt": "'멋 부림'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-08q03a",
              "text": "깔롱"
            },
            {
              "choiceId": "ws-26111-08q03b",
              "text": "똑띠"
            },
            {
              "choiceId": "ws-26111-08q03c",
              "text": "천지빼까리더라"
            },
            {
              "choiceId": "ws-26111-08q03d",
              "text": "온전신에"
            }
          ],
          "correctChoiceId": "ws-26111-08q03a",
          "explanation": "'깔롱'은 경남에서 '멋 부림'을 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-09",
      "seq": 9,
      "level": 3,
      "category": "종합",
      "title": "코스 3 단어 1",
      "cards": [
        {
          "cardId": "ws-26111-09c01",
          "standard": "사내아이",
          "dialect": "머스마"
        },
        {
          "cardId": "ws-26111-09c02",
          "standard": "어린아이",
          "dialect": "얼라"
        },
        {
          "cardId": "ws-26111-09c03",
          "standard": "들어가다",
          "dialect": "들가다"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-09q01",
          "seq": 1,
          "cardId": "ws-26111-09c01",
          "prompt": "'사내아이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-09q01a",
              "text": "머스마"
            },
            {
              "choiceId": "ws-26111-09q01b",
              "text": "너거"
            },
            {
              "choiceId": "ws-26111-09q01c",
              "text": "가시나"
            },
            {
              "choiceId": "ws-26111-09q01d",
              "text": "얼라"
            }
          ],
          "correctChoiceId": "ws-26111-09q01a",
          "explanation": "'머스마'는 경남에서 '사내아이'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-09q02",
          "seq": 2,
          "cardId": "ws-26111-09c02",
          "prompt": "'어린아이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-09q02a",
              "text": "얼라"
            },
            {
              "choiceId": "ws-26111-09q02b",
              "text": "가시나"
            },
            {
              "choiceId": "ws-26111-09q02c",
              "text": "머스마"
            },
            {
              "choiceId": "ws-26111-09q02d",
              "text": "너거"
            }
          ],
          "correctChoiceId": "ws-26111-09q02a",
          "explanation": "'얼라'는 경남에서 '어린아이'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-09q03",
          "seq": 3,
          "cardId": "ws-26111-09c03",
          "prompt": "'들어가다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-09q03a",
              "text": "주아무라"
            },
            {
              "choiceId": "ws-26111-09q03b",
              "text": "데빌고 와라"
            },
            {
              "choiceId": "ws-26111-09q03c",
              "text": "들가다"
            },
            {
              "choiceId": "ws-26111-09q03d",
              "text": "디비자라"
            }
          ],
          "correctChoiceId": "ws-26111-09q03c",
          "explanation": "'들가다'는 경남에서 '들어가다'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-10",
      "seq": 10,
      "level": 3,
      "category": "종합",
      "title": "코스 3 단어 2",
      "cards": [
        {
          "cardId": "ws-26111-10c01",
          "standard": "너희",
          "dialect": "너거"
        },
        {
          "cardId": "ws-26111-10c02",
          "standard": "까뒤집다",
          "dialect": "까디비다"
        },
        {
          "cardId": "ws-26111-10c03",
          "standard": "날마다",
          "dialect": "만날천날"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-10q01",
          "seq": 1,
          "cardId": "ws-26111-10c01",
          "prompt": "'너희'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-10q01a",
              "text": "얼라"
            },
            {
              "choiceId": "ws-26111-10q01b",
              "text": "너거"
            },
            {
              "choiceId": "ws-26111-10q01c",
              "text": "머스마"
            },
            {
              "choiceId": "ws-26111-10q01d",
              "text": "가시나"
            }
          ],
          "correctChoiceId": "ws-26111-10q01b",
          "explanation": "'너거'는 경남에서 '너희'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-10q02",
          "seq": 2,
          "cardId": "ws-26111-10c02",
          "prompt": "'까뒤집다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-10q02a",
              "text": "옇어라"
            },
            {
              "choiceId": "ws-26111-10q02b",
              "text": "낑가라"
            },
            {
              "choiceId": "ws-26111-10q02c",
              "text": "까디비다"
            },
            {
              "choiceId": "ws-26111-10q02d",
              "text": "널찌다"
            }
          ],
          "correctChoiceId": "ws-26111-10q02c",
          "explanation": "'까디비다'는 경남에서 '까뒤집다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-10q03",
          "seq": 3,
          "cardId": "ws-26111-10c03",
          "prompt": "'날마다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-10q03a",
              "text": "퍼뜩"
            },
            {
              "choiceId": "ws-26111-10q03b",
              "text": "엥가이"
            },
            {
              "choiceId": "ws-26111-10q03c",
              "text": "만날천날"
            },
            {
              "choiceId": "ws-26111-10q03d",
              "text": "오만때만"
            }
          ],
          "correctChoiceId": "ws-26111-10q03c",
          "explanation": "'만날천날'은 경남에서 '날마다'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-11",
      "seq": 11,
      "level": 3,
      "category": "종합",
      "title": "코스 3 단어 3",
      "cards": [
        {
          "cardId": "ws-26111-11c01",
          "standard": "무슨",
          "dialect": "무신"
        },
        {
          "cardId": "ws-26111-11c02",
          "standard": "베개",
          "dialect": "비개"
        },
        {
          "cardId": "ws-26111-11c03",
          "standard": "어떻게든",
          "dialect": "우야든동"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-11q01",
          "seq": 1,
          "cardId": "ws-26111-11c01",
          "prompt": "'무슨'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-11q01a",
              "text": "우야든동"
            },
            {
              "choiceId": "ws-26111-11q01b",
              "text": "고마"
            },
            {
              "choiceId": "ws-26111-11q01c",
              "text": "하모"
            },
            {
              "choiceId": "ws-26111-11q01d",
              "text": "무신"
            }
          ],
          "correctChoiceId": "ws-26111-11q01d",
          "explanation": "'무신'은 경남에서 '무슨'을 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-11q02",
          "seq": 2,
          "cardId": "ws-26111-11c02",
          "prompt": "'베개'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-11q02a",
              "text": "정지"
            },
            {
              "choiceId": "ws-26111-11q02b",
              "text": "가새"
            },
            {
              "choiceId": "ws-26111-11q02c",
              "text": "저까치"
            },
            {
              "choiceId": "ws-26111-11q02d",
              "text": "비개"
            }
          ],
          "correctChoiceId": "ws-26111-11q02d",
          "explanation": "'비개'는 경남에서 '베개'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-11q03",
          "seq": 3,
          "cardId": "ws-26111-11c03",
          "prompt": "'어떻게든'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-11q03a",
              "text": "오야"
            },
            {
              "choiceId": "ws-26111-11q03b",
              "text": "우예"
            },
            {
              "choiceId": "ws-26111-11q03c",
              "text": "우야든동"
            },
            {
              "choiceId": "ws-26111-11q03d",
              "text": "마"
            }
          ],
          "correctChoiceId": "ws-26111-11q03c",
          "explanation": "'우야든동'은 경남에서 '어떻게든'을 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-12",
      "seq": 12,
      "level": 3,
      "category": "종합",
      "title": "코스 3 단어 4",
      "cards": [
        {
          "cardId": "ws-26111-12c01",
          "standard": "젓가락",
          "dialect": "저까치"
        },
        {
          "cardId": "ws-26111-12c02",
          "standard": "그만",
          "dialect": "고마"
        },
        {
          "cardId": "ws-26111-12c03",
          "standard": "어찌",
          "dialect": "우예"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-12q01",
          "seq": 1,
          "cardId": "ws-26111-12c01",
          "prompt": "'젓가락'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-12q01a",
              "text": "이바구"
            },
            {
              "choiceId": "ws-26111-12q01b",
              "text": "비개"
            },
            {
              "choiceId": "ws-26111-12q01c",
              "text": "저까치"
            },
            {
              "choiceId": "ws-26111-12q01d",
              "text": "정지"
            }
          ],
          "correctChoiceId": "ws-26111-12q01c",
          "explanation": "'저까치'는 경남에서 '젓가락'을 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-12q02",
          "seq": 2,
          "cardId": "ws-26111-12c02",
          "prompt": "'그만'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-12q02a",
              "text": "고마"
            },
            {
              "choiceId": "ws-26111-12q02b",
              "text": "오야"
            },
            {
              "choiceId": "ws-26111-12q02c",
              "text": "어데"
            },
            {
              "choiceId": "ws-26111-12q02d",
              "text": "마"
            }
          ],
          "correctChoiceId": "ws-26111-12q02a",
          "explanation": "'고마'는 경남에서 '그만'을 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-12q03",
          "seq": 3,
          "cardId": "ws-26111-12c03",
          "prompt": "'어찌'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-12q03a",
              "text": "오야"
            },
            {
              "choiceId": "ws-26111-12q03b",
              "text": "우예"
            },
            {
              "choiceId": "ws-26111-12q03c",
              "text": "마"
            },
            {
              "choiceId": "ws-26111-12q03d",
              "text": "고마"
            }
          ],
          "correctChoiceId": "ws-26111-12q03b",
          "explanation": "'우예'는 경남에서 '어찌'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-13",
      "seq": 13,
      "level": 4,
      "category": "종합",
      "title": "코스 4 단어 1",
      "cards": [
        {
          "cardId": "ws-26111-13c01",
          "standard": "부침개",
          "dialect": "찌짐"
        },
        {
          "cardId": "ws-26111-13c02",
          "standard": "여우",
          "dialect": "야시"
        },
        {
          "cardId": "ws-26111-13c03",
          "standard": "데리고 와라",
          "dialect": "데빌고 와라"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-13q01",
          "seq": 1,
          "cardId": "ws-26111-13c01",
          "prompt": "'부침개'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-13q01a",
              "text": "나락"
            },
            {
              "choiceId": "ws-26111-13q01b",
              "text": "국시"
            },
            {
              "choiceId": "ws-26111-13q01c",
              "text": "찌짐"
            },
            {
              "choiceId": "ws-26111-13q01d",
              "text": "게기"
            }
          ],
          "correctChoiceId": "ws-26111-13q01c",
          "explanation": "'찌짐'은 경남에서 '부침개'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-13q02",
          "seq": 2,
          "cardId": "ws-26111-13c02",
          "prompt": "'여우'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-13q02a",
              "text": "강생이"
            },
            {
              "choiceId": "ws-26111-13q02b",
              "text": "토깨이"
            },
            {
              "choiceId": "ws-26111-13q02c",
              "text": "놀갱이"
            },
            {
              "choiceId": "ws-26111-13q02d",
              "text": "야시"
            }
          ],
          "correctChoiceId": "ws-26111-13q02d",
          "explanation": "'야시'는 경남에서 '여우'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-13q03",
          "seq": 3,
          "cardId": "ws-26111-13c03",
          "prompt": "'데리고 와라'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-13q03a",
              "text": "들가다"
            },
            {
              "choiceId": "ws-26111-13q03b",
              "text": "디비자라"
            },
            {
              "choiceId": "ws-26111-13q03c",
              "text": "댕기다"
            },
            {
              "choiceId": "ws-26111-13q03d",
              "text": "데빌고 와라"
            }
          ],
          "correctChoiceId": "ws-26111-13q03d",
          "explanation": "'데빌고 와라'는 경남에서 '데리고 와라'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-14",
      "seq": 14,
      "level": 4,
      "category": "종합",
      "title": "코스 4 단어 2",
      "cards": [
        {
          "cardId": "ws-26111-14c01",
          "standard": "때리다",
          "dialect": "쌔리다"
        },
        {
          "cardId": "ws-26111-14c02",
          "standard": "고기",
          "dialect": "게기"
        },
        {
          "cardId": "ws-26111-14c03",
          "standard": "여기저기",
          "dialect": "오만때만"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-14q01",
          "seq": 1,
          "cardId": "ws-26111-14c01",
          "prompt": "'때리다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-14q01a",
              "text": "널쭈다"
            },
            {
              "choiceId": "ws-26111-14q01b",
              "text": "쌔리다"
            },
            {
              "choiceId": "ws-26111-14q01c",
              "text": "낑가라"
            },
            {
              "choiceId": "ws-26111-14q01d",
              "text": "옇어라"
            }
          ],
          "correctChoiceId": "ws-26111-14q01b",
          "explanation": "'쌔리다'는 경남에서 '때리다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-14q02",
          "seq": 2,
          "cardId": "ws-26111-14c02",
          "prompt": "'고기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-14q02a",
              "text": "게기"
            },
            {
              "choiceId": "ws-26111-14q02b",
              "text": "찌짐"
            },
            {
              "choiceId": "ws-26111-14q02c",
              "text": "무시"
            },
            {
              "choiceId": "ws-26111-14q02d",
              "text": "국시"
            }
          ],
          "correctChoiceId": "ws-26111-14q02a",
          "explanation": "'게기'는 경남에서 '고기'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-14q03",
          "seq": 3,
          "cardId": "ws-26111-14c03",
          "prompt": "'여기저기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-14q03a",
              "text": "내나"
            },
            {
              "choiceId": "ws-26111-14q03b",
              "text": "짜다시리"
            },
            {
              "choiceId": "ws-26111-14q03c",
              "text": "오만때만"
            },
            {
              "choiceId": "ws-26111-14q03d",
              "text": "온전신에"
            }
          ],
          "correctChoiceId": "ws-26111-14q03c",
          "explanation": "'오만때만'은 경남에서 '여기저기'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-15",
      "seq": 15,
      "level": 4,
      "category": "종합",
      "title": "코스 4 단어 3",
      "cards": [
        {
          "cardId": "ws-26111-15c01",
          "standard": "야위다",
          "dialect": "애비다"
        },
        {
          "cardId": "ws-26111-15c02",
          "standard": "먹다",
          "dialect": "묵다"
        },
        {
          "cardId": "ws-26111-15c03",
          "standard": "남은 거 다 먹어라",
          "dialect": "주아무라"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-15q01",
          "seq": 1,
          "cardId": "ws-26111-15c01",
          "prompt": "'야위다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-15q01a",
              "text": "애비다"
            },
            {
              "choiceId": "ws-26111-15q01b",
              "text": "대끼리다"
            },
            {
              "choiceId": "ws-26111-15q01c",
              "text": "따시다"
            },
            {
              "choiceId": "ws-26111-15q01d",
              "text": "애럽다"
            }
          ],
          "correctChoiceId": "ws-26111-15q01a",
          "explanation": "'애비다'는 경남에서 '야위다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-15q02",
          "seq": 2,
          "cardId": "ws-26111-15c02",
          "prompt": "'먹다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-15q02a",
              "text": "일나다"
            },
            {
              "choiceId": "ws-26111-15q02b",
              "text": "묵다"
            },
            {
              "choiceId": "ws-26111-15q02c",
              "text": "널찌다"
            },
            {
              "choiceId": "ws-26111-15q02d",
              "text": "까디비다"
            }
          ],
          "correctChoiceId": "ws-26111-15q02b",
          "explanation": "'묵다'는 경남에서 '먹다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-15q03",
          "seq": 3,
          "cardId": "ws-26111-15c03",
          "prompt": "'남은 거 다 먹어라'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-15q03a",
              "text": "데피다"
            },
            {
              "choiceId": "ws-26111-15q03b",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws-26111-15q03c",
              "text": "디비자라"
            },
            {
              "choiceId": "ws-26111-15q03d",
              "text": "주아무라"
            }
          ],
          "correctChoiceId": "ws-26111-15q03d",
          "explanation": "'주아무라'는 경남에서 '남은 거 다 먹어라'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-16",
      "seq": 16,
      "level": 4,
      "category": "종합",
      "title": "코스 4 단어 4",
      "cards": [
        {
          "cardId": "ws-26111-16c01",
          "standard": "똑바로",
          "dialect": "똑띠"
        },
        {
          "cardId": "ws-26111-16c02",
          "standard": "온통",
          "dialect": "온전신에"
        },
        {
          "cardId": "ws-26111-16c03",
          "standard": "강아지",
          "dialect": "강생이"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-16q01",
          "seq": 1,
          "cardId": "ws-26111-16c01",
          "prompt": "'똑바로'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-16q01a",
              "text": "만날천날"
            },
            {
              "choiceId": "ws-26111-16q01b",
              "text": "온전신에"
            },
            {
              "choiceId": "ws-26111-16q01c",
              "text": "똑띠"
            },
            {
              "choiceId": "ws-26111-16q01d",
              "text": "마이"
            }
          ],
          "correctChoiceId": "ws-26111-16q01c",
          "explanation": "'똑띠'는 경남에서 '똑바로'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-16q02",
          "seq": 2,
          "cardId": "ws-26111-16c02",
          "prompt": "'온통'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-16q02a",
              "text": "깔롱"
            },
            {
              "choiceId": "ws-26111-16q02b",
              "text": "온전신에"
            },
            {
              "choiceId": "ws-26111-16q02c",
              "text": "오만때만"
            },
            {
              "choiceId": "ws-26111-16q02d",
              "text": "마이"
            }
          ],
          "correctChoiceId": "ws-26111-16q02b",
          "explanation": "'온전신에'는 경남에서 '온통'을 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-16q03",
          "seq": 3,
          "cardId": "ws-26111-16c03",
          "prompt": "'강아지'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-16q03a",
              "text": "토깨이"
            },
            {
              "choiceId": "ws-26111-16q03b",
              "text": "야시"
            },
            {
              "choiceId": "ws-26111-16q03c",
              "text": "강생이"
            },
            {
              "choiceId": "ws-26111-16q03d",
              "text": "놀갱이"
            }
          ],
          "correctChoiceId": "ws-26111-16q03c",
          "explanation": "'강생이'는 경남에서 '강아지'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-17",
      "seq": 17,
      "level": 5,
      "category": "종합",
      "title": "코스 5 단어 1",
      "cards": [
        {
          "cardId": "ws-26111-17c01",
          "standard": "아무렴",
          "dialect": "하모"
        },
        {
          "cardId": "ws-26111-17c02",
          "standard": "일어나다",
          "dialect": "일나다"
        },
        {
          "cardId": "ws-26111-17c03",
          "standard": "김치",
          "dialect": "짐치"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-17q01",
          "seq": 1,
          "cardId": "ws-26111-17c01",
          "prompt": "'아무렴'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-17q01a",
              "text": "어데"
            },
            {
              "choiceId": "ws-26111-17q01b",
              "text": "아인교"
            },
            {
              "choiceId": "ws-26111-17q01c",
              "text": "무신"
            },
            {
              "choiceId": "ws-26111-17q01d",
              "text": "하모"
            }
          ],
          "correctChoiceId": "ws-26111-17q01d",
          "explanation": "'하모'는 경남에서 '아무렴'을 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-17q02",
          "seq": 2,
          "cardId": "ws-26111-17c02",
          "prompt": "'일어나다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-17q02a",
              "text": "문때다"
            },
            {
              "choiceId": "ws-26111-17q02b",
              "text": "데피다"
            },
            {
              "choiceId": "ws-26111-17q02c",
              "text": "댕기다"
            },
            {
              "choiceId": "ws-26111-17q02d",
              "text": "일나다"
            }
          ],
          "correctChoiceId": "ws-26111-17q02d",
          "explanation": "'일나다'는 경남에서 '일어나다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-17q03",
          "seq": 3,
          "cardId": "ws-26111-17c03",
          "prompt": "'김치'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-17q03a",
              "text": "미르치"
            },
            {
              "choiceId": "ws-26111-17q03b",
              "text": "짐치"
            },
            {
              "choiceId": "ws-26111-17q03c",
              "text": "찌짐"
            },
            {
              "choiceId": "ws-26111-17q03d",
              "text": "정구지"
            }
          ],
          "correctChoiceId": "ws-26111-17q03b",
          "explanation": "'짐치'는 경남에서 '김치'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-18",
      "seq": 18,
      "level": 5,
      "category": "종합",
      "title": "코스 5 단어 2",
      "cards": [
        {
          "cardId": "ws-26111-18c01",
          "standard": "다니다",
          "dialect": "댕기다"
        },
        {
          "cardId": "ws-26111-18c02",
          "standard": "가위",
          "dialect": "가새"
        },
        {
          "cardId": "ws-26111-18c03",
          "standard": "어렵다",
          "dialect": "애럽다"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-18q01",
          "seq": 1,
          "cardId": "ws-26111-18c01",
          "prompt": "'다니다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-18q01a",
              "text": "댕기다"
            },
            {
              "choiceId": "ws-26111-18q01b",
              "text": "들가다"
            },
            {
              "choiceId": "ws-26111-18q01c",
              "text": "옇어라"
            },
            {
              "choiceId": "ws-26111-18q01d",
              "text": "까디비다"
            }
          ],
          "correctChoiceId": "ws-26111-18q01a",
          "explanation": "'댕기다'는 경남에서 '다니다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-18q02",
          "seq": 2,
          "cardId": "ws-26111-18c02",
          "prompt": "'가위'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-18q02a",
              "text": "가새"
            },
            {
              "choiceId": "ws-26111-18q02b",
              "text": "비개"
            },
            {
              "choiceId": "ws-26111-18q02c",
              "text": "이바구"
            },
            {
              "choiceId": "ws-26111-18q02d",
              "text": "저까치"
            }
          ],
          "correctChoiceId": "ws-26111-18q02a",
          "explanation": "'가새'는 경남에서 '가위'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-18q03",
          "seq": 3,
          "cardId": "ws-26111-18c03",
          "prompt": "'어렵다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-18q03a",
              "text": "쪼맨하다"
            },
            {
              "choiceId": "ws-26111-18q03b",
              "text": "애비다"
            },
            {
              "choiceId": "ws-26111-18q03c",
              "text": "대끼리다"
            },
            {
              "choiceId": "ws-26111-18q03d",
              "text": "애럽다"
            }
          ],
          "correctChoiceId": "ws-26111-18q03d",
          "explanation": "'애럽다'는 경남에서 '어렵다'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-19",
      "seq": 19,
      "level": 5,
      "category": "종합",
      "title": "코스 5 단어 3",
      "cards": [
        {
          "cardId": "ws-26111-19c01",
          "standard": "그다지",
          "dialect": "짜다시리"
        },
        {
          "cardId": "ws-26111-19c02",
          "standard": "떨어지다",
          "dialect": "널찌다"
        },
        {
          "cardId": "ws-26111-19c03",
          "standard": "노루",
          "dialect": "놀갱이"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-19q01",
          "seq": 1,
          "cardId": "ws-26111-19c01",
          "prompt": "'그다지'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-19q01a",
              "text": "퍼뜩"
            },
            {
              "choiceId": "ws-26111-19q01b",
              "text": "마이"
            },
            {
              "choiceId": "ws-26111-19q01c",
              "text": "짜다시리"
            },
            {
              "choiceId": "ws-26111-19q01d",
              "text": "쪼매"
            }
          ],
          "correctChoiceId": "ws-26111-19q01c",
          "explanation": "'짜다시리'는 경남에서 '그다지'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-19q02",
          "seq": 2,
          "cardId": "ws-26111-19c02",
          "prompt": "'떨어지다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-19q02a",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws-26111-19q02b",
              "text": "널쭈다"
            },
            {
              "choiceId": "ws-26111-19q02c",
              "text": "낑가라"
            },
            {
              "choiceId": "ws-26111-19q02d",
              "text": "널찌다"
            }
          ],
          "correctChoiceId": "ws-26111-19q02d",
          "explanation": "'널찌다'는 경남에서 '떨어지다'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-19q03",
          "seq": 3,
          "cardId": "ws-26111-19c03",
          "prompt": "'노루'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-19q03a",
              "text": "놀갱이"
            },
            {
              "choiceId": "ws-26111-19q03b",
              "text": "야시"
            },
            {
              "choiceId": "ws-26111-19q03c",
              "text": "토깨이"
            },
            {
              "choiceId": "ws-26111-19q03d",
              "text": "강생이"
            }
          ],
          "correctChoiceId": "ws-26111-19q03a",
          "explanation": "'놀갱이'는 경남에서 '노루'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws-26111-20",
      "seq": 20,
      "level": 5,
      "category": "종합",
      "title": "코스 5 단어 4",
      "cards": [
        {
          "cardId": "ws-26111-20c01",
          "standard": "이야기",
          "dialect": "이바구"
        },
        {
          "cardId": "ws-26111-20c02",
          "standard": "부엌",
          "dialect": "정지"
        },
        {
          "cardId": "ws-26111-20c03",
          "standard": "최고다",
          "dialect": "대끼리다"
        }
      ],
      "items": [
        {
          "itemId": "ws-26111-20q01",
          "seq": 1,
          "cardId": "ws-26111-20c01",
          "prompt": "'이야기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-20q01a",
              "text": "비개"
            },
            {
              "choiceId": "ws-26111-20q01b",
              "text": "이바구"
            },
            {
              "choiceId": "ws-26111-20q01c",
              "text": "정지"
            },
            {
              "choiceId": "ws-26111-20q01d",
              "text": "가새"
            }
          ],
          "correctChoiceId": "ws-26111-20q01b",
          "explanation": "'이바구'는 경남에서 '이야기'를 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-20q02",
          "seq": 2,
          "cardId": "ws-26111-20c02",
          "prompt": "'부엌'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-20q02a",
              "text": "이바구"
            },
            {
              "choiceId": "ws-26111-20q02b",
              "text": "정지"
            },
            {
              "choiceId": "ws-26111-20q02c",
              "text": "비개"
            },
            {
              "choiceId": "ws-26111-20q02d",
              "text": "저까치"
            }
          ],
          "correctChoiceId": "ws-26111-20q02b",
          "explanation": "'정지'는 경남에서 '부엌'을 이르는 말입니다."
        },
        {
          "itemId": "ws-26111-20q03",
          "seq": 3,
          "cardId": "ws-26111-20c03",
          "prompt": "'최고다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws-26111-20q03a",
              "text": "대끼리다"
            },
            {
              "choiceId": "ws-26111-20q03b",
              "text": "따시다"
            },
            {
              "choiceId": "ws-26111-20q03c",
              "text": "파이다"
            },
            {
              "choiceId": "ws-26111-20q03d",
              "text": "쪼맨하다"
            }
          ],
          "correctChoiceId": "ws-26111-20q03a",
          "explanation": "'대끼리다'는 경남에서 '최고다'를 이르는 말입니다."
        }
      ]
    }
  ]
}$definition$,
        timestamp with time zone '2026-11-15T00:00:00Z');
