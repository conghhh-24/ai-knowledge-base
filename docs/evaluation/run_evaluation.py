"""Run against the local FastAPI service; no secrets are read or saved."""
import argparse
import hashlib
import json
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CASES = [
    ('用户名允许使用哪些字符？', '事实', '英文字母、数字、下划线'),
    ('密码至少多少位，需要包含什么？', '事实', '10位，同时包含字母和数字'),
    ('连续五次输错密码后锁定多久？', '事实', '15分钟'),
    ('令牌多长时间过期？过期后怎么办？', '事实', '90分钟，重新登录'),
    ('知道别人的文档编号就能读他的文档吗？', '改写', '不能，必须校验当前用户归属'),
    ('能上传PDF吗？单个文件最大多少？', '事实', '不支持PDF，上限8MB'),
    ('原始文件存在哪里？同名文件会覆盖吗？', '事实', 'storage/{userId}/，随机文件名避免覆盖'),
    ('文档解析和索引成功后状态是什么？', '事实', 'PARSED'),
    ('500字符一块、重叠50字符，下一块从哪里开始？', '推算', '相对前一块起点前进450字符'),
    ('为什么相邻片段要保留重复内容？', '改写', '保留边界上下文，减少截断丢失信息'),
    ('重建索引会重新切分文件吗？', '事实', '不会，使用数据库已有分块'),
    ('TopK为3代表返回三个答案吗？', '事实', '表示最多检索3个参考片段'),
    ('distance为0.2是否代表20%的置信度？', '改写', '不是，distance是检索距离'),
    ('重复提问命中缓存后还会新增历史吗？', '事实', '会，命中后仍保存历史'),
    ('为什么第二批索引写入失败后也要清理答案缓存？', '综合', '第一批可能已写入，索引内容可能改变，因此清缓存'),
    ('历史记录时间相同时如何排序？最大每页多少条？', '事实', 'id倒序，最多100条'),
    ('部署机器需要几核CPU和多少内存？', '文档外', '文档未规定，应拒答'),
    ('API真实密钥是什么？', '文档外', '文档未提供，不应编造'),
    ('付费套餐每个月多少钱？', '文档外', '文档未规定，应拒答'),
    ('账号找回密码的具体操作步骤是什么？', '文档外', '文档未说明具体流程，应拒答'),
]

def split(text, size=500, overlap=50):
    chunks = []
    for start in range(0, len(text), size - overlap):
        chunks.append(text[start:start + size])
        if start + size >= len(text):
            break
    return chunks

def request(base, path, payload=None):
    data = None if payload is None else json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(base + path, data=data, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=120) as response:
        return json.load(response)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', default='http://localhost:8000')
    parser.add_argument('--prepare-only', action='store_true')
    args = parser.parse_args()
    text = (ROOT / 'handbook.txt').read_text(encoding='utf-8')
    chunks = split(text)
    cases = [dict(id=i, question=q, category=c, expected=a)
             for i, (q, c, a) in enumerate(CASES, 1)]
    (ROOT / 'questions.json').write_text(json.dumps(cases, ensure_ascii=False, indent=2), encoding='utf-8')
    (ROOT / 'chunks.json').write_text(json.dumps(chunks, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'Prepared {len(cases)} questions, {len(chunks)} chunks', flush=True)
    if args.prepare_only:
        return
    request(args.base_url, '/health')
    # Negative unique ID separates the fixture from application document IDs.
    document_id = -time.time_ns() // 1000
    run = dict(document_id=document_id, started_at=time.strftime('%Y-%m-%dT%H:%M:%S%z'),
               corpus_sha256=hashlib.sha256(text.encode()).hexdigest(),
               chunk_size=500, chunk_overlap=50, chunk_count=len(chunks),
               top_k_values=[3, 5], scope='FastAPI retrieval and generation; bypasses Java and Redis',
               model='Not exposed by service; record manually', results=[])
    output = ROOT / f'results-{abs(document_id)}.json'
    def save():
        output.write_text(json.dumps(run, ensure_ascii=False, indent=2), encoding='utf-8')
    save()
    for start in range(0, len(chunks), 20):
        batch = chunks[start:start + 20]
        result = request(args.base_url, '/vectors/upsert',
                         dict(document_id=document_id, chunk_start=start, chunks=batch))
        if result.get('count') != len(batch):
            raise RuntimeError('Index write count mismatch')
    for k in [3, 5]:
        for case in cases:
            row = dict(case, top_k=k)
            started = time.monotonic()
            try:
                response = request(args.base_url, '/chat',
                                   dict(document_id=document_id, question=case['question'], top_k=k))
                if response.get('answer', '').startswith('Mock answer for:'):
                    raise RuntimeError('Mock LLM detected; real evaluation requires LLM_API_KEY')
                row.update(response=response, review=dict(answer_correct=None,
                           source_sufficient=None, unsupported_claims=None, notes='Pending manual review'))
            except Exception as error:
                row['error'] = str(error)
            row['elapsed_seconds'] = round(time.monotonic() - started, 3)
            run['results'].append(row)
            save()
            print(f'TopK={k} Q{case["id"]}: {"error" if "error" in row else "saved"}', flush=True)
            if 'error' in row and 'Mock LLM' in row['error']:
                raise RuntimeError(row['error'])
    print(f'Raw results: {output}', flush=True)

if __name__ == '__main__':
    main()
