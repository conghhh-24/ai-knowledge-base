"""Persist Codex's review of this specific run, after reading all 40 answers."""
import json
from pathlib import Path
from statistics import mean

root = Path(__file__).resolve().parent
path = root / 'results-1793325460045592.json'
data = json.loads(path.read_text(encoding='utf-8'))
assert len(data['results']) == 40
# Evidence chunks were checked against chunks.json and every returned source list.
evidence = {1:[0],2:[0],3:[0],4:[0],5:[0],6:[1],7:[1],8:[1],9:[1],
            10:[1,2],11:[2],12:[2],13:[2],14:[3],15:[2,3],16:[3]}
for row in data['results']:
    assert 'error' not in row
    i = row['id']
    source_ids = {s['chunk_index'] for s in row['response']['sources']}
    if i <= 16:
        required = evidence[i]
        sufficient = (bool(source_ids & set(required)) if i == 10
                      else set(required).issubset(source_ids))
        assert sufficient
    else:
        sufficient = None
    row['review'] = dict(
        reviewer='Codex qualitative review; no independent human review',
        answer_correct=i != 7, source_sufficient=sufficient, unsupported_claims=False,
        notes=('来源chunk 1提供随机文件名；答案对同一用户同名文件拒绝推断，按预设期望严格记为部分回答/不通过。题目本身范围含糊，保留原题和原评分。'
               if i == 7 else ('文档外题明确拒答，无具体参数编造。' if i > 16
                              else '所需依据chunk: ' + ','.join(map(str,evidence[i])))))
data['review_status'] = 'All 40 answers reviewed by Codex; independent human review pending'
path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
lines = ['\n## 实测结果（2026-10-30）',
         '\n已完成真实服务调用，共40次问答；没有Mock答案或请求错误。模型型号接口未返回，无法从结果核实，需以启动配置补录。',
         '\n评分由Codex逐题核对期望答案、返回来源和固定文档，尚未经独立人工复核。',
         '\n| TopK | 回答正确 | 来源充分（文档内题） | 文档外正确拒答 | 请求错误 | 平均耗时 |',
         '|---|---|---|---|---|---|']
for k in (3,5):
    rows = [r for r in data['results'] if r['top_k']==k]
    lines.append(f'| {k} | 19/20（95%） | 16/16（100%） | 4/4（100%） | 0/20 | {mean(r["elapsed_seconds"] for r in rows):.2f}秒 |')
lines += ['\n### 逐题评分', '\n| 题号 | 问题 | TopK=3 | TopK=5 |', '|---|---|---|---|']
for row in data['results'][:20]:
    outcome = '部分回答/不通过' if row['id']==7 else '通过'
    lines.append(f'| {row["id"]} | {row["question"]} | {outcome} | {outcome} |')
lines += ['\n### 差异与失败分析',
          '\n第7题两组均返回正确存储路径与随机文件名，但对同一用户同名上传是否覆盖表示文档未明确说明。依据预先固定的期望，严格判为部分回答。来源chunk 1已经命中，因此不是检索遗漏；原因涉及题目范围含糊与模型保守推断。后续可明确区分“不同用户同名文件”与“随机命名的作用”，不能改写本轮题目后追认通过。',
          '\n其余19题两组均通过，没有为了凑数量制造失败案例。本轮文档外题含明确的“未规定”表述，拒答难度较低；文档只有5块，TopK=5相当于全文输入。当前样本不足以证明真实长文档、跨块复杂问题的效果。',
          '\n本轮TopK=5未提高正确率，保留TopK=3作为当前基准；不据单次顺序运行的耗时推断稳定性能优势。后续需要更长文档、没有显式否定提示的文档外题和重复运行。',
          '\n原始答案、来源和逐题评分：[results-1793325460045592.json](evaluation/results-1793325460045592.json)。运行脚本：[run_evaluation.py](evaluation/run_evaluation.py)。']
report = root.parent / 'rag-evaluation.md'
text = report.read_text(encoding='utf-8')
text = text.replace('状态：材料已准备，真实模型调用与人工评分待执行。尚无正确率结论。',
                    '状态：已完成两组共40次真实服务问答和Codex评分，独立人工复核待完成。')
text = text.replace('待真实服务启动后执行。不得用示例数字代替实测结果，也不预设必须出现失败案例。',
                    '结果见下方；保留原始响应以供复核。')
if '\n## 实测结果' in text:
    text = text.split('\n## 实测结果')[0]
report.write_text(text + '\n'.join(lines) + '\n', encoding='utf-8')
print('Reviewed 40 responses and updated evaluation report')
