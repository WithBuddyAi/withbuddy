"""
HTML → PDF 변환 스크립트 (Playwright)
실행: python ai/scripts/make_pdf.py
"""

import os
from playwright.sync_api import sync_playwright

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

TARGETS = [
    {
        "html": os.path.join(BASE_DIR, "docs", "beta_test_report.html"),
        "pdf":  os.path.join(BASE_DIR, "docs", "WithBuddy_AI_베타테스트_보고서_20260510.pdf"),
        "name": "베타 테스트 보고서",
    },
    {
        "html": os.path.join(BASE_DIR, "scripts", "inscope_test_report.html"),
        "pdf":  os.path.join(BASE_DIR, "scripts", "WithBuddy_AI_INSCOPE_테스트_결과_20260510.pdf"),
        "name": "IN SCOPE 정답률 테스트 보고서",
    },
    {
        "html": os.path.join(BASE_DIR, "docs", "ai_progress_report.html"),
        "pdf":  os.path.join(BASE_DIR, "docs", "WithBuddy_AI_개발진척도_20260511.pdf"),
        "name": "AI 파트 개발 진척도",
    },
    {
        "html": os.path.join(BASE_DIR, "docs", "outscope_test_report.html"),
        "pdf":  os.path.join(BASE_DIR, "docs", "WithBuddy_AI_OOS_테스트결과_20260511.pdf"),
        "name": "OUT OF SCOPE 테스트 결과",
    },
]


def convert(page, target):
    html_path = target["html"]
    pdf_path  = target["pdf"]

    if not os.path.exists(html_path):
        print(f"  [SKIP] 파일 없음: {html_path}")
        return

    print(f"  변환 중: {target['name']} ...", end=" ", flush=True)

    url = "file:///" + html_path.replace("\\", "/")
    page.goto(url, wait_until="networkidle")

    page.pdf(
        path=pdf_path,
        format="A4",
        print_background=True,
        margin={"top": "15mm", "bottom": "15mm", "left": "14mm", "right": "14mm"},
    )

    size_kb = round(os.path.getsize(pdf_path) / 1024)
    print(f"완료 ({size_kb} KB) → {os.path.basename(pdf_path)}")


if __name__ == "__main__":
    print("\nHTML → PDF 변환 (Playwright)")
    print("=" * 60)
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page()
        for t in TARGETS:
            convert(page, t)
        browser.close()
    print("=" * 60)
    print("완료\n")
