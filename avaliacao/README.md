# Experimento (Seção 3.9 da monografia)

Script que executa o protocolo de avaliação: compara **pgvector**, **ChromaDB** e a **baseline lexical** na recuperação
(Recall@1/3/5/10 e MRR@10) e avalia as respostas do modelo com a escala de 1 a 10 de Finardi et al. (2024), incluindo
o **teto** (pontuação máxima relativa).

## Preparação (uma vez)

```powershell
cd avaliacao
pip install -r requirements.txt
```

No `src/main/resources/application.properties` local (não versionado), confira antes de medir:

```properties
spring.ai.ollama.chat.model=llama3.2:3b
rag.temperatura=0.0
logging.level.org.springframework.ai=INFO
```

Suba a infraestrutura (`docker compose up -d`) e a aplicação (`mvnw.cmd spring-boot:run`). O script usa
`http://localhost:8080` por padrão (`--url` muda o endereço).

## Passo a passo

| # | Comando | O que faz |
|---|---|---|
| 1 | `python avaliar.py trechos` | Exporta os trechos da base para `trechos.xlsx` (vector_id, área, arquivo, texto), com filtro por coluna. |
| 2 | `python avaliar.py modelo` | Cria `gabarito.xlsx` com as 60 linhas já divididas por tipo e área (aba **Cotas** mostra o andamento). |
| 3 | *(manual)* | Escreva as perguntas, as respostas de referência e os vector_id relevantes (separados por `;`). |
| 4 | `python avaliar.py validar` | Confere o gabarito: campos obrigatórios, tipos, cotas e se os vector_id existem na base. |
| 5 | `python avaliar.py recuperar` | Roda as 60 perguntas nos três métodos (top-10, 5 repetições para o tempo) e calcula as métricas. Leva poucos minutos. |
| 6 | `python avaliar.py gerar` | Gera as respostas com pgvector, ChromaDB e teto (~170 gerações em CPU, **algumas horas**). Pode ser interrompido com Ctrl+C e retomado: continua de onde parou. No fim, cria `resultados/avaliacao_cega.xlsx`. |
| 7 | *(manual)* | Na planilha cega, preencha **nota** (1, 3, 5, 7 ou 10), **fundamentada** (sim/nao) e, nas perguntas sem resposta na base, **informou_ausencia**. A aba **Escala** traz os critérios. Não abra `chave_avaliacao.csv` antes de terminar. |
| 8 | `python avaliar.py notas` | Calcula nota média, degradação em relação ao teto, taxas de fundamentação e alucinação e os tempos de geração. |

Termine o gabarito (passo 3) **antes** dos passos 5 e 6, para que ele não seja influenciado pelos resultados.

## Arquivos gerados em `resultados/`

| Arquivo | Conteúdo | Uso na monografia |
|---|---|---|
| `recuperacao_metricas.csv` | Recall@k e MRR@10 por método: geral, por tipo de pergunta e por fonte (PDF/relacional) | Tabela de qualidade da recuperação |
| `recuperacao_por_pergunta.csv` | As mesmas métricas pergunta a pergunta | Exemplos e análise de falhas |
| `recuperacao_rankings.csv` | Os 10 trechos devolvidos por método, marcando os relevantes | Conferência e exemplos |
| `concordancia_pgvector_chroma.csv` | Se pgvector e ChromaDB devolveram os mesmos trechos na mesma ordem | Pergunta 2 da avaliação |
| `recuperacao_tempos*.csv` | Tempos de recuperação (mediana e p95) | Tabela de desempenho |
| `respostas.jsonl` | Todas as respostas geradas, com os trechos usados e os tempos | Registro do experimento |
| `avaliacao_cega.xlsx` / `chave_avaliacao.csv` | Planilha de notas (sem identificar o método) e a chave que liga cada linha ao método | Avaliação manual |
| `respostas_metricas.csv` | Nota média, degradação, fundamentação, alucinação e recusa correta | Tabela de qualidade das respostas |
| `geracao_tempos_resumo.csv` | Tempos de geração (mediana e p95) e participação da geração no tempo total | Tabela de desempenho |

Os CSV usam `;` como separador e vírgula decimal, para abrir direto no Excel em português.

## Rodada com outro modelo de embeddings (bge-m3)

Repete só a **recuperação** com o `bge-m3` (multilíngue, 1024 dimensões), sem alterar os resultados já gerados.
O perfil `bge-m3` usa uma tabela do pgvector (`vector_store_bge_m3`) e uma coleção do ChromaDB
(`tc_corporate_rag_bge_m3`) próprias; a baseline lexical e a tabela `document_chunk` são as mesmas.

```powershell
# 1. Baixar o modelo no Ollama (uma vez)
docker exec ollama ollama pull bge-m3

# 2. Subir a aplicação com o perfil (use aspas no -D)
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=bge-m3"

# 3. Em outro terminal: gerar os vetores de todos os trechos nos dois bancos
curl.exe -X POST http://localhost:8080/api/evaluation/reindex

# 4. Conferir (deve mostrar o mesmo numero de trechos em document_chunk e vector_store_bge_m3)
docker exec postgres-pgvector psql -U lhcampos -d tc_rag_db -c "SELECT (SELECT count(*) FROM document_chunk) AS trechos, (SELECT count(*) FROM vector_store_bge_m3) AS vetores_bge_m3;"

# 5. Rodar só a recuperação, gravando em outra pasta
cd avaliacao
python avaliar.py recuperar --saida resultados/bge_m3
```

As métricas ficam em `resultados/bge_m3/recuperacao_metricas.csv` (linhas `pgvector` e `chroma`; a linha `lexical`
repete a baseline e deve coincidir com a da pasta `resultados/com_prefixo`). Para voltar ao `nomic-embed-text`, reinicie
a aplicação sem o perfil: as tabelas antigas não foram alteradas.

## Espaço ocupado por cada banco

```powershell
docker exec postgres-pgvector psql -U lhcampos -d tc_rag_db -c "SELECT pg_size_pretty(pg_total_relation_size('vector_store')) AS pgvector, pg_size_pretty(pg_total_relation_size('document_chunk')) AS lexical;"
docker exec chromadb du -sh /data
```

## Endpoints usados

- `POST /api/evaluation/retrieve?method=pgvector|chroma|lexical&k=10`: só a recuperação.
- `POST /api/query?store=pgvector|chroma`: a consulta RAG normal (k = `rag.top-k` = 4).
- `POST /api/evaluation/ceiling`: resposta gerada com os trechos do gabarito (`{"pergunta": "...", "vectorIds": ["..."]}`).
- `GET /api/evaluation/chunks`: todos os trechos da base.
