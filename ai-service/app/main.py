from fastapi import FastAPI

from app.embeddings import EmbeddingClient
from app.schemas import (
    ChatRequest, ChatResponse, EmbeddingRequest, EmbeddingResponse,
    VectorSearchRequest, VectorUpsertRequest, VectorUpsertResponse,
)
from app.services import ChatService
from app.vector_store import VectorStore

app = FastAPI()
chat_service = ChatService()
embedding_client = None
vector_store = None


def get_embedding_client():
    global embedding_client
    if embedding_client is None:
        embedding_client = EmbeddingClient()
    return embedding_client


def get_vector_store():
    global vector_store
    if vector_store is None:
        vector_store = VectorStore()
    return vector_store


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest):
    contexts = None
    if request.document_id is not None:
        contexts = get_vector_store().search(
            request.question, request.top_k, request.document_id
        )
    return chat_service.chat(request, contexts)


@app.post("/embeddings", response_model=EmbeddingResponse)
def embeddings(request: EmbeddingRequest):
    vectors = get_embedding_client().embed(request.texts)
    return EmbeddingResponse(count=len(vectors), dimension=len(vectors[0]))


@app.post("/vectors/upsert", response_model=VectorUpsertResponse)
def upsert_vectors(request: VectorUpsertRequest):
    count = get_vector_store().upsert(
        request.document_id, request.chunks, request.chunk_start
    )
    return VectorUpsertResponse(count=count)


@app.delete("/vectors/{document_id}", status_code=204)
def delete_vectors(document_id: int):
    # Deleting an absent document is also successful, so cleanup can be retried.
    get_vector_store().delete(document_id)


@app.post("/vectors/search")
def search_vectors(request: VectorSearchRequest):
    return {"results": get_vector_store().search(
        request.question, request.top_k, request.document_id
    )}
