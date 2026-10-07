-- KAN-265: 단어 학습 발행본 wd-gn-2026.10.1 - 어휘 세트 20개, 카드 145장 (레벨 1 5개, 레벨 2 3개, 레벨 3 3개, 레벨 4 3개, 레벨 5 6개).
--
-- 이 파일은 손으로 쓰지 않는다 - tools/content/build_word_learning.py가 레벨테스트 어휘 풀
-- (vocabulary_gn.py)에서 만든다. 세트는 분류별(10카드 상한), 레벨은 낱말 근거의 평균, 문항은
-- 표준어 → 사투리 한 방향이고 오답은 같은 분류에서 contentVersion을 시드로 뽑는다 (명세서 §3.16).
--
-- 발행 후 불변이다 (§5.4와 같은 규칙). 해설이나 세트 구성이 바뀌면 이 행을 UPDATE하지 않고
-- 새 contentVersion으로 INSERT한다 - 진행 중인 시도가 자기 버전의 문항을 계속 봐야 한다.
-- 활성 전환 행은 없다 - 서버가 발행 시각이 가장 늦은 발행본을 목록과 상세에 쓴다.
insert into word_learning_definition (content_version, dialect, body, published_at)
values ('wd-gn-2026.10.1', 'GYEONGNAM', $definition${
  "contentVersion": "wd-gn-2026.10.1",
  "dialect": "GYEONGNAM",
  "sets": [
    {
      "setId": "ws01",
      "seq": 1,
      "level": 1,
      "category": "음식과 식재료",
      "title": "음식과 식재료 1",
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
          "standard": "옥수수",
          "dialect": "옥수갱이"
        },
        {
          "cardId": "ws01c06",
          "standard": "상어고기",
          "dialect": "돔배기"
        },
        {
          "cardId": "ws01c07",
          "standard": "고기",
          "dialect": "게기"
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
              "text": "찌짐"
            },
            {
              "choiceId": "ws01q01b",
              "text": "꼬장"
            },
            {
              "choiceId": "ws01q01c",
              "text": "너물"
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
              "text": "너물"
            },
            {
              "choiceId": "ws01q02b",
              "text": "밀가리"
            },
            {
              "choiceId": "ws01q02c",
              "text": "찌짐"
            },
            {
              "choiceId": "ws01q02d",
              "text": "정구지"
            }
          ],
          "correctChoiceId": "ws01q02c",
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
              "text": "덴장"
            },
            {
              "choiceId": "ws01q03b",
              "text": "찌짐"
            },
            {
              "choiceId": "ws01q03c",
              "text": "짐치"
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
              "text": "문에"
            },
            {
              "choiceId": "ws01q04b",
              "text": "나락"
            },
            {
              "choiceId": "ws01q04c",
              "text": "너물"
            },
            {
              "choiceId": "ws01q04d",
              "text": "게기"
            }
          ],
          "correctChoiceId": "ws01q04b",
          "explanation": "'나락'은 경남에서 '벼'를 이르는 말입니다."
        },
        {
          "itemId": "ws01q05",
          "seq": 5,
          "cardId": "ws01c05",
          "prompt": "'옥수수'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws01q05a",
              "text": "옥수갱이"
            },
            {
              "choiceId": "ws01q05b",
              "text": "꼬장"
            },
            {
              "choiceId": "ws01q05c",
              "text": "기렁장"
            },
            {
              "choiceId": "ws01q05d",
              "text": "정구지"
            }
          ],
          "correctChoiceId": "ws01q05a",
          "explanation": "'옥수갱이'는 경남에서 '옥수수'를 이르는 말입니다."
        },
        {
          "itemId": "ws01q06",
          "seq": 6,
          "cardId": "ws01c06",
          "prompt": "'상어고기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws01q06a",
              "text": "미르치"
            },
            {
              "choiceId": "ws01q06b",
              "text": "나새이"
            },
            {
              "choiceId": "ws01q06c",
              "text": "문에"
            },
            {
              "choiceId": "ws01q06d",
              "text": "돔배기"
            }
          ],
          "correctChoiceId": "ws01q06d",
          "explanation": "'돔배기'는 경남에서 '상어고기'를 이르는 말입니다."
        },
        {
          "itemId": "ws01q07",
          "seq": 7,
          "cardId": "ws01c07",
          "prompt": "'고기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws01q07a",
              "text": "꼬장"
            },
            {
              "choiceId": "ws01q07b",
              "text": "게기"
            },
            {
              "choiceId": "ws01q07c",
              "text": "국시"
            },
            {
              "choiceId": "ws01q07d",
              "text": "나새이"
            }
          ],
          "correctChoiceId": "ws01q07b",
          "explanation": "'게기'는 경남에서 '고기'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws02",
      "seq": 2,
      "level": 2,
      "category": "음식과 식재료",
      "title": "음식과 식재료 2",
      "cards": [
        {
          "cardId": "ws02c01",
          "standard": "가마솥",
          "dialect": "가매솥"
        },
        {
          "cardId": "ws02c02",
          "standard": "간장",
          "dialect": "기렁장"
        },
        {
          "cardId": "ws02c03",
          "standard": "김치",
          "dialect": "짐치"
        },
        {
          "cardId": "ws02c04",
          "standard": "무",
          "dialect": "무시"
        },
        {
          "cardId": "ws02c05",
          "standard": "고추장",
          "dialect": "꼬장"
        },
        {
          "cardId": "ws02c06",
          "standard": "소금",
          "dialect": "소곰"
        },
        {
          "cardId": "ws02c07",
          "standard": "멸치",
          "dialect": "미르치"
        }
      ],
      "items": [
        {
          "itemId": "ws02q01",
          "seq": 1,
          "cardId": "ws02c01",
          "prompt": "'가마솥'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws02q01a",
              "text": "미르치"
            },
            {
              "choiceId": "ws02q01b",
              "text": "나락"
            },
            {
              "choiceId": "ws02q01c",
              "text": "돔배기"
            },
            {
              "choiceId": "ws02q01d",
              "text": "가매솥"
            }
          ],
          "correctChoiceId": "ws02q01d",
          "explanation": "'가매솥'은 경남에서 '가마솥'을 이르는 말입니다."
        },
        {
          "itemId": "ws02q02",
          "seq": 2,
          "cardId": "ws02c02",
          "prompt": "'간장'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws02q02a",
              "text": "무시"
            },
            {
              "choiceId": "ws02q02b",
              "text": "기렁장"
            },
            {
              "choiceId": "ws02q02c",
              "text": "게기"
            },
            {
              "choiceId": "ws02q02d",
              "text": "소곰"
            }
          ],
          "correctChoiceId": "ws02q02b",
          "explanation": "'기렁장'은 경남에서 '간장'을 이르는 말입니다."
        },
        {
          "itemId": "ws02q03",
          "seq": 3,
          "cardId": "ws02c03",
          "prompt": "'김치'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws02q03a",
              "text": "밀가리"
            },
            {
              "choiceId": "ws02q03b",
              "text": "옥수갱이"
            },
            {
              "choiceId": "ws02q03c",
              "text": "짐치"
            },
            {
              "choiceId": "ws02q03d",
              "text": "미르치"
            }
          ],
          "correctChoiceId": "ws02q03c",
          "explanation": "'짐치'는 경남에서 '김치'를 이르는 말입니다."
        },
        {
          "itemId": "ws02q04",
          "seq": 4,
          "cardId": "ws02c04",
          "prompt": "'무'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws02q04a",
              "text": "나락"
            },
            {
              "choiceId": "ws02q04b",
              "text": "무시"
            },
            {
              "choiceId": "ws02q04c",
              "text": "기렁장"
            },
            {
              "choiceId": "ws02q04d",
              "text": "정구지"
            }
          ],
          "correctChoiceId": "ws02q04b",
          "explanation": "'무시'는 경남에서 '무'를 이르는 말입니다."
        },
        {
          "itemId": "ws02q05",
          "seq": 5,
          "cardId": "ws02c05",
          "prompt": "'고추장'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws02q05a",
              "text": "꼬장"
            },
            {
              "choiceId": "ws02q05b",
              "text": "국시"
            },
            {
              "choiceId": "ws02q05c",
              "text": "소곰"
            },
            {
              "choiceId": "ws02q05d",
              "text": "수꾸"
            }
          ],
          "correctChoiceId": "ws02q05a",
          "explanation": "'꼬장'은 경남에서 '고추장'을 이르는 말입니다."
        },
        {
          "itemId": "ws02q06",
          "seq": 6,
          "cardId": "ws02c06",
          "prompt": "'소금'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws02q06a",
              "text": "게기"
            },
            {
              "choiceId": "ws02q06b",
              "text": "덴장"
            },
            {
              "choiceId": "ws02q06c",
              "text": "소곰"
            },
            {
              "choiceId": "ws02q06d",
              "text": "찌짐"
            }
          ],
          "correctChoiceId": "ws02q06c",
          "explanation": "'소곰'은 경남에서 '소금'을 이르는 말입니다."
        },
        {
          "itemId": "ws02q07",
          "seq": 7,
          "cardId": "ws02c07",
          "prompt": "'멸치'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws02q07a",
              "text": "소곰"
            },
            {
              "choiceId": "ws02q07b",
              "text": "찌짐"
            },
            {
              "choiceId": "ws02q07c",
              "text": "미르치"
            },
            {
              "choiceId": "ws02q07d",
              "text": "가매솥"
            }
          ],
          "correctChoiceId": "ws02q07c",
          "explanation": "'미르치'는 경남에서 '멸치'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws03",
      "seq": 3,
      "level": 5,
      "category": "음식과 식재료",
      "title": "음식과 식재료 3",
      "cards": [
        {
          "cardId": "ws03c01",
          "standard": "밀가루",
          "dialect": "밀가리"
        },
        {
          "cardId": "ws03c02",
          "standard": "냉이",
          "dialect": "나새이"
        },
        {
          "cardId": "ws03c03",
          "standard": "도라지",
          "dialect": "돌가지"
        },
        {
          "cardId": "ws03c04",
          "standard": "된장",
          "dialect": "덴장"
        },
        {
          "cardId": "ws03c05",
          "standard": "나물",
          "dialect": "너물"
        },
        {
          "cardId": "ws03c06",
          "standard": "수수",
          "dialect": "수꾸"
        },
        {
          "cardId": "ws03c07",
          "standard": "문어",
          "dialect": "문에"
        }
      ],
      "items": [
        {
          "itemId": "ws03q01",
          "seq": 1,
          "cardId": "ws03c01",
          "prompt": "'밀가루'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws03q01a",
              "text": "너물"
            },
            {
              "choiceId": "ws03q01b",
              "text": "수꾸"
            },
            {
              "choiceId": "ws03q01c",
              "text": "밀가리"
            },
            {
              "choiceId": "ws03q01d",
              "text": "무시"
            }
          ],
          "correctChoiceId": "ws03q01c",
          "explanation": "'밀가리'는 경남에서 '밀가루'를 이르는 말입니다."
        },
        {
          "itemId": "ws03q02",
          "seq": 2,
          "cardId": "ws03c02",
          "prompt": "'냉이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws03q02a",
              "text": "찌짐"
            },
            {
              "choiceId": "ws03q02b",
              "text": "가매솥"
            },
            {
              "choiceId": "ws03q02c",
              "text": "나새이"
            },
            {
              "choiceId": "ws03q02d",
              "text": "소곰"
            }
          ],
          "correctChoiceId": "ws03q02c",
          "explanation": "'나새이'는 경남에서 '냉이'를 이르는 말입니다."
        },
        {
          "itemId": "ws03q03",
          "seq": 3,
          "cardId": "ws03c03",
          "prompt": "'도라지'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws03q03a",
              "text": "기렁장"
            },
            {
              "choiceId": "ws03q03b",
              "text": "찌짐"
            },
            {
              "choiceId": "ws03q03c",
              "text": "짐치"
            },
            {
              "choiceId": "ws03q03d",
              "text": "돌가지"
            }
          ],
          "correctChoiceId": "ws03q03d",
          "explanation": "'돌가지'는 경남에서 '도라지'를 이르는 말입니다."
        },
        {
          "itemId": "ws03q04",
          "seq": 4,
          "cardId": "ws03c04",
          "prompt": "'된장'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws03q04a",
              "text": "문에"
            },
            {
              "choiceId": "ws03q04b",
              "text": "덴장"
            },
            {
              "choiceId": "ws03q04c",
              "text": "짐치"
            },
            {
              "choiceId": "ws03q04d",
              "text": "수꾸"
            }
          ],
          "correctChoiceId": "ws03q04b",
          "explanation": "'덴장'은 경남에서 '된장'을 이르는 말입니다."
        },
        {
          "itemId": "ws03q05",
          "seq": 5,
          "cardId": "ws03c05",
          "prompt": "'나물'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws03q05a",
              "text": "찌짐"
            },
            {
              "choiceId": "ws03q05b",
              "text": "너물"
            },
            {
              "choiceId": "ws03q05c",
              "text": "나새이"
            },
            {
              "choiceId": "ws03q05d",
              "text": "무시"
            }
          ],
          "correctChoiceId": "ws03q05b",
          "explanation": "'너물'은 경남에서 '나물'을 이르는 말입니다."
        },
        {
          "itemId": "ws03q06",
          "seq": 6,
          "cardId": "ws03c06",
          "prompt": "'수수'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws03q06a",
              "text": "나새이"
            },
            {
              "choiceId": "ws03q06b",
              "text": "수꾸"
            },
            {
              "choiceId": "ws03q06c",
              "text": "문에"
            },
            {
              "choiceId": "ws03q06d",
              "text": "찌짐"
            }
          ],
          "correctChoiceId": "ws03q06b",
          "explanation": "'수꾸'는 경남에서 '수수'를 이르는 말입니다."
        },
        {
          "itemId": "ws03q07",
          "seq": 7,
          "cardId": "ws03c07",
          "prompt": "'문어'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws03q07a",
              "text": "꼬장"
            },
            {
              "choiceId": "ws03q07b",
              "text": "나락"
            },
            {
              "choiceId": "ws03q07c",
              "text": "돔배기"
            },
            {
              "choiceId": "ws03q07d",
              "text": "문에"
            }
          ],
          "correctChoiceId": "ws03q07d",
          "explanation": "'문에'는 경남에서 '문어'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws04",
      "seq": 4,
      "level": 3,
      "category": "집과 살림",
      "title": "집과 살림 1",
      "cards": [
        {
          "cardId": "ws04c01",
          "standard": "부엌",
          "dialect": "정지"
        },
        {
          "cardId": "ws04c02",
          "standard": "들깨",
          "dialect": "두리깨"
        },
        {
          "cardId": "ws04c03",
          "standard": "장독대",
          "dialect": "장구방"
        },
        {
          "cardId": "ws04c04",
          "standard": "소쿠리",
          "dialect": "소구리"
        },
        {
          "cardId": "ws04c05",
          "standard": "우물",
          "dialect": "새미"
        },
        {
          "cardId": "ws04c06",
          "standard": "그릇",
          "dialect": "그륵"
        },
        {
          "cardId": "ws04c07",
          "standard": "젓가락",
          "dialect": "저까치"
        },
        {
          "cardId": "ws04c08",
          "standard": "이야기",
          "dialect": "이바구"
        },
        {
          "cardId": "ws04c09",
          "standard": "마루",
          "dialect": "마리"
        },
        {
          "cardId": "ws04c10",
          "standard": "절구",
          "dialect": "도구통"
        }
      ],
      "items": [
        {
          "itemId": "ws04q01",
          "seq": 1,
          "cardId": "ws04c01",
          "prompt": "'부엌'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q01a",
              "text": "도구통"
            },
            {
              "choiceId": "ws04q01b",
              "text": "사분"
            },
            {
              "choiceId": "ws04q01c",
              "text": "저까치"
            },
            {
              "choiceId": "ws04q01d",
              "text": "정지"
            }
          ],
          "correctChoiceId": "ws04q01d",
          "explanation": "'정지'는 경남에서 '부엌'을 이르는 말입니다."
        },
        {
          "itemId": "ws04q02",
          "seq": 2,
          "cardId": "ws04c02",
          "prompt": "'들깨'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q02a",
              "text": "비개"
            },
            {
              "choiceId": "ws04q02b",
              "text": "정지"
            },
            {
              "choiceId": "ws04q02c",
              "text": "대리미"
            },
            {
              "choiceId": "ws04q02d",
              "text": "두리깨"
            }
          ],
          "correctChoiceId": "ws04q02d",
          "explanation": "'두리깨'는 경남에서 '들깨'를 이르는 말입니다."
        },
        {
          "itemId": "ws04q03",
          "seq": 3,
          "cardId": "ws04c03",
          "prompt": "'장독대'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q03a",
              "text": "대리미"
            },
            {
              "choiceId": "ws04q03b",
              "text": "동우"
            },
            {
              "choiceId": "ws04q03c",
              "text": "주개"
            },
            {
              "choiceId": "ws04q03d",
              "text": "장구방"
            }
          ],
          "correctChoiceId": "ws04q03d",
          "explanation": "'장구방'은 경남에서 '장독대'를 이르는 말입니다."
        },
        {
          "itemId": "ws04q04",
          "seq": 4,
          "cardId": "ws04c04",
          "prompt": "'소쿠리'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q04a",
              "text": "홀깨"
            },
            {
              "choiceId": "ws04q04b",
              "text": "대리미"
            },
            {
              "choiceId": "ws04q04c",
              "text": "구죽"
            },
            {
              "choiceId": "ws04q04d",
              "text": "소구리"
            }
          ],
          "correctChoiceId": "ws04q04d",
          "explanation": "'소구리'는 경남에서 '소쿠리'를 이르는 말입니다."
        },
        {
          "itemId": "ws04q05",
          "seq": 5,
          "cardId": "ws04c05",
          "prompt": "'우물'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q05a",
              "text": "도구통"
            },
            {
              "choiceId": "ws04q05b",
              "text": "이바구"
            },
            {
              "choiceId": "ws04q05c",
              "text": "부지깨이"
            },
            {
              "choiceId": "ws04q05d",
              "text": "새미"
            }
          ],
          "correctChoiceId": "ws04q05d",
          "explanation": "'새미'는 경남에서 '우물'을 이르는 말입니다."
        },
        {
          "itemId": "ws04q06",
          "seq": 6,
          "cardId": "ws04c06",
          "prompt": "'그릇'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q06a",
              "text": "산태미"
            },
            {
              "choiceId": "ws04q06b",
              "text": "부지깨이"
            },
            {
              "choiceId": "ws04q06c",
              "text": "대리미"
            },
            {
              "choiceId": "ws04q06d",
              "text": "그륵"
            }
          ],
          "correctChoiceId": "ws04q06d",
          "explanation": "'그륵'은 경남에서 '그릇'을 이르는 말입니다."
        },
        {
          "itemId": "ws04q07",
          "seq": 7,
          "cardId": "ws04c07",
          "prompt": "'젓가락'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q07a",
              "text": "저까치"
            },
            {
              "choiceId": "ws04q07b",
              "text": "오강"
            },
            {
              "choiceId": "ws04q07c",
              "text": "홀깨"
            },
            {
              "choiceId": "ws04q07d",
              "text": "동우"
            }
          ],
          "correctChoiceId": "ws04q07a",
          "explanation": "'저까치'는 경남에서 '젓가락'을 이르는 말입니다."
        },
        {
          "itemId": "ws04q08",
          "seq": 8,
          "cardId": "ws04c08",
          "prompt": "'이야기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q08a",
              "text": "이바구"
            },
            {
              "choiceId": "ws04q08b",
              "text": "두리깨"
            },
            {
              "choiceId": "ws04q08c",
              "text": "사분"
            },
            {
              "choiceId": "ws04q08d",
              "text": "도가지"
            }
          ],
          "correctChoiceId": "ws04q08a",
          "explanation": "'이바구'는 경남에서 '이야기'를 이르는 말입니다."
        },
        {
          "itemId": "ws04q09",
          "seq": 9,
          "cardId": "ws04c09",
          "prompt": "'마루'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q09a",
              "text": "성냥간"
            },
            {
              "choiceId": "ws04q09b",
              "text": "명지"
            },
            {
              "choiceId": "ws04q09c",
              "text": "삽짝"
            },
            {
              "choiceId": "ws04q09d",
              "text": "마리"
            }
          ],
          "correctChoiceId": "ws04q09d",
          "explanation": "'마리'는 경남에서 '마루'를 이르는 말입니다."
        },
        {
          "itemId": "ws04q10",
          "seq": 10,
          "cardId": "ws04c10",
          "prompt": "'절구'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws04q10a",
              "text": "삽짝"
            },
            {
              "choiceId": "ws04q10b",
              "text": "도구통"
            },
            {
              "choiceId": "ws04q10c",
              "text": "껍디기"
            },
            {
              "choiceId": "ws04q10d",
              "text": "장구방"
            }
          ],
          "correctChoiceId": "ws04q10b",
          "explanation": "'도구통'은 경남에서 '절구'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws05",
      "seq": 5,
      "level": 5,
      "category": "집과 살림",
      "title": "집과 살림 2",
      "cards": [
        {
          "cardId": "ws05c01",
          "standard": "삼태기",
          "dialect": "산태미"
        },
        {
          "cardId": "ws05c02",
          "standard": "다리미",
          "dialect": "대리미"
        },
        {
          "cardId": "ws05c03",
          "standard": "동이",
          "dialect": "동우"
        },
        {
          "cardId": "ws05c04",
          "standard": "독",
          "dialect": "도가지"
        },
        {
          "cardId": "ws05c05",
          "standard": "두레박",
          "dialect": "두룸박"
        },
        {
          "cardId": "ws05c06",
          "standard": "요강",
          "dialect": "오강"
        },
        {
          "cardId": "ws05c07",
          "standard": "베개",
          "dialect": "비개"
        },
        {
          "cardId": "ws05c08",
          "standard": "주걱",
          "dialect": "주개"
        },
        {
          "cardId": "ws05c09",
          "standard": "괭이",
          "dialect": "꽹이"
        },
        {
          "cardId": "ws05c10",
          "standard": "비누",
          "dialect": "사분"
        }
      ],
      "items": [
        {
          "itemId": "ws05q01",
          "seq": 1,
          "cardId": "ws05c01",
          "prompt": "'삼태기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q01a",
              "text": "홀깨"
            },
            {
              "choiceId": "ws05q01b",
              "text": "꽹이"
            },
            {
              "choiceId": "ws05q01c",
              "text": "산태미"
            },
            {
              "choiceId": "ws05q01d",
              "text": "삽짝"
            }
          ],
          "correctChoiceId": "ws05q01c",
          "explanation": "'산태미'는 경남에서 '삼태기'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q02",
          "seq": 2,
          "cardId": "ws05c02",
          "prompt": "'다리미'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q02a",
              "text": "마리"
            },
            {
              "choiceId": "ws05q02b",
              "text": "대리미"
            },
            {
              "choiceId": "ws05q02c",
              "text": "비개"
            },
            {
              "choiceId": "ws05q02d",
              "text": "새미"
            }
          ],
          "correctChoiceId": "ws05q02b",
          "explanation": "'대리미'는 경남에서 '다리미'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q03",
          "seq": 3,
          "cardId": "ws05c03",
          "prompt": "'동이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q03a",
              "text": "두리깨"
            },
            {
              "choiceId": "ws05q03b",
              "text": "동우"
            },
            {
              "choiceId": "ws05q03c",
              "text": "삽짝"
            },
            {
              "choiceId": "ws05q03d",
              "text": "이바구"
            }
          ],
          "correctChoiceId": "ws05q03b",
          "explanation": "'동우'는 경남에서 '동이'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q04",
          "seq": 4,
          "cardId": "ws05c04",
          "prompt": "'독'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q04a",
              "text": "껍디기"
            },
            {
              "choiceId": "ws05q04b",
              "text": "도가지"
            },
            {
              "choiceId": "ws05q04c",
              "text": "삽짝"
            },
            {
              "choiceId": "ws05q04d",
              "text": "새미"
            }
          ],
          "correctChoiceId": "ws05q04b",
          "explanation": "'도가지'는 경남에서 '독'을 이르는 말입니다."
        },
        {
          "itemId": "ws05q05",
          "seq": 5,
          "cardId": "ws05c05",
          "prompt": "'두레박'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q05a",
              "text": "이바구"
            },
            {
              "choiceId": "ws05q05b",
              "text": "사분"
            },
            {
              "choiceId": "ws05q05c",
              "text": "마리"
            },
            {
              "choiceId": "ws05q05d",
              "text": "두룸박"
            }
          ],
          "correctChoiceId": "ws05q05d",
          "explanation": "'두룸박'은 경남에서 '두레박'을 이르는 말입니다."
        },
        {
          "itemId": "ws05q06",
          "seq": 6,
          "cardId": "ws05c06",
          "prompt": "'요강'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q06a",
              "text": "오강"
            },
            {
              "choiceId": "ws05q06b",
              "text": "성냥간"
            },
            {
              "choiceId": "ws05q06c",
              "text": "산태미"
            },
            {
              "choiceId": "ws05q06d",
              "text": "장구방"
            }
          ],
          "correctChoiceId": "ws05q06a",
          "explanation": "'오강'은 경남에서 '요강'을 이르는 말입니다."
        },
        {
          "itemId": "ws05q07",
          "seq": 7,
          "cardId": "ws05c07",
          "prompt": "'베개'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q07a",
              "text": "꽹이"
            },
            {
              "choiceId": "ws05q07b",
              "text": "삽짝"
            },
            {
              "choiceId": "ws05q07c",
              "text": "비개"
            },
            {
              "choiceId": "ws05q07d",
              "text": "도구통"
            }
          ],
          "correctChoiceId": "ws05q07c",
          "explanation": "'비개'는 경남에서 '베개'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q08",
          "seq": 8,
          "cardId": "ws05c08",
          "prompt": "'주걱'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q08a",
              "text": "부지깨이"
            },
            {
              "choiceId": "ws05q08b",
              "text": "주개"
            },
            {
              "choiceId": "ws05q08c",
              "text": "오강"
            },
            {
              "choiceId": "ws05q08d",
              "text": "씨레기"
            }
          ],
          "correctChoiceId": "ws05q08b",
          "explanation": "'주개'는 경남에서 '주걱'을 이르는 말입니다."
        },
        {
          "itemId": "ws05q09",
          "seq": 9,
          "cardId": "ws05c09",
          "prompt": "'괭이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q09a",
              "text": "꽹이"
            },
            {
              "choiceId": "ws05q09b",
              "text": "구죽"
            },
            {
              "choiceId": "ws05q09c",
              "text": "사분"
            },
            {
              "choiceId": "ws05q09d",
              "text": "껍디기"
            }
          ],
          "correctChoiceId": "ws05q09a",
          "explanation": "'꽹이'는 경남에서 '괭이'를 이르는 말입니다."
        },
        {
          "itemId": "ws05q10",
          "seq": 10,
          "cardId": "ws05c10",
          "prompt": "'비누'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws05q10a",
              "text": "부지깨이"
            },
            {
              "choiceId": "ws05q10b",
              "text": "그륵"
            },
            {
              "choiceId": "ws05q10c",
              "text": "사분"
            },
            {
              "choiceId": "ws05q10d",
              "text": "꽹이"
            }
          ],
          "correctChoiceId": "ws05q10c",
          "explanation": "'사분'은 경남에서 '비누'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws06",
      "seq": 6,
      "level": 5,
      "category": "집과 살림",
      "title": "집과 살림 3",
      "cards": [
        {
          "cardId": "ws06c01",
          "standard": "대장간",
          "dialect": "성냥간"
        },
        {
          "cardId": "ws06c02",
          "standard": "명주",
          "dialect": "명지"
        },
        {
          "cardId": "ws06c03",
          "standard": "구두",
          "dialect": "구죽"
        },
        {
          "cardId": "ws06c04",
          "standard": "쓰레기",
          "dialect": "씨레기"
        },
        {
          "cardId": "ws06c05",
          "standard": "부지깽이",
          "dialect": "부지깨이"
        },
        {
          "cardId": "ws06c06",
          "standard": "가위",
          "dialect": "가시개"
        },
        {
          "cardId": "ws06c07",
          "standard": "사립문",
          "dialect": "삽짝"
        },
        {
          "cardId": "ws06c08",
          "standard": "벼훑이",
          "dialect": "홀깨"
        },
        {
          "cardId": "ws06c09",
          "standard": "껍데기",
          "dialect": "껍디기"
        }
      ],
      "items": [
        {
          "itemId": "ws06q01",
          "seq": 1,
          "cardId": "ws06c01",
          "prompt": "'대장간'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q01a",
              "text": "성냥간"
            },
            {
              "choiceId": "ws06q01b",
              "text": "사분"
            },
            {
              "choiceId": "ws06q01c",
              "text": "부지깨이"
            },
            {
              "choiceId": "ws06q01d",
              "text": "저까치"
            }
          ],
          "correctChoiceId": "ws06q01a",
          "explanation": "'성냥간'은 경남에서 '대장간'을 이르는 말입니다."
        },
        {
          "itemId": "ws06q02",
          "seq": 2,
          "cardId": "ws06c02",
          "prompt": "'명주'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q02a",
              "text": "명지"
            },
            {
              "choiceId": "ws06q02b",
              "text": "마리"
            },
            {
              "choiceId": "ws06q02c",
              "text": "이바구"
            },
            {
              "choiceId": "ws06q02d",
              "text": "도가지"
            }
          ],
          "correctChoiceId": "ws06q02a",
          "explanation": "'명지'는 경남에서 '명주'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q03",
          "seq": 3,
          "cardId": "ws06c03",
          "prompt": "'구두'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q03a",
              "text": "구죽"
            },
            {
              "choiceId": "ws06q03b",
              "text": "껍디기"
            },
            {
              "choiceId": "ws06q03c",
              "text": "장구방"
            },
            {
              "choiceId": "ws06q03d",
              "text": "사분"
            }
          ],
          "correctChoiceId": "ws06q03a",
          "explanation": "'구죽'은 경남에서 '구두'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q04",
          "seq": 4,
          "cardId": "ws06c04",
          "prompt": "'쓰레기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q04a",
              "text": "명지"
            },
            {
              "choiceId": "ws06q04b",
              "text": "부지깨이"
            },
            {
              "choiceId": "ws06q04c",
              "text": "저까치"
            },
            {
              "choiceId": "ws06q04d",
              "text": "씨레기"
            }
          ],
          "correctChoiceId": "ws06q04d",
          "explanation": "'씨레기'는 경남에서 '쓰레기'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q05",
          "seq": 5,
          "cardId": "ws06c05",
          "prompt": "'부지깽이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q05a",
              "text": "이바구"
            },
            {
              "choiceId": "ws06q05b",
              "text": "부지깨이"
            },
            {
              "choiceId": "ws06q05c",
              "text": "장구방"
            },
            {
              "choiceId": "ws06q05d",
              "text": "명지"
            }
          ],
          "correctChoiceId": "ws06q05b",
          "explanation": "'부지깨이'는 경남에서 '부지깽이'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q06",
          "seq": 6,
          "cardId": "ws06c06",
          "prompt": "'가위'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q06a",
              "text": "그륵"
            },
            {
              "choiceId": "ws06q06b",
              "text": "비개"
            },
            {
              "choiceId": "ws06q06c",
              "text": "도구통"
            },
            {
              "choiceId": "ws06q06d",
              "text": "가시개"
            }
          ],
          "correctChoiceId": "ws06q06d",
          "explanation": "'가시개'는 경남에서 '가위'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q07",
          "seq": 7,
          "cardId": "ws06c07",
          "prompt": "'사립문'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q07a",
              "text": "구죽"
            },
            {
              "choiceId": "ws06q07b",
              "text": "그륵"
            },
            {
              "choiceId": "ws06q07c",
              "text": "삽짝"
            },
            {
              "choiceId": "ws06q07d",
              "text": "씨레기"
            }
          ],
          "correctChoiceId": "ws06q07c",
          "explanation": "'삽짝'은 경남에서 '사립문'을 이르는 말입니다."
        },
        {
          "itemId": "ws06q08",
          "seq": 8,
          "cardId": "ws06c08",
          "prompt": "'벼훑이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q08a",
              "text": "주개"
            },
            {
              "choiceId": "ws06q08b",
              "text": "정지"
            },
            {
              "choiceId": "ws06q08c",
              "text": "동우"
            },
            {
              "choiceId": "ws06q08d",
              "text": "홀깨"
            }
          ],
          "correctChoiceId": "ws06q08d",
          "explanation": "'홀깨'는 경남에서 '벼훑이'를 이르는 말입니다."
        },
        {
          "itemId": "ws06q09",
          "seq": 9,
          "cardId": "ws06c09",
          "prompt": "'껍데기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws06q09a",
              "text": "껍디기"
            },
            {
              "choiceId": "ws06q09b",
              "text": "가시개"
            },
            {
              "choiceId": "ws06q09c",
              "text": "삽짝"
            },
            {
              "choiceId": "ws06q09d",
              "text": "홀깨"
            }
          ],
          "correctChoiceId": "ws06q09a",
          "explanation": "'껍디기'는 경남에서 '껍데기'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws07",
      "seq": 7,
      "level": 2,
      "category": "사람과 친족",
      "title": "사람과 친족 1",
      "cards": [
        {
          "cardId": "ws07c01",
          "standard": "사내아이",
          "dialect": "머스마"
        },
        {
          "cardId": "ws07c02",
          "standard": "어린아이",
          "dialect": "알라"
        },
        {
          "cardId": "ws07c03",
          "standard": "할아버지",
          "dialect": "할배"
        },
        {
          "cardId": "ws07c04",
          "standard": "계집아이",
          "dialect": "가시나"
        },
        {
          "cardId": "ws07c05",
          "standard": "할머니",
          "dialect": "할매"
        },
        {
          "cardId": "ws07c06",
          "standard": "아버지",
          "dialect": "아부지"
        }
      ],
      "items": [
        {
          "itemId": "ws07q01",
          "seq": 1,
          "cardId": "ws07c01",
          "prompt": "'사내아이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q01a",
              "text": "가시나"
            },
            {
              "choiceId": "ws07q01b",
              "text": "머스마"
            },
            {
              "choiceId": "ws07q01c",
              "text": "자슥"
            },
            {
              "choiceId": "ws07q01d",
              "text": "너거"
            }
          ],
          "correctChoiceId": "ws07q01b",
          "explanation": "'머스마'는 경남에서 '사내아이'를 이르는 말입니다."
        },
        {
          "itemId": "ws07q02",
          "seq": 2,
          "cardId": "ws07c02",
          "prompt": "'어린아이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q02a",
              "text": "자슥"
            },
            {
              "choiceId": "ws07q02b",
              "text": "너거"
            },
            {
              "choiceId": "ws07q02c",
              "text": "머스마"
            },
            {
              "choiceId": "ws07q02d",
              "text": "알라"
            }
          ],
          "correctChoiceId": "ws07q02d",
          "explanation": "'알라'는 경남에서 '어린아이'를 이르는 말입니다."
        },
        {
          "itemId": "ws07q03",
          "seq": 3,
          "cardId": "ws07c03",
          "prompt": "'할아버지'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q03a",
              "text": "가시나"
            },
            {
              "choiceId": "ws07q03b",
              "text": "아부지"
            },
            {
              "choiceId": "ws07q03c",
              "text": "할배"
            },
            {
              "choiceId": "ws07q03d",
              "text": "알라"
            }
          ],
          "correctChoiceId": "ws07q03c",
          "explanation": "'할배'는 경남에서 '할아버지'를 이르는 말입니다."
        },
        {
          "itemId": "ws07q04",
          "seq": 4,
          "cardId": "ws07c04",
          "prompt": "'계집아이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q04a",
              "text": "가시나"
            },
            {
              "choiceId": "ws07q04b",
              "text": "동세"
            },
            {
              "choiceId": "ws07q04c",
              "text": "머스마"
            },
            {
              "choiceId": "ws07q04d",
              "text": "누부"
            }
          ],
          "correctChoiceId": "ws07q04a",
          "explanation": "'가시나'는 경남에서 '계집아이'를 이르는 말입니다."
        },
        {
          "itemId": "ws07q05",
          "seq": 5,
          "cardId": "ws07c05",
          "prompt": "'할머니'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q05a",
              "text": "누부"
            },
            {
              "choiceId": "ws07q05b",
              "text": "할매"
            },
            {
              "choiceId": "ws07q05c",
              "text": "동세"
            },
            {
              "choiceId": "ws07q05d",
              "text": "손지"
            }
          ],
          "correctChoiceId": "ws07q05b",
          "explanation": "'할매'는 경남에서 '할머니'를 이르는 말입니다."
        },
        {
          "itemId": "ws07q06",
          "seq": 6,
          "cardId": "ws07c06",
          "prompt": "'아버지'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws07q06a",
              "text": "알라"
            },
            {
              "choiceId": "ws07q06b",
              "text": "동세"
            },
            {
              "choiceId": "ws07q06c",
              "text": "손지"
            },
            {
              "choiceId": "ws07q06d",
              "text": "아부지"
            }
          ],
          "correctChoiceId": "ws07q06d",
          "explanation": "'아부지'는 경남에서 '아버지'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws08",
      "seq": 8,
      "level": 4,
      "category": "사람과 친족",
      "title": "사람과 친족 2",
      "cards": [
        {
          "cardId": "ws08c01",
          "standard": "누나",
          "dialect": "누부"
        },
        {
          "cardId": "ws08c02",
          "standard": "자식",
          "dialect": "자슥"
        },
        {
          "cardId": "ws08c03",
          "standard": "손자",
          "dialect": "손지"
        },
        {
          "cardId": "ws08c04",
          "standard": "너희",
          "dialect": "너거"
        },
        {
          "cardId": "ws08c05",
          "standard": "동서",
          "dialect": "동세"
        },
        {
          "cardId": "ws08c06",
          "standard": "어머니",
          "dialect": "어매"
        }
      ],
      "items": [
        {
          "itemId": "ws08q01",
          "seq": 1,
          "cardId": "ws08c01",
          "prompt": "'누나'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q01a",
              "text": "할매"
            },
            {
              "choiceId": "ws08q01b",
              "text": "누부"
            },
            {
              "choiceId": "ws08q01c",
              "text": "알라"
            },
            {
              "choiceId": "ws08q01d",
              "text": "자슥"
            }
          ],
          "correctChoiceId": "ws08q01b",
          "explanation": "'누부'는 경남에서 '누나'를 이르는 말입니다."
        },
        {
          "itemId": "ws08q02",
          "seq": 2,
          "cardId": "ws08c02",
          "prompt": "'자식'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q02a",
              "text": "알라"
            },
            {
              "choiceId": "ws08q02b",
              "text": "자슥"
            },
            {
              "choiceId": "ws08q02c",
              "text": "할배"
            },
            {
              "choiceId": "ws08q02d",
              "text": "동세"
            }
          ],
          "correctChoiceId": "ws08q02b",
          "explanation": "'자슥'은 경남에서 '자식'을 이르는 말입니다."
        },
        {
          "itemId": "ws08q03",
          "seq": 3,
          "cardId": "ws08c03",
          "prompt": "'손자'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q03a",
              "text": "누부"
            },
            {
              "choiceId": "ws08q03b",
              "text": "너거"
            },
            {
              "choiceId": "ws08q03c",
              "text": "손지"
            },
            {
              "choiceId": "ws08q03d",
              "text": "알라"
            }
          ],
          "correctChoiceId": "ws08q03c",
          "explanation": "'손지'는 경남에서 '손자'를 이르는 말입니다."
        },
        {
          "itemId": "ws08q04",
          "seq": 4,
          "cardId": "ws08c04",
          "prompt": "'너희'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q04a",
              "text": "어매"
            },
            {
              "choiceId": "ws08q04b",
              "text": "너거"
            },
            {
              "choiceId": "ws08q04c",
              "text": "아부지"
            },
            {
              "choiceId": "ws08q04d",
              "text": "할배"
            }
          ],
          "correctChoiceId": "ws08q04b",
          "explanation": "'너거'는 경남에서 '너희'를 이르는 말입니다."
        },
        {
          "itemId": "ws08q05",
          "seq": 5,
          "cardId": "ws08c05",
          "prompt": "'동서'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q05a",
              "text": "누부"
            },
            {
              "choiceId": "ws08q05b",
              "text": "어매"
            },
            {
              "choiceId": "ws08q05c",
              "text": "동세"
            },
            {
              "choiceId": "ws08q05d",
              "text": "할배"
            }
          ],
          "correctChoiceId": "ws08q05c",
          "explanation": "'동세'는 경남에서 '동서'를 이르는 말입니다."
        },
        {
          "itemId": "ws08q06",
          "seq": 6,
          "cardId": "ws08c06",
          "prompt": "'어머니'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws08q06a",
              "text": "동세"
            },
            {
              "choiceId": "ws08q06b",
              "text": "할배"
            },
            {
              "choiceId": "ws08q06c",
              "text": "누부"
            },
            {
              "choiceId": "ws08q06d",
              "text": "어매"
            }
          ],
          "correctChoiceId": "ws08q06d",
          "explanation": "'어매'는 경남에서 '어머니'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws09",
      "seq": 9,
      "level": 4,
      "category": "몸",
      "title": "몸",
      "cards": [
        {
          "cardId": "ws09c01",
          "standard": "궁둥이",
          "dialect": "궁디"
        },
        {
          "cardId": "ws09c02",
          "standard": "눈",
          "dialect": "눈까리"
        },
        {
          "cardId": "ws09c03",
          "standard": "허벅지",
          "dialect": "허북지"
        },
        {
          "cardId": "ws09c04",
          "standard": "팔꿈치",
          "dialect": "팔꼼치"
        }
      ],
      "items": [
        {
          "itemId": "ws09q01",
          "seq": 1,
          "cardId": "ws09c01",
          "prompt": "'궁둥이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws09q01a",
              "text": "궁디"
            },
            {
              "choiceId": "ws09q01b",
              "text": "눈까리"
            },
            {
              "choiceId": "ws09q01c",
              "text": "허북지"
            },
            {
              "choiceId": "ws09q01d",
              "text": "팔꼼치"
            }
          ],
          "correctChoiceId": "ws09q01a",
          "explanation": "'궁디'는 경남에서 '궁둥이'를 이르는 말입니다."
        },
        {
          "itemId": "ws09q02",
          "seq": 2,
          "cardId": "ws09c02",
          "prompt": "'눈'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws09q02a",
              "text": "궁디"
            },
            {
              "choiceId": "ws09q02b",
              "text": "허북지"
            },
            {
              "choiceId": "ws09q02c",
              "text": "눈까리"
            },
            {
              "choiceId": "ws09q02d",
              "text": "팔꼼치"
            }
          ],
          "correctChoiceId": "ws09q02c",
          "explanation": "'눈까리'는 경남에서 '눈'을 이르는 말입니다."
        },
        {
          "itemId": "ws09q03",
          "seq": 3,
          "cardId": "ws09c03",
          "prompt": "'허벅지'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws09q03a",
              "text": "허북지"
            },
            {
              "choiceId": "ws09q03b",
              "text": "팔꼼치"
            },
            {
              "choiceId": "ws09q03c",
              "text": "눈까리"
            },
            {
              "choiceId": "ws09q03d",
              "text": "궁디"
            }
          ],
          "correctChoiceId": "ws09q03a",
          "explanation": "'허북지'는 경남에서 '허벅지'를 이르는 말입니다."
        },
        {
          "itemId": "ws09q04",
          "seq": 4,
          "cardId": "ws09c04",
          "prompt": "'팔꿈치'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws09q04a",
              "text": "궁디"
            },
            {
              "choiceId": "ws09q04b",
              "text": "눈까리"
            },
            {
              "choiceId": "ws09q04c",
              "text": "허북지"
            },
            {
              "choiceId": "ws09q04d",
              "text": "팔꼼치"
            }
          ],
          "correctChoiceId": "ws09q04d",
          "explanation": "'팔꼼치'는 경남에서 '팔꿈치'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws10",
      "seq": 10,
      "level": 3,
      "category": "동물과 자연",
      "title": "동물과 자연 1",
      "cards": [
        {
          "cardId": "ws10c01",
          "standard": "노루",
          "dialect": "놀갱이"
        },
        {
          "cardId": "ws10c02",
          "standard": "뿌리다",
          "dialect": "가무치다"
        },
        {
          "cardId": "ws10c03",
          "standard": "복숭아밭",
          "dialect": "복숭밭"
        },
        {
          "cardId": "ws10c04",
          "standard": "개구리",
          "dialect": "깨구리"
        },
        {
          "cardId": "ws10c05",
          "standard": "파리",
          "dialect": "포리"
        },
        {
          "cardId": "ws10c06",
          "standard": "모기",
          "dialect": "모구"
        },
        {
          "cardId": "ws10c07",
          "standard": "강아지",
          "dialect": "강생이"
        },
        {
          "cardId": "ws10c08",
          "standard": "여우",
          "dialect": "야시"
        }
      ],
      "items": [
        {
          "itemId": "ws10q01",
          "seq": 1,
          "cardId": "ws10c01",
          "prompt": "'노루'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q01a",
              "text": "놀갱이"
            },
            {
              "choiceId": "ws10q01b",
              "text": "뒤지기"
            },
            {
              "choiceId": "ws10q01c",
              "text": "지실"
            },
            {
              "choiceId": "ws10q01d",
              "text": "토깨이"
            }
          ],
          "correctChoiceId": "ws10q01a",
          "explanation": "'놀갱이'는 경남에서 '노루'를 이르는 말입니다."
        },
        {
          "itemId": "ws10q02",
          "seq": 2,
          "cardId": "ws10c02",
          "prompt": "'뿌리다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q02a",
              "text": "놀갱이"
            },
            {
              "choiceId": "ws10q02b",
              "text": "가무치다"
            },
            {
              "choiceId": "ws10q02c",
              "text": "복숭밭"
            },
            {
              "choiceId": "ws10q02d",
              "text": "니비"
            }
          ],
          "correctChoiceId": "ws10q02b",
          "explanation": "'가무치다'는 경남에서 '뿌리다'를 이르는 말입니다."
        },
        {
          "itemId": "ws10q03",
          "seq": 3,
          "cardId": "ws10c03",
          "prompt": "'복숭아밭'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q03a",
              "text": "이실"
            },
            {
              "choiceId": "ws10q03b",
              "text": "강생이"
            },
            {
              "choiceId": "ws10q03c",
              "text": "거무"
            },
            {
              "choiceId": "ws10q03d",
              "text": "복숭밭"
            }
          ],
          "correctChoiceId": "ws10q03d",
          "explanation": "'복숭밭'은 경남에서 '복숭아밭'을 이르는 말입니다."
        },
        {
          "itemId": "ws10q04",
          "seq": 4,
          "cardId": "ws10c04",
          "prompt": "'개구리'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q04a",
              "text": "복숭밭"
            },
            {
              "choiceId": "ws10q04b",
              "text": "게울"
            },
            {
              "choiceId": "ws10q04c",
              "text": "깨구리"
            },
            {
              "choiceId": "ws10q04d",
              "text": "거무"
            }
          ],
          "correctChoiceId": "ws10q04c",
          "explanation": "'깨구리'는 경남에서 '개구리'를 이르는 말입니다."
        },
        {
          "itemId": "ws10q05",
          "seq": 5,
          "cardId": "ws10c05",
          "prompt": "'파리'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q05a",
              "text": "니비"
            },
            {
              "choiceId": "ws10q05b",
              "text": "고내이"
            },
            {
              "choiceId": "ws10q05c",
              "text": "복숭밭"
            },
            {
              "choiceId": "ws10q05d",
              "text": "포리"
            }
          ],
          "correctChoiceId": "ws10q05d",
          "explanation": "'포리'는 경남에서 '파리'를 이르는 말입니다."
        },
        {
          "itemId": "ws10q06",
          "seq": 6,
          "cardId": "ws10c06",
          "prompt": "'모기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q06a",
              "text": "모구"
            },
            {
              "choiceId": "ws10q06b",
              "text": "야시"
            },
            {
              "choiceId": "ws10q06c",
              "text": "거무"
            },
            {
              "choiceId": "ws10q06d",
              "text": "이실"
            }
          ],
          "correctChoiceId": "ws10q06a",
          "explanation": "'모구'는 경남에서 '모기'를 이르는 말입니다."
        },
        {
          "itemId": "ws10q07",
          "seq": 7,
          "cardId": "ws10c07",
          "prompt": "'강아지'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q07a",
              "text": "거무"
            },
            {
              "choiceId": "ws10q07b",
              "text": "가무치다"
            },
            {
              "choiceId": "ws10q07c",
              "text": "강생이"
            },
            {
              "choiceId": "ws10q07d",
              "text": "니비"
            }
          ],
          "correctChoiceId": "ws10q07c",
          "explanation": "'강생이'는 경남에서 '강아지'를 이르는 말입니다."
        },
        {
          "itemId": "ws10q08",
          "seq": 8,
          "cardId": "ws10c08",
          "prompt": "'여우'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws10q08a",
              "text": "뚜께비"
            },
            {
              "choiceId": "ws10q08b",
              "text": "가무치다"
            },
            {
              "choiceId": "ws10q08c",
              "text": "야시"
            },
            {
              "choiceId": "ws10q08d",
              "text": "고내이"
            }
          ],
          "correctChoiceId": "ws10q08c",
          "explanation": "'야시'는 경남에서 '여우'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws11",
      "seq": 11,
      "level": 5,
      "category": "동물과 자연",
      "title": "동물과 자연 2",
      "cards": [
        {
          "cardId": "ws11c01",
          "standard": "토끼",
          "dialect": "토깨이"
        },
        {
          "cardId": "ws11c02",
          "standard": "염소",
          "dialect": "염생이"
        },
        {
          "cardId": "ws11c03",
          "standard": "고양이",
          "dialect": "고내이"
        },
        {
          "cardId": "ws11c04",
          "standard": "나비",
          "dialect": "나부"
        },
        {
          "cardId": "ws11c05",
          "standard": "누에",
          "dialect": "니비"
        },
        {
          "cardId": "ws11c06",
          "standard": "거미",
          "dialect": "거무"
        },
        {
          "cardId": "ws11c07",
          "standard": "잠자리",
          "dialect": "철기"
        },
        {
          "cardId": "ws11c08",
          "standard": "두더지",
          "dialect": "뒤지기"
        }
      ],
      "items": [
        {
          "itemId": "ws11q01",
          "seq": 1,
          "cardId": "ws11c01",
          "prompt": "'토끼'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q01a",
              "text": "니비"
            },
            {
              "choiceId": "ws11q01b",
              "text": "고내이"
            },
            {
              "choiceId": "ws11q01c",
              "text": "토깨이"
            },
            {
              "choiceId": "ws11q01d",
              "text": "염생이"
            }
          ],
          "correctChoiceId": "ws11q01c",
          "explanation": "'토깨이'는 경남에서 '토끼'를 이르는 말입니다."
        },
        {
          "itemId": "ws11q02",
          "seq": 2,
          "cardId": "ws11c02",
          "prompt": "'염소'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q02a",
              "text": "포리"
            },
            {
              "choiceId": "ws11q02b",
              "text": "이실"
            },
            {
              "choiceId": "ws11q02c",
              "text": "염생이"
            },
            {
              "choiceId": "ws11q02d",
              "text": "모구"
            }
          ],
          "correctChoiceId": "ws11q02c",
          "explanation": "'염생이'는 경남에서 '염소'를 이르는 말입니다."
        },
        {
          "itemId": "ws11q03",
          "seq": 3,
          "cardId": "ws11c03",
          "prompt": "'고양이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q03a",
              "text": "모구"
            },
            {
              "choiceId": "ws11q03b",
              "text": "고내이"
            },
            {
              "choiceId": "ws11q03c",
              "text": "가무치다"
            },
            {
              "choiceId": "ws11q03d",
              "text": "토깨이"
            }
          ],
          "correctChoiceId": "ws11q03b",
          "explanation": "'고내이'는 경남에서 '고양이'를 이르는 말입니다."
        },
        {
          "itemId": "ws11q04",
          "seq": 4,
          "cardId": "ws11c04",
          "prompt": "'나비'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q04a",
              "text": "깨구리"
            },
            {
              "choiceId": "ws11q04b",
              "text": "고내이"
            },
            {
              "choiceId": "ws11q04c",
              "text": "모구"
            },
            {
              "choiceId": "ws11q04d",
              "text": "나부"
            }
          ],
          "correctChoiceId": "ws11q04d",
          "explanation": "'나부'는 경남에서 '나비'를 이르는 말입니다."
        },
        {
          "itemId": "ws11q05",
          "seq": 5,
          "cardId": "ws11c05",
          "prompt": "'누에'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q05a",
              "text": "깨구리"
            },
            {
              "choiceId": "ws11q05b",
              "text": "뒤지기"
            },
            {
              "choiceId": "ws11q05c",
              "text": "니비"
            },
            {
              "choiceId": "ws11q05d",
              "text": "게울"
            }
          ],
          "correctChoiceId": "ws11q05c",
          "explanation": "'니비'는 경남에서 '누에'를 이르는 말입니다."
        },
        {
          "itemId": "ws11q06",
          "seq": 6,
          "cardId": "ws11c06",
          "prompt": "'거미'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q06a",
              "text": "날쎄"
            },
            {
              "choiceId": "ws11q06b",
              "text": "깨구리"
            },
            {
              "choiceId": "ws11q06c",
              "text": "이실"
            },
            {
              "choiceId": "ws11q06d",
              "text": "거무"
            }
          ],
          "correctChoiceId": "ws11q06d",
          "explanation": "'거무'는 경남에서 '거미'를 이르는 말입니다."
        },
        {
          "itemId": "ws11q07",
          "seq": 7,
          "cardId": "ws11c07",
          "prompt": "'잠자리'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q07a",
              "text": "강생이"
            },
            {
              "choiceId": "ws11q07b",
              "text": "복숭밭"
            },
            {
              "choiceId": "ws11q07c",
              "text": "철기"
            },
            {
              "choiceId": "ws11q07d",
              "text": "구룸"
            }
          ],
          "correctChoiceId": "ws11q07c",
          "explanation": "'철기'는 경남에서 '잠자리'를 이르는 말입니다."
        },
        {
          "itemId": "ws11q08",
          "seq": 8,
          "cardId": "ws11c08",
          "prompt": "'두더지'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws11q08a",
              "text": "복숭밭"
            },
            {
              "choiceId": "ws11q08b",
              "text": "뒤지기"
            },
            {
              "choiceId": "ws11q08c",
              "text": "모구"
            },
            {
              "choiceId": "ws11q08d",
              "text": "뚜께비"
            }
          ],
          "correctChoiceId": "ws11q08b",
          "explanation": "'뒤지기'는 경남에서 '두더지'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws12",
      "seq": 12,
      "level": 5,
      "category": "동물과 자연",
      "title": "동물과 자연 3",
      "cards": [
        {
          "cardId": "ws12c01",
          "standard": "구름",
          "dialect": "구룸"
        },
        {
          "cardId": "ws12c02",
          "standard": "이슬",
          "dialect": "이실"
        },
        {
          "cardId": "ws12c03",
          "standard": "날씨",
          "dialect": "날쎄"
        },
        {
          "cardId": "ws12c04",
          "standard": "겨울",
          "dialect": "게울"
        },
        {
          "cardId": "ws12c05",
          "standard": "기슭",
          "dialect": "지실"
        },
        {
          "cardId": "ws12c06",
          "standard": "냄새",
          "dialect": "냄시"
        },
        {
          "cardId": "ws12c07",
          "standard": "두꺼비",
          "dialect": "뚜께비"
        }
      ],
      "items": [
        {
          "itemId": "ws12q01",
          "seq": 1,
          "cardId": "ws12c01",
          "prompt": "'구름'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws12q01a",
              "text": "구룸"
            },
            {
              "choiceId": "ws12q01b",
              "text": "날쎄"
            },
            {
              "choiceId": "ws12q01c",
              "text": "철기"
            },
            {
              "choiceId": "ws12q01d",
              "text": "뚜께비"
            }
          ],
          "correctChoiceId": "ws12q01a",
          "explanation": "'구룸'은 경남에서 '구름'을 이르는 말입니다."
        },
        {
          "itemId": "ws12q02",
          "seq": 2,
          "cardId": "ws12c02",
          "prompt": "'이슬'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws12q02a",
              "text": "냄시"
            },
            {
              "choiceId": "ws12q02b",
              "text": "포리"
            },
            {
              "choiceId": "ws12q02c",
              "text": "모구"
            },
            {
              "choiceId": "ws12q02d",
              "text": "이실"
            }
          ],
          "correctChoiceId": "ws12q02d",
          "explanation": "'이실'은 경남에서 '이슬'을 이르는 말입니다."
        },
        {
          "itemId": "ws12q03",
          "seq": 3,
          "cardId": "ws12c03",
          "prompt": "'날씨'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws12q03a",
              "text": "뚜께비"
            },
            {
              "choiceId": "ws12q03b",
              "text": "날쎄"
            },
            {
              "choiceId": "ws12q03c",
              "text": "구룸"
            },
            {
              "choiceId": "ws12q03d",
              "text": "염생이"
            }
          ],
          "correctChoiceId": "ws12q03b",
          "explanation": "'날쎄'는 경남에서 '날씨'를 이르는 말입니다."
        },
        {
          "itemId": "ws12q04",
          "seq": 4,
          "cardId": "ws12c04",
          "prompt": "'겨울'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws12q04a",
              "text": "복숭밭"
            },
            {
              "choiceId": "ws12q04b",
              "text": "토깨이"
            },
            {
              "choiceId": "ws12q04c",
              "text": "뚜께비"
            },
            {
              "choiceId": "ws12q04d",
              "text": "게울"
            }
          ],
          "correctChoiceId": "ws12q04d",
          "explanation": "'게울'은 경남에서 '겨울'을 이르는 말입니다."
        },
        {
          "itemId": "ws12q05",
          "seq": 5,
          "cardId": "ws12c05",
          "prompt": "'기슭'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws12q05a",
              "text": "뚜께비"
            },
            {
              "choiceId": "ws12q05b",
              "text": "날쎄"
            },
            {
              "choiceId": "ws12q05c",
              "text": "게울"
            },
            {
              "choiceId": "ws12q05d",
              "text": "지실"
            }
          ],
          "correctChoiceId": "ws12q05d",
          "explanation": "'지실'은 경남에서 '기슭'을 이르는 말입니다."
        },
        {
          "itemId": "ws12q06",
          "seq": 6,
          "cardId": "ws12c06",
          "prompt": "'냄새'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws12q06a",
              "text": "강생이"
            },
            {
              "choiceId": "ws12q06b",
              "text": "이실"
            },
            {
              "choiceId": "ws12q06c",
              "text": "냄시"
            },
            {
              "choiceId": "ws12q06d",
              "text": "포리"
            }
          ],
          "correctChoiceId": "ws12q06c",
          "explanation": "'냄시'는 경남에서 '냄새'를 이르는 말입니다."
        },
        {
          "itemId": "ws12q07",
          "seq": 7,
          "cardId": "ws12c07",
          "prompt": "'두꺼비'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws12q07a",
              "text": "거무"
            },
            {
              "choiceId": "ws12q07b",
              "text": "니비"
            },
            {
              "choiceId": "ws12q07c",
              "text": "가무치다"
            },
            {
              "choiceId": "ws12q07d",
              "text": "뚜께비"
            }
          ],
          "correctChoiceId": "ws12q07d",
          "explanation": "'뚜께비'는 경남에서 '두꺼비'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws13",
      "seq": 13,
      "level": 1,
      "category": "움직임과 상태",
      "title": "움직임과 상태 1",
      "cards": [
        {
          "cardId": "ws13c01",
          "standard": "먹다",
          "dialect": "묵다"
        },
        {
          "cardId": "ws13c02",
          "standard": "말하다",
          "dialect": "카다"
        },
        {
          "cardId": "ws13c03",
          "standard": "떨어지다",
          "dialect": "널찌다"
        },
        {
          "cardId": "ws13c04",
          "standard": "다니다",
          "dialect": "댕기다"
        },
        {
          "cardId": "ws13c05",
          "standard": "까뒤집다",
          "dialect": "까디비다"
        },
        {
          "cardId": "ws13c06",
          "standard": "끓이다",
          "dialect": "낋이다"
        },
        {
          "cardId": "ws13c07",
          "standard": "일어나다",
          "dialect": "일나다"
        },
        {
          "cardId": "ws13c08",
          "standard": "들어가다",
          "dialect": "들가다"
        },
        {
          "cardId": "ws13c09",
          "standard": "얻어먹다",
          "dialect": "얻어묵다"
        }
      ],
      "items": [
        {
          "itemId": "ws13q01",
          "seq": 1,
          "cardId": "ws13c01",
          "prompt": "'먹다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws13q01a",
              "text": "옇다"
            },
            {
              "choiceId": "ws13q01b",
              "text": "자물시다"
            },
            {
              "choiceId": "ws13q01c",
              "text": "묵다"
            },
            {
              "choiceId": "ws13q01d",
              "text": "카다"
            }
          ],
          "correctChoiceId": "ws13q01c",
          "explanation": "'묵다'는 경남에서 '먹다'를 이르는 말입니다."
        },
        {
          "itemId": "ws13q02",
          "seq": 2,
          "cardId": "ws13c02",
          "prompt": "'말하다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws13q02a",
              "text": "카다"
            },
            {
              "choiceId": "ws13q02b",
              "text": "문때다"
            },
            {
              "choiceId": "ws13q02c",
              "text": "낋이다"
            },
            {
              "choiceId": "ws13q02d",
              "text": "댕기다"
            }
          ],
          "correctChoiceId": "ws13q02a",
          "explanation": "'카다'는 경남에서 '말하다'를 이르는 말입니다."
        },
        {
          "itemId": "ws13q03",
          "seq": 3,
          "cardId": "ws13c03",
          "prompt": "'떨어지다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws13q03a",
              "text": "낑기다"
            },
            {
              "choiceId": "ws13q03b",
              "text": "널찌다"
            },
            {
              "choiceId": "ws13q03c",
              "text": "카다"
            },
            {
              "choiceId": "ws13q03d",
              "text": "쌂다"
            }
          ],
          "correctChoiceId": "ws13q03b",
          "explanation": "'널찌다'는 경남에서 '떨어지다'를 이르는 말입니다."
        },
        {
          "itemId": "ws13q04",
          "seq": 4,
          "cardId": "ws13c04",
          "prompt": "'다니다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws13q04a",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws13q04b",
              "text": "카다"
            },
            {
              "choiceId": "ws13q04c",
              "text": "뛰댕기다"
            },
            {
              "choiceId": "ws13q04d",
              "text": "댕기다"
            }
          ],
          "correctChoiceId": "ws13q04d",
          "explanation": "'댕기다'는 경남에서 '다니다'를 이르는 말입니다."
        },
        {
          "itemId": "ws13q05",
          "seq": 5,
          "cardId": "ws13c05",
          "prompt": "'까뒤집다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws13q05a",
              "text": "자물시다"
            },
            {
              "choiceId": "ws13q05b",
              "text": "묵다"
            },
            {
              "choiceId": "ws13q05c",
              "text": "널쭈다"
            },
            {
              "choiceId": "ws13q05d",
              "text": "까디비다"
            }
          ],
          "correctChoiceId": "ws13q05d",
          "explanation": "'까디비다'는 경남에서 '까뒤집다'를 이르는 말입니다."
        },
        {
          "itemId": "ws13q06",
          "seq": 6,
          "cardId": "ws13c06",
          "prompt": "'끓이다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws13q06a",
              "text": "낋이다"
            },
            {
              "choiceId": "ws13q06b",
              "text": "널쭈다"
            },
            {
              "choiceId": "ws13q06c",
              "text": "낑기다"
            },
            {
              "choiceId": "ws13q06d",
              "text": "갈차주다"
            }
          ],
          "correctChoiceId": "ws13q06a",
          "explanation": "'낋이다'는 경남에서 '끓이다'를 이르는 말입니다."
        },
        {
          "itemId": "ws13q07",
          "seq": 7,
          "cardId": "ws13c07",
          "prompt": "'일어나다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws13q07a",
              "text": "쌔리다"
            },
            {
              "choiceId": "ws13q07b",
              "text": "일나다"
            },
            {
              "choiceId": "ws13q07c",
              "text": "얻어묵다"
            },
            {
              "choiceId": "ws13q07d",
              "text": "자물시다"
            }
          ],
          "correctChoiceId": "ws13q07b",
          "explanation": "'일나다'는 경남에서 '일어나다'를 이르는 말입니다."
        },
        {
          "itemId": "ws13q08",
          "seq": 8,
          "cardId": "ws13c08",
          "prompt": "'들어가다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws13q08a",
              "text": "까디비다"
            },
            {
              "choiceId": "ws13q08b",
              "text": "들가다"
            },
            {
              "choiceId": "ws13q08c",
              "text": "옇다"
            },
            {
              "choiceId": "ws13q08d",
              "text": "몬하다"
            }
          ],
          "correctChoiceId": "ws13q08b",
          "explanation": "'들가다'는 경남에서 '들어가다'를 이르는 말입니다."
        },
        {
          "itemId": "ws13q09",
          "seq": 9,
          "cardId": "ws13c09",
          "prompt": "'얻어먹다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws13q09a",
              "text": "얻어묵다"
            },
            {
              "choiceId": "ws13q09b",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws13q09c",
              "text": "옇다"
            },
            {
              "choiceId": "ws13q09d",
              "text": "묵다"
            }
          ],
          "correctChoiceId": "ws13q09a",
          "explanation": "'얻어묵다'는 경남에서 '얻어먹다'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws14",
      "seq": 14,
      "level": 3,
      "category": "움직임과 상태",
      "title": "움직임과 상태 2",
      "cards": [
        {
          "cardId": "ws14c01",
          "standard": "뛰어다니다",
          "dialect": "뛰댕기다"
        },
        {
          "cardId": "ws14c02",
          "standard": "못하다",
          "dialect": "몬하다"
        },
        {
          "cardId": "ws14c03",
          "standard": "가르치다",
          "dialect": "갈차주다"
        },
        {
          "cardId": "ws14c04",
          "standard": "때리다",
          "dialect": "쌔리다"
        },
        {
          "cardId": "ws14c05",
          "standard": "문지르다",
          "dialect": "문때다"
        },
        {
          "cardId": "ws14c06",
          "standard": "데우다",
          "dialect": "데파다"
        },
        {
          "cardId": "ws14c07",
          "standard": "떨어뜨리다",
          "dialect": "널쭈다"
        },
        {
          "cardId": "ws14c08",
          "standard": "줍다",
          "dialect": "줏다"
        }
      ],
      "items": [
        {
          "itemId": "ws14q01",
          "seq": 1,
          "cardId": "ws14c01",
          "prompt": "'뛰어다니다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws14q01a",
              "text": "땡기다"
            },
            {
              "choiceId": "ws14q01b",
              "text": "뛰댕기다"
            },
            {
              "choiceId": "ws14q01c",
              "text": "쌔리다"
            },
            {
              "choiceId": "ws14q01d",
              "text": "갈차주다"
            }
          ],
          "correctChoiceId": "ws14q01b",
          "explanation": "'뛰댕기다'는 경남에서 '뛰어다니다'를 이르는 말입니다."
        },
        {
          "itemId": "ws14q02",
          "seq": 2,
          "cardId": "ws14c02",
          "prompt": "'못하다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws14q02a",
              "text": "줏다"
            },
            {
              "choiceId": "ws14q02b",
              "text": "까디비다"
            },
            {
              "choiceId": "ws14q02c",
              "text": "몬하다"
            },
            {
              "choiceId": "ws14q02d",
              "text": "문때다"
            }
          ],
          "correctChoiceId": "ws14q02c",
          "explanation": "'몬하다'는 경남에서 '못하다'를 이르는 말입니다."
        },
        {
          "itemId": "ws14q03",
          "seq": 3,
          "cardId": "ws14c03",
          "prompt": "'가르치다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws14q03a",
              "text": "댕기다"
            },
            {
              "choiceId": "ws14q03b",
              "text": "카다"
            },
            {
              "choiceId": "ws14q03c",
              "text": "갈차주다"
            },
            {
              "choiceId": "ws14q03d",
              "text": "쌂다"
            }
          ],
          "correctChoiceId": "ws14q03c",
          "explanation": "'갈차주다'는 경남에서 '가르치다'를 이르는 말입니다."
        },
        {
          "itemId": "ws14q04",
          "seq": 4,
          "cardId": "ws14c04",
          "prompt": "'때리다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws14q04a",
              "text": "일나다"
            },
            {
              "choiceId": "ws14q04b",
              "text": "땡기다"
            },
            {
              "choiceId": "ws14q04c",
              "text": "데파다"
            },
            {
              "choiceId": "ws14q04d",
              "text": "쌔리다"
            }
          ],
          "correctChoiceId": "ws14q04d",
          "explanation": "'쌔리다'는 경남에서 '때리다'를 이르는 말입니다."
        },
        {
          "itemId": "ws14q05",
          "seq": 5,
          "cardId": "ws14c05",
          "prompt": "'문지르다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws14q05a",
              "text": "문때다"
            },
            {
              "choiceId": "ws14q05b",
              "text": "갈차주다"
            },
            {
              "choiceId": "ws14q05c",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws14q05d",
              "text": "얻어묵다"
            }
          ],
          "correctChoiceId": "ws14q05a",
          "explanation": "'문때다'는 경남에서 '문지르다'를 이르는 말입니다."
        },
        {
          "itemId": "ws14q06",
          "seq": 6,
          "cardId": "ws14c06",
          "prompt": "'데우다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws14q06a",
              "text": "낑기다"
            },
            {
              "choiceId": "ws14q06b",
              "text": "데파다"
            },
            {
              "choiceId": "ws14q06c",
              "text": "널쭈다"
            },
            {
              "choiceId": "ws14q06d",
              "text": "뛰댕기다"
            }
          ],
          "correctChoiceId": "ws14q06b",
          "explanation": "'데파다'는 경남에서 '데우다'를 이르는 말입니다."
        },
        {
          "itemId": "ws14q07",
          "seq": 7,
          "cardId": "ws14c07",
          "prompt": "'떨어뜨리다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws14q07a",
              "text": "문때다"
            },
            {
              "choiceId": "ws14q07b",
              "text": "들가다"
            },
            {
              "choiceId": "ws14q07c",
              "text": "널쭈다"
            },
            {
              "choiceId": "ws14q07d",
              "text": "꿉다"
            }
          ],
          "correctChoiceId": "ws14q07c",
          "explanation": "'널쭈다'는 경남에서 '떨어뜨리다'를 이르는 말입니다."
        },
        {
          "itemId": "ws14q08",
          "seq": 8,
          "cardId": "ws14c08",
          "prompt": "'줍다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws14q08a",
              "text": "낋이다"
            },
            {
              "choiceId": "ws14q08b",
              "text": "줏다"
            },
            {
              "choiceId": "ws14q08c",
              "text": "문때다"
            },
            {
              "choiceId": "ws14q08d",
              "text": "데불다"
            }
          ],
          "correctChoiceId": "ws14q08b",
          "explanation": "'줏다'는 경남에서 '줍다'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws15",
      "seq": 15,
      "level": 5,
      "category": "움직임과 상태",
      "title": "움직임과 상태 3",
      "cards": [
        {
          "cardId": "ws15c01",
          "standard": "꿰매다",
          "dialect": "꼬매다"
        },
        {
          "cardId": "ws15c02",
          "standard": "당기다",
          "dialect": "땡기다"
        },
        {
          "cardId": "ws15c03",
          "standard": "굽다",
          "dialect": "꿉다"
        },
        {
          "cardId": "ws15c04",
          "standard": "삶다",
          "dialect": "쌂다"
        },
        {
          "cardId": "ws15c05",
          "standard": "까무러치다",
          "dialect": "자물시다"
        },
        {
          "cardId": "ws15c06",
          "standard": "끼이다",
          "dialect": "낑기다"
        },
        {
          "cardId": "ws15c07",
          "standard": "넣다",
          "dialect": "옇다"
        },
        {
          "cardId": "ws15c08",
          "standard": "데리다",
          "dialect": "데불다"
        }
      ],
      "items": [
        {
          "itemId": "ws15q01",
          "seq": 1,
          "cardId": "ws15c01",
          "prompt": "'꿰매다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws15q01a",
              "text": "얻어묵다"
            },
            {
              "choiceId": "ws15q01b",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws15q01c",
              "text": "옇다"
            },
            {
              "choiceId": "ws15q01d",
              "text": "자물시다"
            }
          ],
          "correctChoiceId": "ws15q01b",
          "explanation": "'꼬매다'는 경남에서 '꿰매다'를 이르는 말입니다."
        },
        {
          "itemId": "ws15q02",
          "seq": 2,
          "cardId": "ws15c02",
          "prompt": "'당기다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws15q02a",
              "text": "까디비다"
            },
            {
              "choiceId": "ws15q02b",
              "text": "쌔리다"
            },
            {
              "choiceId": "ws15q02c",
              "text": "땡기다"
            },
            {
              "choiceId": "ws15q02d",
              "text": "낋이다"
            }
          ],
          "correctChoiceId": "ws15q02c",
          "explanation": "'땡기다'는 경남에서 '당기다'를 이르는 말입니다."
        },
        {
          "itemId": "ws15q03",
          "seq": 3,
          "cardId": "ws15c03",
          "prompt": "'굽다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws15q03a",
              "text": "카다"
            },
            {
              "choiceId": "ws15q03b",
              "text": "문때다"
            },
            {
              "choiceId": "ws15q03c",
              "text": "갈차주다"
            },
            {
              "choiceId": "ws15q03d",
              "text": "꿉다"
            }
          ],
          "correctChoiceId": "ws15q03d",
          "explanation": "'꿉다'는 경남에서 '굽다'를 이르는 말입니다."
        },
        {
          "itemId": "ws15q04",
          "seq": 4,
          "cardId": "ws15c04",
          "prompt": "'삶다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws15q04a",
              "text": "옇다"
            },
            {
              "choiceId": "ws15q04b",
              "text": "널쭈다"
            },
            {
              "choiceId": "ws15q04c",
              "text": "낋이다"
            },
            {
              "choiceId": "ws15q04d",
              "text": "쌂다"
            }
          ],
          "correctChoiceId": "ws15q04d",
          "explanation": "'쌂다'는 경남에서 '삶다'를 이르는 말입니다."
        },
        {
          "itemId": "ws15q05",
          "seq": 5,
          "cardId": "ws15c05",
          "prompt": "'까무러치다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws15q05a",
              "text": "묵다"
            },
            {
              "choiceId": "ws15q05b",
              "text": "줏다"
            },
            {
              "choiceId": "ws15q05c",
              "text": "카다"
            },
            {
              "choiceId": "ws15q05d",
              "text": "자물시다"
            }
          ],
          "correctChoiceId": "ws15q05d",
          "explanation": "'자물시다'는 경남에서 '까무러치다'를 이르는 말입니다."
        },
        {
          "itemId": "ws15q06",
          "seq": 6,
          "cardId": "ws15c06",
          "prompt": "'끼이다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws15q06a",
              "text": "데파다"
            },
            {
              "choiceId": "ws15q06b",
              "text": "땡기다"
            },
            {
              "choiceId": "ws15q06c",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws15q06d",
              "text": "낑기다"
            }
          ],
          "correctChoiceId": "ws15q06d",
          "explanation": "'낑기다'는 경남에서 '끼이다'를 이르는 말입니다."
        },
        {
          "itemId": "ws15q07",
          "seq": 7,
          "cardId": "ws15c07",
          "prompt": "'넣다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws15q07a",
              "text": "문때다"
            },
            {
              "choiceId": "ws15q07b",
              "text": "옇다"
            },
            {
              "choiceId": "ws15q07c",
              "text": "꼬매다"
            },
            {
              "choiceId": "ws15q07d",
              "text": "널찌다"
            }
          ],
          "correctChoiceId": "ws15q07b",
          "explanation": "'옇다'는 경남에서 '넣다'를 이르는 말입니다."
        },
        {
          "itemId": "ws15q08",
          "seq": 8,
          "cardId": "ws15c08",
          "prompt": "'데리다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws15q08a",
              "text": "까디비다"
            },
            {
              "choiceId": "ws15q08b",
              "text": "데불다"
            },
            {
              "choiceId": "ws15q08c",
              "text": "줏다"
            },
            {
              "choiceId": "ws15q08d",
              "text": "일나다"
            }
          ],
          "correctChoiceId": "ws15q08b",
          "explanation": "'데불다'는 경남에서 '데리다'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws16",
      "seq": 16,
      "level": 2,
      "category": "성질과 모양",
      "title": "성질과 모양",
      "cards": [
        {
          "cardId": "ws16c01",
          "standard": "깊다",
          "dialect": "기프다"
        },
        {
          "cardId": "ws16c02",
          "standard": "따뜻하다",
          "dialect": "따시다"
        },
        {
          "cardId": "ws16c03",
          "standard": "조그맣다",
          "dialect": "쪼맨하다"
        },
        {
          "cardId": "ws16c04",
          "standard": "같다",
          "dialect": "겉다"
        },
        {
          "cardId": "ws16c05",
          "standard": "나쁘다",
          "dialect": "파이다"
        },
        {
          "cardId": "ws16c06",
          "standard": "졸리다",
          "dialect": "자부럽다"
        },
        {
          "cardId": "ws16c07",
          "standard": "외롭다",
          "dialect": "애럽다"
        }
      ],
      "items": [
        {
          "itemId": "ws16q01",
          "seq": 1,
          "cardId": "ws16c01",
          "prompt": "'깊다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws16q01a",
              "text": "겉다"
            },
            {
              "choiceId": "ws16q01b",
              "text": "따시다"
            },
            {
              "choiceId": "ws16q01c",
              "text": "기프다"
            },
            {
              "choiceId": "ws16q01d",
              "text": "자부럽다"
            }
          ],
          "correctChoiceId": "ws16q01c",
          "explanation": "'기프다'는 경남에서 '깊다'를 이르는 말입니다."
        },
        {
          "itemId": "ws16q02",
          "seq": 2,
          "cardId": "ws16c02",
          "prompt": "'따뜻하다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws16q02a",
              "text": "겉다"
            },
            {
              "choiceId": "ws16q02b",
              "text": "따시다"
            },
            {
              "choiceId": "ws16q02c",
              "text": "기프다"
            },
            {
              "choiceId": "ws16q02d",
              "text": "애럽다"
            }
          ],
          "correctChoiceId": "ws16q02b",
          "explanation": "'따시다'는 경남에서 '따뜻하다'를 이르는 말입니다."
        },
        {
          "itemId": "ws16q03",
          "seq": 3,
          "cardId": "ws16c03",
          "prompt": "'조그맣다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws16q03a",
              "text": "애럽다"
            },
            {
              "choiceId": "ws16q03b",
              "text": "따시다"
            },
            {
              "choiceId": "ws16q03c",
              "text": "겉다"
            },
            {
              "choiceId": "ws16q03d",
              "text": "쪼맨하다"
            }
          ],
          "correctChoiceId": "ws16q03d",
          "explanation": "'쪼맨하다'는 경남에서 '조그맣다'를 이르는 말입니다."
        },
        {
          "itemId": "ws16q04",
          "seq": 4,
          "cardId": "ws16c04",
          "prompt": "'같다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws16q04a",
              "text": "겉다"
            },
            {
              "choiceId": "ws16q04b",
              "text": "파이다"
            },
            {
              "choiceId": "ws16q04c",
              "text": "기프다"
            },
            {
              "choiceId": "ws16q04d",
              "text": "따시다"
            }
          ],
          "correctChoiceId": "ws16q04a",
          "explanation": "'겉다'는 경남에서 '같다'를 이르는 말입니다."
        },
        {
          "itemId": "ws16q05",
          "seq": 5,
          "cardId": "ws16c05",
          "prompt": "'나쁘다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws16q05a",
              "text": "기프다"
            },
            {
              "choiceId": "ws16q05b",
              "text": "파이다"
            },
            {
              "choiceId": "ws16q05c",
              "text": "따시다"
            },
            {
              "choiceId": "ws16q05d",
              "text": "겉다"
            }
          ],
          "correctChoiceId": "ws16q05b",
          "explanation": "'파이다'는 경남에서 '나쁘다'를 이르는 말입니다."
        },
        {
          "itemId": "ws16q06",
          "seq": 6,
          "cardId": "ws16c06",
          "prompt": "'졸리다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws16q06a",
              "text": "겉다"
            },
            {
              "choiceId": "ws16q06b",
              "text": "자부럽다"
            },
            {
              "choiceId": "ws16q06c",
              "text": "따시다"
            },
            {
              "choiceId": "ws16q06d",
              "text": "기프다"
            }
          ],
          "correctChoiceId": "ws16q06b",
          "explanation": "'자부럽다'는 경남에서 '졸리다'를 이르는 말입니다."
        },
        {
          "itemId": "ws16q07",
          "seq": 7,
          "cardId": "ws16c07",
          "prompt": "'외롭다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws16q07a",
              "text": "파이다"
            },
            {
              "choiceId": "ws16q07b",
              "text": "애럽다"
            },
            {
              "choiceId": "ws16q07c",
              "text": "쪼맨하다"
            },
            {
              "choiceId": "ws16q07d",
              "text": "자부럽다"
            }
          ],
          "correctChoiceId": "ws16q07b",
          "explanation": "'애럽다'는 경남에서 '외롭다'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws17",
      "seq": 17,
      "level": 1,
      "category": "정도와 때",
      "title": "정도와 때 1",
      "cards": [
        {
          "cardId": "ws17c01",
          "standard": "엄청",
          "dialect": "억수로"
        },
        {
          "cardId": "ws17c02",
          "standard": "많이",
          "dialect": "마이"
        },
        {
          "cardId": "ws17c03",
          "standard": "조금",
          "dialect": "쪼매"
        },
        {
          "cardId": "ws17c04",
          "standard": "빨리",
          "dialect": "싸게"
        },
        {
          "cardId": "ws17c05",
          "standard": "이제",
          "dialect": "인자"
        },
        {
          "cardId": "ws17c06",
          "standard": "날마다",
          "dialect": "만날천날"
        },
        {
          "cardId": "ws17c07",
          "standard": "함부로",
          "dialect": "무짜로"
        }
      ],
      "items": [
        {
          "itemId": "ws17q01",
          "seq": 1,
          "cardId": "ws17c01",
          "prompt": "'엄청'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws17q01a",
              "text": "싸게"
            },
            {
              "choiceId": "ws17q01b",
              "text": "억수로"
            },
            {
              "choiceId": "ws17q01c",
              "text": "만날천날"
            },
            {
              "choiceId": "ws17q01d",
              "text": "깔롱"
            }
          ],
          "correctChoiceId": "ws17q01b",
          "explanation": "'억수로'는 경남에서 '엄청'을 이르는 말입니다."
        },
        {
          "itemId": "ws17q02",
          "seq": 2,
          "cardId": "ws17c02",
          "prompt": "'많이'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws17q02a",
              "text": "마이"
            },
            {
              "choiceId": "ws17q02b",
              "text": "만날천날"
            },
            {
              "choiceId": "ws17q02c",
              "text": "깔롱"
            },
            {
              "choiceId": "ws17q02d",
              "text": "원캉"
            }
          ],
          "correctChoiceId": "ws17q02a",
          "explanation": "'마이'는 경남에서 '많이'를 이르는 말입니다."
        },
        {
          "itemId": "ws17q03",
          "seq": 3,
          "cardId": "ws17c03",
          "prompt": "'조금'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws17q03a",
              "text": "깔롱"
            },
            {
              "choiceId": "ws17q03b",
              "text": "쪼매"
            },
            {
              "choiceId": "ws17q03c",
              "text": "마이"
            },
            {
              "choiceId": "ws17q03d",
              "text": "인자"
            }
          ],
          "correctChoiceId": "ws17q03b",
          "explanation": "'쪼매'는 경남에서 '조금'을 이르는 말입니다."
        },
        {
          "itemId": "ws17q04",
          "seq": 4,
          "cardId": "ws17c04",
          "prompt": "'빨리'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws17q04a",
              "text": "짜다리"
            },
            {
              "choiceId": "ws17q04b",
              "text": "싸게"
            },
            {
              "choiceId": "ws17q04c",
              "text": "살째기"
            },
            {
              "choiceId": "ws17q04d",
              "text": "억수로"
            }
          ],
          "correctChoiceId": "ws17q04b",
          "explanation": "'싸게'는 경남에서 '빨리'를 이르는 말입니다."
        },
        {
          "itemId": "ws17q05",
          "seq": 5,
          "cardId": "ws17c05",
          "prompt": "'이제'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws17q05a",
              "text": "단디"
            },
            {
              "choiceId": "ws17q05b",
              "text": "한분"
            },
            {
              "choiceId": "ws17q05c",
              "text": "무짜로"
            },
            {
              "choiceId": "ws17q05d",
              "text": "인자"
            }
          ],
          "correctChoiceId": "ws17q05d",
          "explanation": "'인자'는 경남에서 '이제'를 이르는 말입니다."
        },
        {
          "itemId": "ws17q06",
          "seq": 6,
          "cardId": "ws17c06",
          "prompt": "'날마다'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws17q06a",
              "text": "만날천날"
            },
            {
              "choiceId": "ws17q06b",
              "text": "쪼매"
            },
            {
              "choiceId": "ws17q06c",
              "text": "원캉"
            },
            {
              "choiceId": "ws17q06d",
              "text": "깔롱"
            }
          ],
          "correctChoiceId": "ws17q06a",
          "explanation": "'만날천날'은 경남에서 '날마다'를 이르는 말입니다."
        },
        {
          "itemId": "ws17q07",
          "seq": 7,
          "cardId": "ws17c07",
          "prompt": "'함부로'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws17q07a",
              "text": "한분"
            },
            {
              "choiceId": "ws17q07b",
              "text": "무짜로"
            },
            {
              "choiceId": "ws17q07c",
              "text": "짜다리"
            },
            {
              "choiceId": "ws17q07d",
              "text": "억수로"
            }
          ],
          "correctChoiceId": "ws17q07b",
          "explanation": "'무짜로'는 경남에서 '함부로'를 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws18",
      "seq": 18,
      "level": 4,
      "category": "정도와 때",
      "title": "정도와 때 2",
      "cards": [
        {
          "cardId": "ws18c01",
          "standard": "한번",
          "dialect": "한분"
        },
        {
          "cardId": "ws18c02",
          "standard": "단단히",
          "dialect": "단디"
        },
        {
          "cardId": "ws18c03",
          "standard": "그다지",
          "dialect": "짜다리"
        },
        {
          "cardId": "ws18c04",
          "standard": "멋 부림",
          "dialect": "깔롱"
        },
        {
          "cardId": "ws18c05",
          "standard": "워낙",
          "dialect": "원캉"
        },
        {
          "cardId": "ws18c06",
          "standard": "살짝",
          "dialect": "살째기"
        }
      ],
      "items": [
        {
          "itemId": "ws18q01",
          "seq": 1,
          "cardId": "ws18c01",
          "prompt": "'한번'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws18q01a",
              "text": "깔롱"
            },
            {
              "choiceId": "ws18q01b",
              "text": "단디"
            },
            {
              "choiceId": "ws18q01c",
              "text": "한분"
            },
            {
              "choiceId": "ws18q01d",
              "text": "무짜로"
            }
          ],
          "correctChoiceId": "ws18q01c",
          "explanation": "'한분'은 경남에서 '한번'을 이르는 말입니다."
        },
        {
          "itemId": "ws18q02",
          "seq": 2,
          "cardId": "ws18c02",
          "prompt": "'단단히'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws18q02a",
              "text": "살째기"
            },
            {
              "choiceId": "ws18q02b",
              "text": "단디"
            },
            {
              "choiceId": "ws18q02c",
              "text": "쪼매"
            },
            {
              "choiceId": "ws18q02d",
              "text": "억수로"
            }
          ],
          "correctChoiceId": "ws18q02b",
          "explanation": "'단디'는 경남에서 '단단히'를 이르는 말입니다."
        },
        {
          "itemId": "ws18q03",
          "seq": 3,
          "cardId": "ws18c03",
          "prompt": "'그다지'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws18q03a",
              "text": "짜다리"
            },
            {
              "choiceId": "ws18q03b",
              "text": "깔롱"
            },
            {
              "choiceId": "ws18q03c",
              "text": "쪼매"
            },
            {
              "choiceId": "ws18q03d",
              "text": "살째기"
            }
          ],
          "correctChoiceId": "ws18q03a",
          "explanation": "'짜다리'는 경남에서 '그다지'를 이르는 말입니다."
        },
        {
          "itemId": "ws18q04",
          "seq": 4,
          "cardId": "ws18c04",
          "prompt": "'멋 부림'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws18q04a",
              "text": "깔롱"
            },
            {
              "choiceId": "ws18q04b",
              "text": "한분"
            },
            {
              "choiceId": "ws18q04c",
              "text": "인자"
            },
            {
              "choiceId": "ws18q04d",
              "text": "쪼매"
            }
          ],
          "correctChoiceId": "ws18q04a",
          "explanation": "'깔롱'은 경남에서 '멋 부림'을 이르는 말입니다."
        },
        {
          "itemId": "ws18q05",
          "seq": 5,
          "cardId": "ws18c05",
          "prompt": "'워낙'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws18q05a",
              "text": "싸게"
            },
            {
              "choiceId": "ws18q05b",
              "text": "만날천날"
            },
            {
              "choiceId": "ws18q05c",
              "text": "한분"
            },
            {
              "choiceId": "ws18q05d",
              "text": "원캉"
            }
          ],
          "correctChoiceId": "ws18q05d",
          "explanation": "'원캉'은 경남에서 '워낙'을 이르는 말입니다."
        },
        {
          "itemId": "ws18q06",
          "seq": 6,
          "cardId": "ws18c06",
          "prompt": "'살짝'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws18q06a",
              "text": "쪼매"
            },
            {
              "choiceId": "ws18q06b",
              "text": "싸게"
            },
            {
              "choiceId": "ws18q06c",
              "text": "단디"
            },
            {
              "choiceId": "ws18q06d",
              "text": "살째기"
            }
          ],
          "correctChoiceId": "ws18q06d",
          "explanation": "'살째기'는 경남에서 '살짝'을 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws19",
      "seq": 19,
      "level": 1,
      "category": "묻고 답하는 말",
      "title": "묻고 답하는 말 1",
      "cards": [
        {
          "cardId": "ws19c01",
          "standard": "무슨",
          "dialect": "무신"
        },
        {
          "cardId": "ws19c02",
          "standard": "어디",
          "dialect": "어데"
        },
        {
          "cardId": "ws19c03",
          "standard": "여기",
          "dialect": "여개"
        },
        {
          "cardId": "ws19c04",
          "standard": "얼마",
          "dialect": "얼매"
        },
        {
          "cardId": "ws19c05",
          "standard": "어찌",
          "dialect": "우예"
        },
        {
          "cardId": "ws19c06",
          "standard": "어떻게든",
          "dialect": "우야든동"
        }
      ],
      "items": [
        {
          "itemId": "ws19q01",
          "seq": 1,
          "cardId": "ws19c01",
          "prompt": "'무슨'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws19q01a",
              "text": "몬"
            },
            {
              "choiceId": "ws19q01b",
              "text": "우야든동"
            },
            {
              "choiceId": "ws19q01c",
              "text": "여개"
            },
            {
              "choiceId": "ws19q01d",
              "text": "무신"
            }
          ],
          "correctChoiceId": "ws19q01d",
          "explanation": "'무신'은 경남에서 '무슨'을 이르는 말입니다."
        },
        {
          "itemId": "ws19q02",
          "seq": 2,
          "cardId": "ws19c02",
          "prompt": "'어디'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws19q02a",
              "text": "어데"
            },
            {
              "choiceId": "ws19q02b",
              "text": "몬"
            },
            {
              "choiceId": "ws19q02c",
              "text": "우예"
            },
            {
              "choiceId": "ws19q02d",
              "text": "우야든동"
            }
          ],
          "correctChoiceId": "ws19q02a",
          "explanation": "'어데'는 경남에서 '어디'를 이르는 말입니다."
        },
        {
          "itemId": "ws19q03",
          "seq": 3,
          "cardId": "ws19c03",
          "prompt": "'여기'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws19q03a",
              "text": "여개"
            },
            {
              "choiceId": "ws19q03b",
              "text": "와"
            },
            {
              "choiceId": "ws19q03c",
              "text": "우예"
            },
            {
              "choiceId": "ws19q03d",
              "text": "하모"
            }
          ],
          "correctChoiceId": "ws19q03a",
          "explanation": "'여개'는 경남에서 '여기'를 이르는 말입니다."
        },
        {
          "itemId": "ws19q04",
          "seq": 4,
          "cardId": "ws19c04",
          "prompt": "'얼마'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws19q04a",
              "text": "그라모"
            },
            {
              "choiceId": "ws19q04b",
              "text": "얼매"
            },
            {
              "choiceId": "ws19q04c",
              "text": "몬"
            },
            {
              "choiceId": "ws19q04d",
              "text": "하모"
            }
          ],
          "correctChoiceId": "ws19q04b",
          "explanation": "'얼매'는 경남에서 '얼마'를 이르는 말입니다."
        },
        {
          "itemId": "ws19q05",
          "seq": 5,
          "cardId": "ws19c05",
          "prompt": "'어찌'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws19q05a",
              "text": "우야든동"
            },
            {
              "choiceId": "ws19q05b",
              "text": "몬"
            },
            {
              "choiceId": "ws19q05c",
              "text": "어데"
            },
            {
              "choiceId": "ws19q05d",
              "text": "우예"
            }
          ],
          "correctChoiceId": "ws19q05d",
          "explanation": "'우예'는 경남에서 '어찌'를 이르는 말입니다."
        },
        {
          "itemId": "ws19q06",
          "seq": 6,
          "cardId": "ws19c06",
          "prompt": "'어떻게든'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws19q06a",
              "text": "우야든동"
            },
            {
              "choiceId": "ws19q06b",
              "text": "와"
            },
            {
              "choiceId": "ws19q06c",
              "text": "그라모"
            },
            {
              "choiceId": "ws19q06d",
              "text": "고마"
            }
          ],
          "correctChoiceId": "ws19q06a",
          "explanation": "'우야든동'은 경남에서 '어떻게든'을 이르는 말입니다."
        }
      ]
    },
    {
      "setId": "ws20",
      "seq": 20,
      "level": 1,
      "category": "묻고 답하는 말",
      "title": "묻고 답하는 말 2",
      "cards": [
        {
          "cardId": "ws20c01",
          "standard": "그러면",
          "dialect": "그라모"
        },
        {
          "cardId": "ws20c02",
          "standard": "못",
          "dialect": "몬"
        },
        {
          "cardId": "ws20c03",
          "standard": "그만",
          "dialect": "고마"
        },
        {
          "cardId": "ws20c04",
          "standard": "왜",
          "dialect": "와"
        },
        {
          "cardId": "ws20c05",
          "standard": "아무렴",
          "dialect": "하모"
        }
      ],
      "items": [
        {
          "itemId": "ws20q01",
          "seq": 1,
          "cardId": "ws20c01",
          "prompt": "'그러면'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws20q01a",
              "text": "여개"
            },
            {
              "choiceId": "ws20q01b",
              "text": "와"
            },
            {
              "choiceId": "ws20q01c",
              "text": "그라모"
            },
            {
              "choiceId": "ws20q01d",
              "text": "무신"
            }
          ],
          "correctChoiceId": "ws20q01c",
          "explanation": "'그라모'는 경남에서 '그러면'을 이르는 말입니다."
        },
        {
          "itemId": "ws20q02",
          "seq": 2,
          "cardId": "ws20c02",
          "prompt": "'못'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws20q02a",
              "text": "그라모"
            },
            {
              "choiceId": "ws20q02b",
              "text": "무신"
            },
            {
              "choiceId": "ws20q02c",
              "text": "와"
            },
            {
              "choiceId": "ws20q02d",
              "text": "몬"
            }
          ],
          "correctChoiceId": "ws20q02d",
          "explanation": "'몬'은 경남에서 '못'을 이르는 말입니다."
        },
        {
          "itemId": "ws20q03",
          "seq": 3,
          "cardId": "ws20c03",
          "prompt": "'그만'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws20q03a",
              "text": "몬"
            },
            {
              "choiceId": "ws20q03b",
              "text": "와"
            },
            {
              "choiceId": "ws20q03c",
              "text": "여개"
            },
            {
              "choiceId": "ws20q03d",
              "text": "고마"
            }
          ],
          "correctChoiceId": "ws20q03d",
          "explanation": "'고마'는 경남에서 '그만'을 이르는 말입니다."
        },
        {
          "itemId": "ws20q04",
          "seq": 4,
          "cardId": "ws20c04",
          "prompt": "'왜'를 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws20q04a",
              "text": "그라모"
            },
            {
              "choiceId": "ws20q04b",
              "text": "와"
            },
            {
              "choiceId": "ws20q04c",
              "text": "하모"
            },
            {
              "choiceId": "ws20q04d",
              "text": "우예"
            }
          ],
          "correctChoiceId": "ws20q04b",
          "explanation": "'와'는 경남에서 '왜'를 이르는 말입니다."
        },
        {
          "itemId": "ws20q05",
          "seq": 5,
          "cardId": "ws20c05",
          "prompt": "'아무렴'을 경남 사투리로 무엇이라 할까요?",
          "choices": [
            {
              "choiceId": "ws20q05a",
              "text": "하모"
            },
            {
              "choiceId": "ws20q05b",
              "text": "몬"
            },
            {
              "choiceId": "ws20q05c",
              "text": "어데"
            },
            {
              "choiceId": "ws20q05d",
              "text": "그라모"
            }
          ],
          "correctChoiceId": "ws20q05a",
          "explanation": "'하모'는 경남에서 '아무렴'을 이르는 말입니다."
        }
      ]
    }
  ]
}$definition$,
        timestamp with time zone '2026-10-07T00:00:00Z');
