import unittest
from unittest.mock import Mock, patch
from app.schemas import ChatRequest
from app.services import ChatService

class EmptyContextTest(unittest.TestCase):
    def test_document_without_context_never_calls_model(self):
        with patch("app.services.LLMClient") as client:
            service = ChatService()
            for contexts in (None, []):
                answer = service.chat(ChatRequest(question="问题", document_id=123), contexts)
                self.assertEqual("文档中没有相关信息", answer.answer)
                self.assertEqual([], answer.sources)
            client.return_value.generate.assert_not_called()

    def test_normal_chat_still_calls_model(self):
        with patch("app.services.LLMClient") as client:
            client.return_value.generate.return_value = "answer"
            result = ChatService().chat(ChatRequest(question="hello"))
            self.assertEqual("answer", result.answer)
            client.return_value.generate.assert_called_once_with("hello")
