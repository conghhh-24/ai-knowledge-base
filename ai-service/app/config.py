import os


class Settings:
    def __init__(self):
        self.llm_api_key = os.getenv("LLM_API_KEY")
        self.llm_base_url = os.getenv(
            "LLM_BASE_URL",
            "https://api.example.com"
        )
        self.llm_model = os.getenv(
            "LLM_MODEL",
            "default-model"
        )
        self.llm_timeout_seconds = float(
            os.getenv("LLM_TIMEOUT_SECONDS", "10")
        )


settings = Settings()