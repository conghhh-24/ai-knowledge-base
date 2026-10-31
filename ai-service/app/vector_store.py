import chromadb

from app.config import settings
from app.embeddings import EmbeddingClient


class VectorStore:
    def __init__(self):
        self.embedding_client = None
        self.client = chromadb.PersistentClient(path=settings.chroma_path)
        self.collection = self.client.get_or_create_collection(
            name="document_chunks"
        )

    def upsert(self, document_id: int, chunks: list[str], chunk_start: int = 0) -> int:
        embeddings = self.get_embedding_client().embed(chunks)
        ids = [f"{document_id}:{index}" for index in range(chunk_start, chunk_start + len(chunks))]
        metadata = [
            {"document_id": document_id, "chunk_index": index}
            for index in range(chunk_start, chunk_start + len(chunks))
        ]
        self.collection.upsert(
            ids=ids,
            documents=chunks,
            embeddings=embeddings,
            metadatas=metadata,
        )
        return len(ids)

    def delete(self, document_id: int) -> None:
        self.collection.delete(where={"document_id": document_id})

    def get_embedding_client(self):
        if self.embedding_client is None:
            self.embedding_client = EmbeddingClient()
        return self.embedding_client

    def search(self, question: str, top_k: int, document_id: int | None = None):
        query_embedding = self.get_embedding_client().embed([question])[0]
        kwargs = {"query_embeddings": [query_embedding], "n_results": top_k}
        if document_id is not None:
            kwargs["where"] = {"document_id": document_id}
        result = self.collection.query(**kwargs)
        return [
            {"text": text, "metadata": metadata, "distance": distance}
            for text, metadata, distance in zip(
                result["documents"][0],
                result["metadatas"][0],
                result["distances"][0],
            )
        ]
