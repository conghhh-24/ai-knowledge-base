from fastapi import FastAPI

from app.schemas import ChatRequest, ChatResponse
from app.services import ChatService

app = FastAPI()
chat_service = ChatService()


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest):
    return chat_service.chat(request)