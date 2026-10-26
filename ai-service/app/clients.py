from app.config import settings


class LLMClient:
    def __init__(self):
        self.api_key = settings.llm_api_key
        self.base_url = settings.llm_base_url
        self.model = settings.llm_model
        self.timeout_seconds = settings.llm_timeout_seconds

    def generate(self, question: str) -> str:
        if not self.api_key:
            return f"Mock answer for: {question}"

        return (
            f"API key exists. "
            f"Model: {self.model}. "
            f"Real LLM is not connected: {question}"
        )