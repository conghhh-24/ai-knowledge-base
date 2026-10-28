from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=2000)
    document_id: int | None = None


class ChatResponse(BaseModel):
    answer: str


class EmbeddingRequest(BaseModel):
    texts: list[str] = Field(..., min_length=1, max_length=20)


class EmbeddingResponse(BaseModel):
    count: int
    dimension: int


class VectorUpsertRequest(BaseModel):
    document_id: int
    chunk_start: int = Field(default=0, ge=0)
    chunks: list[str] = Field(..., min_length=1, max_length=20)


class VectorUpsertResponse(BaseModel):
    count: int


class VectorSearchRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=2000)
    top_k: int = Field(default=3, ge=1, le=20)
    document_id: int | None = None
