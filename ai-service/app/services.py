from app.clients import LLMClient
from app.schemas import ChatRequest, ChatResponse, ChatSource


class ChatService:
    def __init__(self):
        self.llm_client = LLMClient()

    def chat(self, request: ChatRequest, contexts: list[dict] | None = None) -> ChatResponse:
        prompt = request.question
        if contexts:
            context_text = "\n\n".join(
                f"[片段 {index}] {item['text']}"
                for index, item in enumerate(contexts, start=1)
            )
            prompt = (
                "请根据下面的文档片段回答问题。只能使用文档片段中的信息；"
                "如果文档片段没有答案，请明确说明文档中没有相关信息。\n\n"
                f"文档片段：\n{context_text}\n\n"
                f"问题：{request.question}"
            )
        answer = self.llm_client.generate(prompt)
        sources = [
            ChatSource(
                text=item["text"],
                chunk_index=item["metadata"]["chunk_index"],
                distance=item["distance"],
            )
            for item in contexts or []
        ]
        return ChatResponse(answer=answer, sources=sources)
