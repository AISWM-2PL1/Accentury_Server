-- KAN-265: 단어 학습 발행본 wd-gn-2026.10.1 - 어휘 세트 11개, 카드 72장 (레벨 1 1개, 레벨 2 3개, 레벨 3 5개, 레벨 4 2개).
--
-- 이 파일은 손으로 쓰지 않는다 - tools/content/build_word_learning.py가 검수한 레벨테스트 어휘
-- (curation_gn_2026_10_2.py)에서 만든다. 세트는 분류별(10카드 상한), 레벨은 낱말 근거의 평균, 문항은
-- 표준어 → 사투리 한 방향이고 오답은 같은 분류에서 contentVersion을 시드로 뽑는다 (명세서 §3.16).
--
-- 발행 후 불변이다 (§5.4와 같은 규칙). 해설이나 세트 구성이 바뀌면 이 행을 UPDATE하지 않고
-- 새 contentVersion으로 INSERT한다 - 진행 중인 시도가 자기 버전의 문항을 계속 봐야 한다.
-- 활성 전환 행은 없다 - 서버가 발행 시각이 가장 늦은 발행본을 목록과 상세에 쓴다.
--
-- 예외 (KAN-276, 2026-10-08): 학습 발행본이 아직 초기 단계라 이 행은 검수한 원천으로 같은 contentVersion을
-- 덮어쓴 것이다. 이미 적용된 환경(staging)은 정의 행 교체와 flyway repair로 맞춘다. 다음부터는 새 contentVersion이다.
insert into word_learning_definition (content_version, dialect, body, published_at)
values ('wd-gn-2026.10.1', 'GYEONGNAM', $definition${
  "contentVersion": "wd-gn-2026.10.1",
  "dialect": "GYEONGNAM",
  "sets": [
    {
      "setId": "ws01",
      "seq": 1,
      "level": 2,
      "category": "음식과 식재료",
      "title": "음식과 식재료",
      "cards": [
        {
          "cardId": "ws01c01",
          "standard": "부추",
          "dialect": "정구지"
        },
        {
          "cardId": "ws01c02",
          "standard": "부침개",
          "dialect": "찌짐"
        },
        {
          "cardId": "ws01c03",
          "standard": "국수",
          "dialect": "국시"
        },
        {
          "cardId": "ws01c04",
          "standard": "벼",
          "dialect": "나락"
        },
        {
          "cardId": "ws01c05",
          "standard": "고기",
          "dialect": "게기"
        },
        {
          "cardId": "ws01c06",
          "standard": "김치",
          "dialect": "짐치"
        },
        {
          "cardId": "ws01c07",
          "standard": "무",
          "dialect": "무시"
        },
        {
          "cardId": "ws01c08",
          "standard": "멸치",
          "dialect": "미르치"
        }
      ],
      "items": [
        {
          "itemId": "ws01q01",
          "seq": 1,
          "cardId": "ws01c01",
          "prompt": "'부추'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws01q01a",
              "text": "나락"
            },
            {
              "choiceId": "ws01q01b",
              "text": "게기"
            },
            {
              "choiceId": "ws01q01c",
              "text": "짐치"
            },
            {
              "choiceId": "ws01q01d",
              "text": "정구지"
            }
          ],
          "correctChoiceId": "ws01q01d",
          "explanation": "'정구지'는 경남에서 '부추'를 이르는 말입니다."
        },
        {
          "itemId": "ws01q02",
          "seq": 2,
          "cardId": "ws01c02",
          "prompt": "'부침개'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws01q02a",
              "text": "국시"
            },
            {
              "choiceId": "ws01q02b",
              "text": "게기"
            },
            {
              "choiceId": "ws01q02c",
              "text": "짐치"
            },
            {
              "choiceId": "ws01q02d",
              "text": "찌짐"
            }
          ],
          "correctChoiceId": "ws01q02d",
          "explanation": "'찌짐'은 경남에서 '부침개'를 이르는 말입니다."
        },
        {
          "itemId": "ws01q03",
          "seq": 3,
          "cardId": "ws01c03",
          "prompt": "'국수'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws01q03a",
              "text": "미르치"
            },
            {
              "choiceId": "ws01q03b",
              "text": "짐치"
            },
            {
              "choiceId": "ws01q03c",
              "text": "무시"
            },
            {
              "choiceId": "ws01q03d",
              "text": "국시"
            }
          ],
          "correctChoiceId": "ws01q03d",
          "explanation": "'국시'는 경남에서 '국수'를 이르는 말입니다."
        },
        {
          "itemId": "ws01q04",
          "seq": 4,
          "cardId": "ws01c04",
          "prompt": "'벼'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws01q04a",
              "text": "국시"
            },
            {
              "choiceId": "ws01q04b",
              "text": "나락"
            },
            {
              "choiceId": "ws01q04c",
              "text": "무시"
            },
            {
              "choiceId": "ws01q04d",
              "text": "찌짐"
            }
          ],
          "correctChoiceId": "ws01q04b",
          "explanation": "'나락'은 경남에서 '벼'를 이르는 말입니다."
        },
        {
          "itemId": "ws01q05",
          "seq": 5,
          "cardId": "ws01c05",
          "prompt": "'고기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws01q05a",
              "text": "정구지"
            },
            {
              "choiceId": "ws01q05b",
              "text": "무시"
            },
            {
              "choiceId": "ws01q05c",
              "text": "게기"
            },
            {
              "choiceId": "ws01q05d",
              "text": "짐치"
            }
          ],
          "correctChoiceId": "ws01q05c",
          "explanation": "'게기'는 경남에서 '고기'를 이르는 말입니다."
        },
        {
          "itemId": "ws01q06",
          "seq": 6,
          "cardId": "ws01c06",
          "prompt": "'김치'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws01q06a",
              "text": "짐치"
            },
            {
              "choiceId": "ws01q06b",
              "text": "찌짐"
            },
            {
              "choiceId": "ws01q06c",
              "text": "미르치"
            },
            {
              "choiceId": "ws01q06d",
              "text": "게기"
            }
          ],
          "correctChoiceId": "ws01q06a",
          "explanation": "'짐치'는 경남에서 '김치'를 이르는 말입니다."
        },
        {
          "itemId": "ws01q07",
          "seq": 7,
          "cardId": "ws01c07",
          "prompt": "'무'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws01q07a",
              "text": "정구지"
            },
            {
              "choiceId": "ws01q07b",
              "text": "무시"
            },
            {
              "choiceId": "ws01q07c",
              "text": "미르치"
            },
            {
              "choiceId": "ws01q07d",
              "text": "짐치"
            }
          ],
          "correctChoiceId": "ws01q07b",
          "explanation": "'무시'는 경남에서 '무'를 이르는 말입니다."
        },
        {
          "itemId": "ws01q08",
          "seq": 8,
          "cardId": "ws01c08",
          "prompt": "'멸치'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws01q08a",
              "text": "짐치"
            },
            {
              "choiceId": "ws01q08b",
              "text": "미르치"
            },
            {
              "choiceId": "ws01q08c",
              "text": "게기"
            },
            {
              "choiceId": "ws01q08d",
              "text": "나락"
            }
          ],
          "correctChoiceId": "ws01q08b",
          "explanation": "'미르치'는 경남에서 '멸치'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws02",
      "seq": 2,
      "level": 3,
      "category": "집과 살림",
      "title": "집과 살림",
      "cards": [
        {
          "cardId": "ws02c01",
          "standard": "부엌",
          "dialect": "정지"
        },
        {
          "cardId": "ws02c02",
          "standard": "젓가락",
          "dialect": "저까치"
        },
        {
          "cardId": "ws02c03",
          "standard": "이야기",
          "dialect": "이바구"
        },
        {
          "cardId": "ws02c04",
          "standard": "베개",
          "dialect": "비개"
        },
        {
          "cardId": "ws02c05",
          "standard": "가위",
          "dialect": "가새"
        }
      ],
      "items": [
        {
          "itemId": "ws02q01",
          "seq": 1,
          "cardId": "ws02c01",
          "prompt": "'부엌'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws02q01a",
              "text": "비개"
            },
            {
              "choiceId": "ws02q01b",
              "text": "이바구"
            },
            {
              "choiceId": "ws02q01c",
              "text": "가새"
            },
            {
              "choiceId": "ws02q01d",
              "text": "정지"
            }
          ],
          "correctChoiceId": "ws02q01d",
          "explanation": "'정지'는 경남에서 '부엌'을 이르는 말입니다."
        },
        {
          "itemId": "ws02q02",
          "seq": 2,
          "cardId": "ws02c02",
          "prompt": "'젓가락'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws02q02a",
              "text": "정지"
            },
            {
              "choiceId": "ws02q02b",
              "text": "저까치"
            },
            {
              "choiceId": "ws02q02c",
              "text": "이바구"
            },
            {
              "choiceId": "ws02q02d",
              "text": "비개"
            }
          ],
          "correctChoiceId": "ws02q02b",
          "explanation": "'저까치'는 경남에서 '젓가락'을 이르는 말입니다."
        },
        {
          "itemId": "ws02q03",
          "seq": 3,
          "cardId": "ws02c03",
          "prompt": "'이야기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws02q03a",
              "text": "정지"
            },
            {
              "choiceId": "ws02q03b",
              "text": "이바구"
            },
            {
              "choiceId": "ws02q03c",
              "text": "가새"
            },
            {
              "choiceId": "ws02q03d",
              "text": "비개"
            }
          ],
          "correctChoiceId": "ws02q03b",
          "explanation": "'이바구'는 경남에서 '이야기'를 이르는 말입니다."
        },
        {
          "itemId": "ws02q04",
          "seq": 4,
          "cardId": "ws02c04",
          "prompt": "'베개'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws02q04a",
              "text": "비개"
            },
            {
              "choiceId": "ws02q04b",
              "text": "저까치"
            },
            {
              "choiceId": "ws02q04c",
              "text": "정지"
            },
            {
              "choiceId": "ws02q04d",
              "text": "이바구"
            }
          ],
          "correctChoiceId": "ws02q04a",
          "explanation": "'비개'는 경남에서 '베개'를 이르는 말입니다."
        },
        {
          "itemId": "ws02q05",
          "seq": 5,
          "cardId": "ws02c05",
          "prompt": "'가위'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws02q05a",
              "text": "비개"
            },
            {
              "choiceId": "ws02q05b",
              "text": "가새"
            },
            {
              "choiceId": "ws02q05c",
              "text": "이바구"
            },
            {
              "choiceId": "ws02q05d",
              "text": "저까치"
            }
          ],
          "correctChoiceId": "ws02q05b",
          "explanation": "'가새'는 경남에서 '가위'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws03",
      "seq": 3,
      "level": 3,
      "category": "사람과 친족",
      "title": "사람과 친족",
      "cards": [
        {
          "cardId": "ws03c01",
          "standard": "사내아이",
          "dialect": "머스마"
        },
        {
          "cardId": "ws03c02",
          "standard": "어린아이",
          "dialect": "얼라"
        },
        {
          "cardId": "ws03c03",
          "standard": "여자아이",
          "dialect": "가시나"
        },
        {
          "cardId": "ws03c04",
          "standard": "너희",
          "dialect": "너거"
        }
      ],
      "items": [
        {
          "itemId": "ws03q01",
          "seq": 1,
          "cardId": "ws03c01",
          "prompt": "'사내아이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws03q01a",
              "text": "너거"
            },
            {
              "choiceId": "ws03q01b",
              "text": "머스마"
            },
            {
              "choiceId": "ws03q01c",
              "text": "가시나"
            },
            {
              "choiceId": "ws03q01d",
              "text": "얼라"
            }
          ],
          "correctChoiceId": "ws03q01b",
          "explanation": "'머스마'는 경남에서 '사내아이'를 이르는 말입니다."
        },
        {
          "itemId": "ws03q02",
          "seq": 2,
          "cardId": "ws03c02",
          "prompt": "'어린아이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws03q02a",
              "text": "머스마"
            },
            {
              "choiceId": "ws03q02b",
              "text": "너거"
            },
            {
              "choiceId": "ws03q02c",
              "text": "얼라"
            },
            {
              "choiceId": "ws03q02d",
              "text": "가시나"
            }
          ],
          "correctChoiceId": "ws03q02c",
          "explanation": "'얼라'는 경남에서 '어린아이'를 이르는 말입니다."
        },
        {
          "itemId": "ws03q03",
          "seq": 3,
          "cardId": "ws03c03",
          "prompt": "'여자아이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws03q03a",
              "text": "너거"
            },
            {
              "choiceId": "ws03q03b",
              "text": "머스마"
            },
            {
              "choiceId": "ws03q03c",
              "text": "얼라"
            },
            {
              "choiceId": "ws03q03d",
              "text": "가시나"
            }
          ],
          "correctChoiceId": "ws03q03d",
          "explanation": "'가시나'는 경남에서 '여자아이'를 이르는 말입니다."
        },
        {
          "itemId": "ws03q04",
          "seq": 4,
          "cardId": "ws03c04",
          "prompt": "'너희'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws03q04a",
              "text": "너거"
            },
            {
              "choiceId": "ws03q04b",
              "text": "가시나"
            },
            {
              "choiceId": "ws03q04c",
              "text": "머스마"
            },
            {
              "choiceId": "ws03q04d",
              "text": "얼라"
            }
          ],
          "correctChoiceId": "ws03q04a",
          "explanation": "'너거'는 경남에서 '너희'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws04",
      "seq": 4,
      "level": 4,
      "category": "동물과 자연",
      "title": "동물과 자연",
      "cards": [
        {
          "cardId": "ws04c01",
          "standard": "노루",
          "dialect": "놀갱이"
        },
        {
          "cardId": "ws04c02",
          "standard": "강아지",
          "dialect": "강생이"
        },
        {
          "cardId": "ws04c03",
          "standard": "여우",
          "dialect": "야시"
        },
        {
          "cardId": "ws04c04",
          "standard": "토끼",
          "dialect": "토깨이"
        },
        {
          "cardId": "ws04c05",
          "standard": "염소",
          "dialect": "염생이"
        }
      ],
      "items": [
        {
          "itemId": "ws04q01",
          "seq": 1,
          "cardId": "ws04c01",
          "prompt": "'노루'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q01a",
              "text": "토깨이"
            },
            {
              "choiceId": "ws04q01b",
              "text": "강생이"
            },
            {
              "choiceId": "ws04q01c",
              "text": "염생이"
            },
            {
              "choiceId": "ws04q01d",
              "text": "놀갱이"
            }
          ],
          "correctChoiceId": "ws04q01d",
          "explanation": "'놀갱이'는 경남에서 '노루'를 이르는 말입니다."
        },
        {
          "itemId": "ws04q02",
          "seq": 2,
          "cardId": "ws04c02",
          "prompt": "'강아지'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q02a",
              "text": "놀갱이"
            },
            {
              "choiceId": "ws04q02b",
              "text": "야시"
            },
            {
              "choiceId": "ws04q02c",
              "text": "토깨이"
            },
            {
              "choiceId": "ws04q02d",
              "text": "강생이"
            }
          ],
          "correctChoiceId": "ws04q02d",
          "explanation": "'강생이'는 경남에서 '강아지'를 이르는 말입니다."
        },
        {
          "itemId": "ws04q03",
          "seq": 3,
          "cardId": "ws04c03",
          "prompt": "'여우'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q03a",
              "text": "강생이"
            },
            {
              "choiceId": "ws04q03b",
              "text": "놀갱이"
            },
            {
              "choiceId": "ws04q03c",
              "text": "염생이"
            },
            {
              "choiceId": "ws04q03d",
              "text": "야시"
            }
          ],
          "correctChoiceId": "ws04q03d",
          "explanation": "'야시'는 경남에서 '여우'를 이르는 말입니다."
        },
        {
          "itemId": "ws04q04",
          "seq": 4,
          "cardId": "ws04c04",
          "prompt": "'토끼'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q04a",
              "text": "야시"
            },
            {
              "choiceId": "ws04q04b",
              "text": "놀갱이"
            },
            {
              "choiceId": "ws04q04c",
              "text": "염생이"
            },
            {
              "choiceId": "ws04q04d",
              "text": "토깨이"
            }
          ],
          "correctChoiceId": "ws04q04d",
          "explanation": "'토깨이'는 경남에서 '토끼'를 이르는 말입니다."
        },
        {
          "itemId": "ws04q05",
          "seq": 5,
          "cardId": "ws04c05",
          "prompt": "'염소'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q05a",
              "text": "강생이"
            },
            {
              "choiceId": "ws04q05b",
              "text": "놀갱이"
            },
            {
              "choiceId": "ws04q05c",
              "text": "염생이"
            },
            {
              "choiceId": "ws04q05d",
              "text": "토깨이"
            }
          ],
          "correctChoiceId": "ws04q05c",
          "explanation": "'염생이'는 경남에서 '염소'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws05",
      "seq": 5,
      "level": 2,
      "category": "움직임과 상태",
      "title": "움직임과 상태 1",
      "cards": [
        {
          "cardId": "ws05c01",
          "standard": "먹다",
          "dialect": "묵다"
        },
        {
          "cardId": "ws05c02",
          "standard": "떨어지다",
          "dialect": "널찌다"
        },
        {
          "cardId": "ws05c03",
          "standard": "다니다",
          "dialect": "댕기다"
        },
        {
          "cardId": "ws05c04",
          "standard": "까뒤집다",
          "dialect": "까디비다"
        },
        {
          "cardId": "ws05c05",
          "standard": "일어나다",
          "dialect": "일나다"
        },
        {
          "cardId": "ws05c06",
          "standard": "들어가다",
          "dialect": "들가다"
        },
        {
          "cardId": "ws05c07",
          "standard": "가르치다",
          "dialect": "갈차주다"
        },
        {
          "cardId": "ws05c08",
          "standard": "때리다",
          "dialect": "쌔리다"
        },
        {
          "cardId": "ws05c09",
          "standard": "문지르다",
          "dialect": "문때다"
        }
      ],
      "items": [
        {
          "itemId": "ws05q01",
          "seq": 1,
          "cardId": "ws05c01",
          "prompt": "'먹다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q01a",
              "text": "옇어라"
            },
            {
              "choiceId": "ws05q01b",
              "text": "묵다"
            },
            {
              "choiceId": "ws05q01c",
              "text": "데피다"
            },
            {
              "choiceId": "ws05q01d",
              "text": "갈차주다"
            }
          ],
          "correctChoiceId": "ws05q01b",
          "explanation": "'묵다'는 경남에서 '먹다'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q02",
          "seq": 2,
          "cardId": "ws05c02",
          "prompt": "'떨어지다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q02a",
              "text": "욕보다"
            },
            {
              "choiceId": "ws05q02b",
              "text": "널찌다"
            },
            {
              "choiceId": "ws05q02c",
              "text": "댕기다"
            },
            {
              "choiceId": "ws05q02d",
              "text": "묵다"
            }
          ],
          "correctChoiceId": "ws05q02b",
          "explanation": "'널찌다'는 경남에서 '떨어지다'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q03",
          "seq": 3,
          "cardId": "ws05c03",
          "prompt": "'다니다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q03a",
              "text": "까디비다"
            },
            {
              "choiceId": "ws05q03b",
              "text": "댕기다"
            },
            {
              "choiceId": "ws05q03c",
              "text": "일나다"
            },
            {
              "choiceId": "ws05q03d",
              "text": "널쭈다"
            }
          ],
          "correctChoiceId": "ws05q03b",
          "explanation": "'댕기다'는 경남에서 '다니다'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q04",
          "seq": 4,
          "cardId": "ws05c04",
          "prompt": "'까뒤집다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q04a",
              "text": "묵다"
            },
            {
              "choiceId": "ws05q04b",
              "text": "까디비다"
            },
            {
              "choiceId": "ws05q04c",
              "text": "일나다"
            },
            {
              "choiceId": "ws05q04d",
              "text": "주아무라"
            }
          ],
          "correctChoiceId": "ws05q04b",
          "explanation": "'까디비다'는 경남에서 '까뒤집다'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q05",
          "seq": 5,
          "cardId": "ws05c05",
          "prompt": "'일어나다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q05a",
              "text": "갈차주다"
            },
            {
              "choiceId": "ws05q05b",
              "text": "들가다"
            },
            {
              "choiceId": "ws05q05c",
              "text": "데빌고 와라"
            },
            {
              "choiceId": "ws05q05d",
              "text": "일나다"
            }
          ],
          "correctChoiceId": "ws05q05d",
          "explanation": "'일나다'는 경남에서 '일어나다'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q06",
          "seq": 6,
          "cardId": "ws05c06",
          "prompt": "'들어가다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q06a",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws05q06b",
              "text": "널쭈다"
            },
            {
              "choiceId": "ws05q06c",
              "text": "데피다"
            },
            {
              "choiceId": "ws05q06d",
              "text": "들가다"
            }
          ],
          "correctChoiceId": "ws05q06d",
          "explanation": "'들가다'는 경남에서 '들어가다'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q07",
          "seq": 7,
          "cardId": "ws05c07",
          "prompt": "'가르치다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q07a",
              "text": "갈차주다"
            },
            {
              "choiceId": "ws05q07b",
              "text": "주아무라"
            },
            {
              "choiceId": "ws05q07c",
              "text": "데빌고 와라"
            },
            {
              "choiceId": "ws05q07d",
              "text": "낑가라"
            }
          ],
          "correctChoiceId": "ws05q07a",
          "explanation": "'갈차주다'는 경남에서 '가르치다'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q08",
          "seq": 8,
          "cardId": "ws05c08",
          "prompt": "'때리다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q08a",
              "text": "쌔리다"
            },
            {
              "choiceId": "ws05q08b",
              "text": "옇어라"
            },
            {
              "choiceId": "ws05q08c",
              "text": "일나다"
            },
            {
              "choiceId": "ws05q08d",
              "text": "꼬매다"
            }
          ],
          "correctChoiceId": "ws05q08a",
          "explanation": "'쌔리다'는 경남에서 '때리다'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q09",
          "seq": 9,
          "cardId": "ws05c09",
          "prompt": "'문지르다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q09a",
              "text": "쌔리다"
            },
            {
              "choiceId": "ws05q09b",
              "text": "문때다"
            },
            {
              "choiceId": "ws05q09c",
              "text": "일나다"
            },
            {
              "choiceId": "ws05q09d",
              "text": "묵다"
            }
          ],
          "correctChoiceId": "ws05q09b",
          "explanation": "'문때다'는 경남에서 '문지르다'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws06",
      "seq": 6,
      "level": 4,
      "category": "움직임과 상태",
      "title": "움직임과 상태 2",
      "cards": [
        {
          "cardId": "ws06c01",
          "standard": "데우다",
          "dialect": "데피다"
        },
        {
          "cardId": "ws06c02",
          "standard": "떨어뜨리다",
          "dialect": "널쭈다"
        },
        {
          "cardId": "ws06c03",
          "standard": "남은 거 다 먹어라",
          "dialect": "주아무라"
        },
        {
          "cardId": "ws06c04",
          "standard": "드러누워 자라",
          "dialect": "디비자라"
        },
        {
          "cardId": "ws06c05",
          "standard": "수고하다",
          "dialect": "욕보다"
        },
        {
          "cardId": "ws06c06",
          "standard": "꿰매다",
          "dialect": "꼬매다"
        },
        {
          "cardId": "ws06c07",
          "standard": "끼워라",
          "dialect": "낑가라"
        },
        {
          "cardId": "ws06c08",
          "standard": "넣어라",
          "dialect": "옇어라"
        },
        {
          "cardId": "ws06c09",
          "standard": "데리고 와라",
          "dialect": "데빌고 와라"
        }
      ],
      "items": [
        {
          "itemId": "ws06q01",
          "seq": 1,
          "cardId": "ws06c01",
          "prompt": "'데우다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q01a",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws06q01b",
              "text": "옇어라"
            },
            {
              "choiceId": "ws06q01c",
              "text": "데피다"
            },
            {
              "choiceId": "ws06q01d",
              "text": "묵다"
            }
          ],
          "correctChoiceId": "ws06q01c",
          "explanation": "'데피다'는 경남에서 '데우다'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q02",
          "seq": 2,
          "cardId": "ws06c02",
          "prompt": "'떨어뜨리다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q02a",
              "text": "디비자라"
            },
            {
              "choiceId": "ws06q02b",
              "text": "일나다"
            },
            {
              "choiceId": "ws06q02c",
              "text": "널쭈다"
            },
            {
              "choiceId": "ws06q02d",
              "text": "주아무라"
            }
          ],
          "correctChoiceId": "ws06q02c",
          "explanation": "'널쭈다'는 경남에서 '떨어뜨리다'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q03",
          "seq": 3,
          "cardId": "ws06c03",
          "prompt": "'남은 거 다 먹어라'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q03a",
              "text": "들가다"
            },
            {
              "choiceId": "ws06q03b",
              "text": "주아무라"
            },
            {
              "choiceId": "ws06q03c",
              "text": "데빌고 와라"
            },
            {
              "choiceId": "ws06q03d",
              "text": "디비자라"
            }
          ],
          "correctChoiceId": "ws06q03b",
          "explanation": "'주아무라'는 경남에서 '남은 거 다 먹어라'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q04",
          "seq": 4,
          "cardId": "ws06c04",
          "prompt": "'드러누워 자라'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q04a",
              "text": "데피다"
            },
            {
              "choiceId": "ws06q04b",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws06q04c",
              "text": "댕기다"
            },
            {
              "choiceId": "ws06q04d",
              "text": "디비자라"
            }
          ],
          "correctChoiceId": "ws06q04d",
          "explanation": "'디비자라'는 경남에서 '드러누워 자라'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q05",
          "seq": 5,
          "cardId": "ws06c05",
          "prompt": "'수고하다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q05a",
              "text": "갈차주다"
            },
            {
              "choiceId": "ws06q05b",
              "text": "댕기다"
            },
            {
              "choiceId": "ws06q05c",
              "text": "욕보다"
            },
            {
              "choiceId": "ws06q05d",
              "text": "데피다"
            }
          ],
          "correctChoiceId": "ws06q05c",
          "explanation": "'욕보다'는 경남에서 '수고하다'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q06",
          "seq": 6,
          "cardId": "ws06c06",
          "prompt": "'꿰매다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q06a",
              "text": "데빌고 와라"
            },
            {
              "choiceId": "ws06q06b",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws06q06c",
              "text": "낑가라"
            },
            {
              "choiceId": "ws06q06d",
              "text": "데피다"
            }
          ],
          "correctChoiceId": "ws06q06b",
          "explanation": "'꼬매다'는 경남에서 '꿰매다'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q07",
          "seq": 7,
          "cardId": "ws06c07",
          "prompt": "'끼워라'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q07a",
              "text": "일나다"
            },
            {
              "choiceId": "ws06q07b",
              "text": "낑가라"
            },
            {
              "choiceId": "ws06q07c",
              "text": "까디비다"
            },
            {
              "choiceId": "ws06q07d",
              "text": "꼬매다"
            }
          ],
          "correctChoiceId": "ws06q07b",
          "explanation": "'낑가라'는 경남에서 '끼워라'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q08",
          "seq": 8,
          "cardId": "ws06c08",
          "prompt": "'넣어라'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q08a",
              "text": "갈차주다"
            },
            {
              "choiceId": "ws06q08b",
              "text": "옇어라"
            },
            {
              "choiceId": "ws06q08c",
              "text": "욕보다"
            },
            {
              "choiceId": "ws06q08d",
              "text": "데피다"
            }
          ],
          "correctChoiceId": "ws06q08b",
          "explanation": "'옇어라'는 경남에서 '넣어라'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q09",
          "seq": 9,
          "cardId": "ws06c09",
          "prompt": "'데리고 와라'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q09a",
              "text": "데빌고 와라"
            },
            {
              "choiceId": "ws06q09b",
              "text": "댕기다"
            },
            {
              "choiceId": "ws06q09c",
              "text": "묵다"
            },
            {
              "choiceId": "ws06q09d",
              "text": "일나다"
            }
          ],
          "correctChoiceId": "ws06q09a",
          "explanation": "'데빌고 와라'는 경남에서 '데리고 와라'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws07",
      "seq": 7,
      "level": 3,
      "category": "성질과 모양",
      "title": "성질과 모양",
      "cards": [
        {
          "cardId": "ws07c01",
          "standard": "따뜻하다",
          "dialect": "따시다"
        },
        {
          "cardId": "ws07c02",
          "standard": "조그맣다",
          "dialect": "쪼맨하다"
        },
        {
          "cardId": "ws07c03",
          "standard": "별로다",
          "dialect": "파이다"
        },
        {
          "cardId": "ws07c04",
          "standard": "최고다",
          "dialect": "대끼리다"
        },
        {
          "cardId": "ws07c05",
          "standard": "야위다",
          "dialect": "애비다"
        },
        {
          "cardId": "ws07c06",
          "standard": "졸리다",
          "dialect": "잠오다"
        },
        {
          "cardId": "ws07c07",
          "standard": "어렵다",
          "dialect": "애럽다"
        }
      ],
      "items": [
        {
          "itemId": "ws07q01",
          "seq": 1,
          "cardId": "ws07c01",
          "prompt": "'따뜻하다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q01a",
              "text": "대끼리다"
            },
            {
              "choiceId": "ws07q01b",
              "text": "애럽다"
            },
            {
              "choiceId": "ws07q01c",
              "text": "따시다"
            },
            {
              "choiceId": "ws07q01d",
              "text": "잠오다"
            }
          ],
          "correctChoiceId": "ws07q01c",
          "explanation": "'따시다'는 경남에서 '따뜻하다'를 이르는 말입니다."
        },
        {
          "itemId": "ws07q02",
          "seq": 2,
          "cardId": "ws07c02",
          "prompt": "'조그맣다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q02a",
              "text": "쪼맨하다"
            },
            {
              "choiceId": "ws07q02b",
              "text": "애럽다"
            },
            {
              "choiceId": "ws07q02c",
              "text": "따시다"
            },
            {
              "choiceId": "ws07q02d",
              "text": "애비다"
            }
          ],
          "correctChoiceId": "ws07q02a",
          "explanation": "'쪼맨하다'는 경남에서 '조그맣다'를 이르는 말입니다."
        },
        {
          "itemId": "ws07q03",
          "seq": 3,
          "cardId": "ws07c03",
          "prompt": "'별로다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q03a",
              "text": "애비다"
            },
            {
              "choiceId": "ws07q03b",
              "text": "따시다"
            },
            {
              "choiceId": "ws07q03c",
              "text": "쪼맨하다"
            },
            {
              "choiceId": "ws07q03d",
              "text": "파이다"
            }
          ],
          "correctChoiceId": "ws07q03d",
          "explanation": "'파이다'는 경남에서 '별로다'를 이르는 말입니다."
        },
        {
          "itemId": "ws07q04",
          "seq": 4,
          "cardId": "ws07c04",
          "prompt": "'최고다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q04a",
              "text": "애럽다"
            },
            {
              "choiceId": "ws07q04b",
              "text": "대끼리다"
            },
            {
              "choiceId": "ws07q04c",
              "text": "따시다"
            },
            {
              "choiceId": "ws07q04d",
              "text": "애비다"
            }
          ],
          "correctChoiceId": "ws07q04b",
          "explanation": "'대끼리다'는 경남에서 '최고다'를 이르는 말입니다."
        },
        {
          "itemId": "ws07q05",
          "seq": 5,
          "cardId": "ws07c05",
          "prompt": "'야위다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q05a",
              "text": "파이다"
            },
            {
              "choiceId": "ws07q05b",
              "text": "애럽다"
            },
            {
              "choiceId": "ws07q05c",
              "text": "쪼맨하다"
            },
            {
              "choiceId": "ws07q05d",
              "text": "애비다"
            }
          ],
          "correctChoiceId": "ws07q05d",
          "explanation": "'애비다'는 경남에서 '야위다'를 이르는 말입니다."
        },
        {
          "itemId": "ws07q06",
          "seq": 6,
          "cardId": "ws07c06",
          "prompt": "'졸리다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q06a",
              "text": "대끼리다"
            },
            {
              "choiceId": "ws07q06b",
              "text": "잠오다"
            },
            {
              "choiceId": "ws07q06c",
              "text": "쪼맨하다"
            },
            {
              "choiceId": "ws07q06d",
              "text": "파이다"
            }
          ],
          "correctChoiceId": "ws07q06b",
          "explanation": "'잠오다'는 경남에서 '졸리다'를 이르는 말입니다."
        },
        {
          "itemId": "ws07q07",
          "seq": 7,
          "cardId": "ws07c07",
          "prompt": "'어렵다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q07a",
              "text": "애럽다"
            },
            {
              "choiceId": "ws07q07b",
              "text": "잠오다"
            },
            {
              "choiceId": "ws07q07c",
              "text": "파이다"
            },
            {
              "choiceId": "ws07q07d",
              "text": "대끼리다"
            }
          ],
          "correctChoiceId": "ws07q07a",
          "explanation": "'애럽다'는 경남에서 '어렵다'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws08",
      "seq": 8,
      "level": 2,
      "category": "정도와 때",
      "title": "정도와 때 1",
      "cards": [
        {
          "cardId": "ws08c01",
          "standard": "엄청",
          "dialect": "억수로"
        },
        {
          "cardId": "ws08c02",
          "standard": "많이",
          "dialect": "마이"
        },
        {
          "cardId": "ws08c03",
          "standard": "조금",
          "dialect": "쪼매"
        },
        {
          "cardId": "ws08c04",
          "standard": "날마다",
          "dialect": "만날천날"
        },
        {
          "cardId": "ws08c05",
          "standard": "단단히",
          "dialect": "단디"
        },
        {
          "cardId": "ws08c06",
          "standard": "그다지",
          "dialect": "짜다시리"
        },
        {
          "cardId": "ws08c07",
          "standard": "엄청 많더라",
          "dialect": "천지빼까리더라"
        }
      ],
      "items": [
        {
          "itemId": "ws08q01",
          "seq": 1,
          "cardId": "ws08c01",
          "prompt": "'엄청'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q01a",
              "text": "깔롱"
            },
            {
              "choiceId": "ws08q01b",
              "text": "똑띠"
            },
            {
              "choiceId": "ws08q01c",
              "text": "내나"
            },
            {
              "choiceId": "ws08q01d",
              "text": "억수로"
            }
          ],
          "correctChoiceId": "ws08q01d",
          "explanation": "'억수로'는 경남에서 '엄청'을 이르는 말입니다."
        },
        {
          "itemId": "ws08q02",
          "seq": 2,
          "cardId": "ws08c02",
          "prompt": "'많이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q02a",
              "text": "내나"
            },
            {
              "choiceId": "ws08q02b",
              "text": "만날천날"
            },
            {
              "choiceId": "ws08q02c",
              "text": "깔롱"
            },
            {
              "choiceId": "ws08q02d",
              "text": "마이"
            }
          ],
          "correctChoiceId": "ws08q02d",
          "explanation": "'마이'는 경남에서 '많이'를 이르는 말입니다."
        },
        {
          "itemId": "ws08q03",
          "seq": 3,
          "cardId": "ws08c03",
          "prompt": "'조금'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q03a",
              "text": "쪼매"
            },
            {
              "choiceId": "ws08q03b",
              "text": "마이"
            },
            {
              "choiceId": "ws08q03c",
              "text": "짜다시리"
            },
            {
              "choiceId": "ws08q03d",
              "text": "오만때만"
            }
          ],
          "correctChoiceId": "ws08q03a",
          "explanation": "'쪼매'는 경남에서 '조금'을 이르는 말입니다."
        },
        {
          "itemId": "ws08q04",
          "seq": 4,
          "cardId": "ws08c04",
          "prompt": "'날마다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q04a",
              "text": "엥가이"
            },
            {
              "choiceId": "ws08q04b",
              "text": "쪼매"
            },
            {
              "choiceId": "ws08q04c",
              "text": "깔롱"
            },
            {
              "choiceId": "ws08q04d",
              "text": "만날천날"
            }
          ],
          "correctChoiceId": "ws08q04d",
          "explanation": "'만날천날'은 경남에서 '날마다'를 이르는 말입니다."
        },
        {
          "itemId": "ws08q05",
          "seq": 5,
          "cardId": "ws08c05",
          "prompt": "'단단히'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q05a",
              "text": "단디"
            },
            {
              "choiceId": "ws08q05b",
              "text": "오만때만"
            },
            {
              "choiceId": "ws08q05c",
              "text": "온전신에"
            },
            {
              "choiceId": "ws08q05d",
              "text": "깔롱"
            }
          ],
          "correctChoiceId": "ws08q05a",
          "explanation": "'단디'는 경남에서 '단단히'를 이르는 말입니다."
        },
        {
          "itemId": "ws08q06",
          "seq": 6,
          "cardId": "ws08c06",
          "prompt": "'그다지'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q06a",
              "text": "내나"
            },
            {
              "choiceId": "ws08q06b",
              "text": "단디"
            },
            {
              "choiceId": "ws08q06c",
              "text": "짜다시리"
            },
            {
              "choiceId": "ws08q06d",
              "text": "오만때만"
            }
          ],
          "correctChoiceId": "ws08q06c",
          "explanation": "'짜다시리'는 경남에서 '그다지'를 이르는 말입니다."
        },
        {
          "itemId": "ws08q07",
          "seq": 7,
          "cardId": "ws08c07",
          "prompt": "'엄청 많더라'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q07a",
              "text": "내나"
            },
            {
              "choiceId": "ws08q07b",
              "text": "온전신에"
            },
            {
              "choiceId": "ws08q07c",
              "text": "오만때만"
            },
            {
              "choiceId": "ws08q07d",
              "text": "천지빼까리더라"
            }
          ],
          "correctChoiceId": "ws08q07d",
          "explanation": "'천지빼까리더라'는 경남에서 '엄청 많더라'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws09",
      "seq": 9,
      "level": 3,
      "category": "정도와 때",
      "title": "정도와 때 2",
      "cards": [
        {
          "cardId": "ws09c01",
          "standard": "온통",
          "dialect": "온전신에"
        },
        {
          "cardId": "ws09c02",
          "standard": "여기저기",
          "dialect": "오만때만"
        },
        {
          "cardId": "ws09c03",
          "standard": "마찬가지로",
          "dialect": "내나"
        },
        {
          "cardId": "ws09c04",
          "standard": "얼른",
          "dialect": "퍼뜩"
        },
        {
          "cardId": "ws09c05",
          "standard": "어지간히",
          "dialect": "엥가이"
        },
        {
          "cardId": "ws09c06",
          "standard": "똑바로",
          "dialect": "똑띠"
        },
        {
          "cardId": "ws09c07",
          "standard": "멋 부림",
          "dialect": "깔롱"
        }
      ],
      "items": [
        {
          "itemId": "ws09q01",
          "seq": 1,
          "cardId": "ws09c01",
          "prompt": "'온통'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws09q01a",
              "text": "단디"
            },
            {
              "choiceId": "ws09q01b",
              "text": "온전신에"
            },
            {
              "choiceId": "ws09q01c",
              "text": "쪼매"
            },
            {
              "choiceId": "ws09q01d",
              "text": "깔롱"
            }
          ],
          "correctChoiceId": "ws09q01b",
          "explanation": "'온전신에'는 경남에서 '온통'을 이르는 말입니다."
        },
        {
          "itemId": "ws09q02",
          "seq": 2,
          "cardId": "ws09c02",
          "prompt": "'여기저기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws09q02a",
              "text": "오만때만"
            },
            {
              "choiceId": "ws09q02b",
              "text": "퍼뜩"
            },
            {
              "choiceId": "ws09q02c",
              "text": "내나"
            },
            {
              "choiceId": "ws09q02d",
              "text": "만날천날"
            }
          ],
          "correctChoiceId": "ws09q02a",
          "explanation": "'오만때만'은 경남에서 '여기저기'를 이르는 말입니다."
        },
        {
          "itemId": "ws09q03",
          "seq": 3,
          "cardId": "ws09c03",
          "prompt": "'마찬가지로'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws09q03a",
              "text": "온전신에"
            },
            {
              "choiceId": "ws09q03b",
              "text": "내나"
            },
            {
              "choiceId": "ws09q03c",
              "text": "퍼뜩"
            },
            {
              "choiceId": "ws09q03d",
              "text": "쪼매"
            }
          ],
          "correctChoiceId": "ws09q03b",
          "explanation": "'내나'는 경남에서 '마찬가지로'를 이르는 말입니다."
        },
        {
          "itemId": "ws09q04",
          "seq": 4,
          "cardId": "ws09c04",
          "prompt": "'얼른'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws09q04a",
              "text": "퍼뜩"
            },
            {
              "choiceId": "ws09q04b",
              "text": "만날천날"
            },
            {
              "choiceId": "ws09q04c",
              "text": "깔롱"
            },
            {
              "choiceId": "ws09q04d",
              "text": "억수로"
            }
          ],
          "correctChoiceId": "ws09q04a",
          "explanation": "'퍼뜩'은 경남에서 '얼른'을 이르는 말입니다."
        },
        {
          "itemId": "ws09q05",
          "seq": 5,
          "cardId": "ws09c05",
          "prompt": "'어지간히'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws09q05a",
              "text": "엥가이"
            },
            {
              "choiceId": "ws09q05b",
              "text": "천지빼까리더라"
            },
            {
              "choiceId": "ws09q05c",
              "text": "단디"
            },
            {
              "choiceId": "ws09q05d",
              "text": "억수로"
            }
          ],
          "correctChoiceId": "ws09q05a",
          "explanation": "'엥가이'는 경남에서 '어지간히'를 이르는 말입니다."
        },
        {
          "itemId": "ws09q06",
          "seq": 6,
          "cardId": "ws09c06",
          "prompt": "'똑바로'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws09q06a",
              "text": "똑띠"
            },
            {
              "choiceId": "ws09q06b",
              "text": "억수로"
            },
            {
              "choiceId": "ws09q06c",
              "text": "천지빼까리더라"
            },
            {
              "choiceId": "ws09q06d",
              "text": "온전신에"
            }
          ],
          "correctChoiceId": "ws09q06a",
          "explanation": "'똑띠'는 경남에서 '똑바로'를 이르는 말입니다."
        },
        {
          "itemId": "ws09q07",
          "seq": 7,
          "cardId": "ws09c07",
          "prompt": "'멋 부림'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws09q07a",
              "text": "억수로"
            },
            {
              "choiceId": "ws09q07b",
              "text": "퍼뜩"
            },
            {
              "choiceId": "ws09q07c",
              "text": "만날천날"
            },
            {
              "choiceId": "ws09q07d",
              "text": "깔롱"
            }
          ],
          "correctChoiceId": "ws09q07d",
          "explanation": "'깔롱'은 경남에서 '멋 부림'을 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws10",
      "seq": 10,
      "level": 1,
      "category": "묻고 답하는 말",
      "title": "묻고 답하는 말 1",
      "cards": [
        {
          "cardId": "ws10c01",
          "standard": "무슨",
          "dialect": "무신"
        },
        {
          "cardId": "ws10c02",
          "standard": "어디",
          "dialect": "어데"
        },
        {
          "cardId": "ws10c03",
          "standard": "어찌",
          "dialect": "우예"
        },
        {
          "cardId": "ws10c04",
          "standard": "어떻게든",
          "dialect": "우야든동"
        },
        {
          "cardId": "ws10c05",
          "standard": "그러면",
          "dialect": "그라모"
        },
        {
          "cardId": "ws10c06",
          "standard": "그만",
          "dialect": "고마"
        }
      ],
      "items": [
        {
          "itemId": "ws10q01",
          "seq": 1,
          "cardId": "ws10c01",
          "prompt": "'무슨'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q01a",
              "text": "무신"
            },
            {
              "choiceId": "ws10q01b",
              "text": "아인교"
            },
            {
              "choiceId": "ws10q01c",
              "text": "마"
            },
            {
              "choiceId": "ws10q01d",
              "text": "와"
            }
          ],
          "correctChoiceId": "ws10q01a",
          "explanation": "'무신'은 경남에서 '무슨'을 이르는 말입니다."
        },
        {
          "itemId": "ws10q02",
          "seq": 2,
          "cardId": "ws10c02",
          "prompt": "'어디'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q02a",
              "text": "어데"
            },
            {
              "choiceId": "ws10q02b",
              "text": "마"
            },
            {
              "choiceId": "ws10q02c",
              "text": "오야"
            },
            {
              "choiceId": "ws10q02d",
              "text": "그라모"
            }
          ],
          "correctChoiceId": "ws10q02a",
          "explanation": "'어데'는 경남에서 '어디'를 이르는 말입니다."
        },
        {
          "itemId": "ws10q03",
          "seq": 3,
          "cardId": "ws10c03",
          "prompt": "'어찌'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q03a",
              "text": "와"
            },
            {
              "choiceId": "ws10q03b",
              "text": "우예"
            },
            {
              "choiceId": "ws10q03c",
              "text": "고마"
            },
            {
              "choiceId": "ws10q03d",
              "text": "마"
            }
          ],
          "correctChoiceId": "ws10q03b",
          "explanation": "'우예'는 경남에서 '어찌'를 이르는 말입니다."
        },
        {
          "itemId": "ws10q04",
          "seq": 4,
          "cardId": "ws10c04",
          "prompt": "'어떻게든'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q04a",
              "text": "우야든동"
            },
            {
              "choiceId": "ws10q04b",
              "text": "하모"
            },
            {
              "choiceId": "ws10q04c",
              "text": "아인교"
            },
            {
              "choiceId": "ws10q04d",
              "text": "와"
            }
          ],
          "correctChoiceId": "ws10q04a",
          "explanation": "'우야든동'은 경남에서 '어떻게든'을 이르는 말입니다."
        },
        {
          "itemId": "ws10q05",
          "seq": 5,
          "cardId": "ws10c05",
          "prompt": "'그러면'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q05a",
              "text": "무신"
            },
            {
              "choiceId": "ws10q05b",
              "text": "그라모"
            },
            {
              "choiceId": "ws10q05c",
              "text": "우예"
            },
            {
              "choiceId": "ws10q05d",
              "text": "하모"
            }
          ],
          "correctChoiceId": "ws10q05b",
          "explanation": "'그라모'는 경남에서 '그러면'을 이르는 말입니다."
        },
        {
          "itemId": "ws10q06",
          "seq": 6,
          "cardId": "ws10c06",
          "prompt": "'그만'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q06a",
              "text": "마"
            },
            {
              "choiceId": "ws10q06b",
              "text": "우예"
            },
            {
              "choiceId": "ws10q06c",
              "text": "와"
            },
            {
              "choiceId": "ws10q06d",
              "text": "고마"
            }
          ],
          "correctChoiceId": "ws10q06d",
          "explanation": "'고마'는 경남에서 '그만'을 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws11",
      "seq": 11,
      "level": 3,
      "category": "묻고 답하는 말",
      "title": "묻고 답하는 말 2",
      "cards": [
        {
          "cardId": "ws11c01",
          "standard": "왜",
          "dialect": "와"
        },
        {
          "cardId": "ws11c02",
          "standard": "아무렴",
          "dialect": "하모"
        },
        {
          "cardId": "ws11c03",
          "standard": "아닙니까",
          "dialect": "아인교"
        },
        {
          "cardId": "ws11c04",
          "standard": "야",
          "dialect": "마"
        },
        {
          "cardId": "ws11c05",
          "standard": "그래",
          "dialect": "오야"
        }
      ],
      "items": [
        {
          "itemId": "ws11q01",
          "seq": 1,
          "cardId": "ws11c01",
          "prompt": "'왜'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q01a",
              "text": "우예"
            },
            {
              "choiceId": "ws11q01b",
              "text": "오야"
            },
            {
              "choiceId": "ws11q01c",
              "text": "와"
            },
            {
              "choiceId": "ws11q01d",
              "text": "하모"
            }
          ],
          "correctChoiceId": "ws11q01c",
          "explanation": "'와'는 경남에서 '왜'를 이르는 말입니다."
        },
        {
          "itemId": "ws11q02",
          "seq": 2,
          "cardId": "ws11c02",
          "prompt": "'아무렴'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q02a",
              "text": "고마"
            },
            {
              "choiceId": "ws11q02b",
              "text": "그라모"
            },
            {
              "choiceId": "ws11q02c",
              "text": "우예"
            },
            {
              "choiceId": "ws11q02d",
              "text": "하모"
            }
          ],
          "correctChoiceId": "ws11q02d",
          "explanation": "'하모'는 경남에서 '아무렴'을 이르는 말입니다."
        },
        {
          "itemId": "ws11q03",
          "seq": 3,
          "cardId": "ws11c03",
          "prompt": "'아닙니까'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q03a",
              "text": "마"
            },
            {
              "choiceId": "ws11q03b",
              "text": "아인교"
            },
            {
              "choiceId": "ws11q03c",
              "text": "우야든동"
            },
            {
              "choiceId": "ws11q03d",
              "text": "하모"
            }
          ],
          "correctChoiceId": "ws11q03b",
          "explanation": "'아인교'는 경남에서 '아닙니까'를 이르는 말입니다."
        },
        {
          "itemId": "ws11q04",
          "seq": 4,
          "cardId": "ws11c04",
          "prompt": "'야'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q04a",
              "text": "와"
            },
            {
              "choiceId": "ws11q04b",
              "text": "우야든동"
            },
            {
              "choiceId": "ws11q04c",
              "text": "마"
            },
            {
              "choiceId": "ws11q04d",
              "text": "고마"
            }
          ],
          "correctChoiceId": "ws11q04c",
          "explanation": "'마'는 경남에서 '야'를 이르는 말입니다."
        },
        {
          "itemId": "ws11q05",
          "seq": 5,
          "cardId": "ws11c05",
          "prompt": "'그래'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q05a",
              "text": "오야"
            },
            {
              "choiceId": "ws11q05b",
              "text": "하모"
            },
            {
              "choiceId": "ws11q05c",
              "text": "그라모"
            },
            {
              "choiceId": "ws11q05d",
              "text": "무신"
            }
          ],
          "correctChoiceId": "ws11q05a",
          "explanation": "'오야'는 경남에서 '그래'를 이르는 말입니다."
        }
      ]
    }
  ]
}$definition$,
        timestamp with time zone '2026-10-07T00:00:00Z');
