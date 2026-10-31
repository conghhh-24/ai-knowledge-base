import chromadb
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient
from app import main
from app.vector_store import VectorStore


class VectorDeleteTest(unittest.TestCase):
    def test_delete_is_scoped_idempotent_and_needs_no_embedding_key(self):
        with patch('app.vector_store.chromadb.PersistentClient', return_value=chromadb.EphemeralClient()):
            with patch('app.vector_store.EmbeddingClient', side_effect=AssertionError('deletion must not initialize embeddings')):
                store = VectorStore()
            store.collection.upsert(
                ids=['11:0', '11:1', '12:0'],
                embeddings=[[1.0, 0.0], [0.0, 1.0], [0.5, 0.5]],
                metadatas=[{'document_id': 11}, {'document_id': 11}, {'document_id': 12}],
                documents=['first batch', 'second batch', 'another document'])
            with patch.object(main, 'get_vector_store', return_value=store):
                with TestClient(main.app) as client:
                    self.assertEqual(204, client.delete('/vectors/11').status_code)
                    self.assertEqual(204, client.delete('/vectors/11').status_code)
            self.assertEqual([], store.collection.get(where={'document_id': 11})['ids'])
            self.assertEqual(['12:0'], store.collection.get(where={'document_id': 12})['ids'])
            self.assertIsNone(store.embedding_client)


if __name__ == '__main__':
    unittest.main()
