import os


class Settings:
    def __init__(self):
        self.llm_api_key = os.getenv("LLM_API_KEY")
        self.llm_base_url = os.getenv(
            "LLM_BASE_URL",
            "https://api.deepseek.com"
        )
        self.llm_model = os.getenv(
            "LLM_MODEL",
            "deepseek-flash"
        )
        self.llm_timeout_seconds = float(
            os.getenv("LLM_TIMEOUT_SECONDS", "10")
        )
        self.embedding_api_key = os.getenv("DASHSCOPE_API_KEY")
        self.embedding_base_url = os.getenv(
            "DASHSCOPE_BASE_URL",
            "https://dashscope.aliyuncs.com/compatible-mode/v1"
        )
        self.embedding_model = os.getenv(
            "DASHSCOPE_EMBEDDING_MODEL",
            "text-embedding-v3"
        )
        self.chroma_path = os.getenv("CHROMA_PATH", "./data/chroma")


settings = Settings()
