from app.clients import LLMClient
from app.schemas import ChatRequest, ChatResponse


class ChatService:
    def __init__(self):
        self.llm_client = LLMClient()

    def chat(self, request: ChatRequest) -> ChatResponse:
        answer = self.llm_client.generate(request.question)
        return ChatResponse(answer=answer)