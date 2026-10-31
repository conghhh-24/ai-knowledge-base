from openai import OpenAI

from app.config import settings


class EmbeddingClient:
    def __init__(self):
        if not settings.embedding_api_key:
            raise RuntimeError("DASHSCOPE_API_KEY is not configured")
        self.client = OpenAI(
            api_key=settings.embedding_api_key,
            base_url=settings.embedding_base_url,
            max_retries=0,
            timeout=settings.embedding_timeout_seconds,
        )
        self.model = settings.embedding_model

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts or any(not text.strip() for text in texts):
            raise ValueError("texts must contain non-empty strings")
        response = self.client.embeddings.create(
            model=self.model,
            input=texts,
        )
        return [item.embedding for item in response.data]
