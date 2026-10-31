from app.config import settings
from openai import OpenAI


class LLMClient:
    def __init__(self):
        self.api_key = settings.llm_api_key
        self.base_url = settings.llm_base_url
        self.model = settings.llm_model
        self.timeout_seconds = settings.llm_timeout_seconds
        self.client = None
        if self.api_key:
            self.client = OpenAI(
                api_key=self.api_key,
                base_url=self.base_url,
                max_retries=0,
                timeout=self.timeout_seconds,
            )

    def generate(self, question: str) -> str:
        if not self.api_key:
            return f"Mock answer for: {question}"

        response = self.client.chat.completions.create(
            model=self.model,
            messages=[{"role": "user", "content": question}],
            stream=False,
        )
        return response.choices[0].message.content or ""
