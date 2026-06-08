"""
미답변 질문 / 지식 관리 API
────────────────────────────────────────────
리더가 AI가 답변하지 못한 질문에 직접 답변을 입력합니다.
입력된 답변은 docs/qa_knowledge.md에 저장되고
ChromaDB에 즉시 추가되어 이후 AI 응답에 활용됩니다.
"""

from pathlib import Path
from typing import List

from fastapi import APIRouter, HTTPException
from langchain_core.documents import Document
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_text_splitters import RecursiveCharacterTextSplitter
from pydantic import BaseModel, Field

from core.llm import get_llm
from core.vectorstore import get_vectorstore
from memory.unanswered_store import answer_question, delete_question, get_all

router = APIRouter(prefix="/knowledge", tags=["knowledge"])

# ── no_result 요약 엔드포인트 ────────────────────────────────────

_SUMMARY_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """당신은 기업 온보딩 문서 보강을 돕는 AI입니다.
신입 직원들이 반복적으로 질문했지만 회사 문서에서 답을 찾지 못한 질문 목록을 분석하여,
관리자가 어떤 문서를 보강해야 하는지 파악할 수 있도록 요약과 액션을 제안합니다.

중요한 규칙:
- 회사 정책을 임의로 확정하지 않습니다.
- 문서 보강이 필요한 가능성만 제안합니다.
- 요약은 2-3문장으로 간결하게 작성합니다.
- 액션은 2-3개로 제한합니다.
- 마크다운 볼드(**) 등 특수 기호 사용 금지. 일반 텍스트로만 출력합니다."""),
    ("human", """다음은 신입 직원들이 반복적으로 질문했지만 회사 문서에서 답변 근거를 찾지 못한 질문 목록입니다.

질문 목록:
{questions}

이 질문들을 분석하여 아래 형식으로 답변해주세요:

요약: (공통 주제와 어떤 문서가 부족한지 2-3문장)
액션:
1. (구체적인 문서 보강 행동)
2. (구체적인 문서 보강 행동)
3. (필요시 추가)"""),
])


class NoResultSummaryRequest(BaseModel):
    companyCode: str = Field(..., description="회사 코드")
    questions: List[str] = Field(..., description="no_result 질문 목록")


class NoResultSummaryResponse(BaseModel):
    companyCode: str
    questionCount: int
    summary: str
    actions: List[str]


@router.post("/no-result/summary", response_model=NoResultSummaryResponse, tags=["internal"])
async def summarize_no_result(req: NoResultSummaryRequest):
    """
    no_result 질문 목록을 받아 AI가 요약 + 문서 보강 액션을 반환합니다.
    관리자 대시보드에서 문서 보강 후보 항목 클릭 시 호출합니다.
    """
    if not req.questions:
        raise HTTPException(status_code=400, detail="질문 목록이 비어있습니다.")

    questions_text = "\n".join(f"- {q}" for q in req.questions)

    try:
        chain = _SUMMARY_PROMPT | get_llm() | StrOutputParser()
        result = chain.invoke({"questions": questions_text})
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"요약 생성 실패: {str(e)}")

    # 요약과 액션 파싱 (헤더가 독립된 줄에 있는 형식 대응)
    import re as _re
    _clean = lambda s: _re.sub(r'\*+', '', s).strip()

    summary_match = _re.search(r'요약:\s*\*{0,2}\n?(.*?)(?=\n\s*\n?\*{0,2}액션:|$)', result, _re.DOTALL)
    summary = _clean(summary_match.group(1)) if summary_match else _clean(result.split("액션:")[0])

    actions = []
    actions_match = _re.search(r'액션:\s*\*{0,2}\n(.*)', result, _re.DOTALL)
    if actions_match:
        for line in actions_match.group(1).strip().splitlines():
            line = line.strip()
            if line and line[0].isdigit() and "." in line:
                action = _clean(line.split(".", 1)[1].strip())
                if action:
                    actions.append(action)

    return NoResultSummaryResponse(
        companyCode=req.companyCode,
        questionCount=len(req.questions),
        summary=summary,
        actions=actions,
    )

_QA_DOC_PATH = Path(__file__).parent.parent / "docs" / "qa_knowledge.md"

# 부적절한 답변 패턴
_BAD_PATTERNS = [
    "몰라", "모르겠", "어케 알어", "알 게 뭐야",
    "귀찮", "알아서 해", "직접 해",
]

_REFINE_PROMPT = ChatPromptTemplate.from_messages([
    ("system", "당신은 신입 직원을 위한 온보딩 도우미입니다. 사수의 답변을 신입 직원에게 친절하고 자연스럽게 전달해주세요."),
    ("human", """신입 직원이 "{question}" 라고 질문했어.
사수가 이렇게 답했어: "{raw_answer}"

이걸 신입 직원에게 친절하고 자연스럽게 전달해줘.
- 핵심 내용은 유지
- 반말/욕설/불친절한 표현은 정중하게 순화
- 너무 길면 요약
- 추가 설명 없이 답변만 출력"""),
])


def _refine_answer(raw_answer: str, question: str) -> str:
    """사수 답변을 Claude로 친절하게 순화. 실패 시 원본 반환."""
    try:
        chain = _REFINE_PROMPT | get_llm() | StrOutputParser()
        return chain.invoke({"question": question, "raw_answer": raw_answer})
    except Exception:
        return raw_answer


def _validate_answer(answer: str) -> bool:
    """부적절한 답변 필터링. 문제 있으면 False 반환."""
    if len(answer.strip()) < 5:
        return False
    for pattern in _BAD_PATTERNS:
        if pattern in answer:
            return False
    return True


class AnswerRequest(BaseModel):
    id: str = Field(..., description="미답변 질문 ID")
    answer: str = Field(..., description="리더가 입력한 답변")


class AnswerResponse(BaseModel):
    message: str
    question: str
    answer: str


@router.delete("/unanswered/{qid}")
def delete_unanswered(qid: str):
    """미답변 질문 삭제"""
    if not delete_question(qid):
        raise HTTPException(status_code=404, detail="질문을 찾을 수 없습니다.")
    return {"message": "삭제되었습니다."}


@router.get("/unanswered")
def list_questions():
    """전체 미답변·답변 완료 질문 목록 반환"""
    return get_all()


@router.post("/answer", response_model=AnswerResponse)
def save_answer(req: AnswerRequest):
    """
    리더 답변 저장 + ChromaDB 즉시 반영

    1. unanswered.json 상태를 answered로 업데이트
    2. docs/qa_knowledge.md에 Q&A 추가 (문서 히스토리 보존)
    3. ChromaDB에 직접 add_documents → 서버 재시작 없이 즉시 AI가 활용 가능
    """
    if not _validate_answer(req.answer):
        raise HTTPException(status_code=400, detail="부적절하거나 너무 짧은 답변입니다. 성실한 답변을 입력해주세요.")

    item = answer_question(req.id, req.answer)
    if not item:
        raise HTTPException(status_code=404, detail="질문을 찾을 수 없습니다.")

    question = item["question"]

    # 사수 답변을 친절하게 순화
    refined = _refine_answer(req.answer, question)

    # 1) qa_knowledge.md에 누적 저장
    _QA_DOC_PATH.parent.mkdir(parents=True, exist_ok=True)
    if not _QA_DOC_PATH.exists():
        _QA_DOC_PATH.write_text(
            "# 사수 직접 답변 지식 모음\n\n"
            "이 파일은 AI가 답변하지 못한 질문에 대해 사수가 직접 입력한 답변을 저장합니다.\n",
            encoding="utf-8",
        )
    with open(_QA_DOC_PATH, "a", encoding="utf-8") as f:
        f.write(f"\n\n## Q: {question}\n\n**A:** {refined}\n")

    # 2) ChromaDB에 즉시 추가 (재인덱싱 불필요)
    doc = Document(
        page_content=f"질문: {question}\n답변: {refined}",
        metadata={"source": "qa_knowledge.md"},
    )
    splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=50)
    chunks = splitter.split_documents([doc])
    get_vectorstore().add_documents(chunks)

    return AnswerResponse(
        message="답변이 저장되고 AI 지식에 즉시 반영되었습니다.",
        question=question,
        answer=refined,
    )
