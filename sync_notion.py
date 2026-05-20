from notion_client import Client
import os

notion = Client(auth=os.environ["NOTION_TOKEN"])
page_id = os.environ["NOTION_PAGE_ID"]

with open("README.md", "r", encoding="utf-8") as f:
    content = f.read()

notion.blocks.children.append(
    block_id=page_id,
    children=[
        {
            "object": "block",
            "type": "paragraph",
            "paragraph": {
                "rich_text": [{"type": "text", "text": {"content": content[:2000]}}]
            }
        }
    ]
)