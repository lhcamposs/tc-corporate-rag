# 🎓 Busca Semântica e RAG Corporativo (Protótipo de TCC)

Este repositório contém o protótipo de software desenvolvido para o Trabalho de Conclusão de Curso (TCC) intitulado **"Implementação de um Sistema de Busca Semântica e RAG para Bases de Conhecimento Corporativas"** no Instituto Federal Goiano - Campus Urutaí.

O objetivo do projeto é comparar uma abordagem de **Busca Semântica usando RAG (Retrieval-Augmented Generation)** com modelos de linguagem contra uma **baseline de Busca Lexical (full-text search do PostgreSQL)** para extração de respostas em bases de conhecimento corporativas (documentos PDF e dados estruturados).

## 🛠️ Tecnologias Utilizadas
* **Java 25** + **Spring Boot 4**
* **Spring AI** (para orquestração de LLMs e Vetores)
* **PostgreSQL + pgvector** (Banco de dados relacional e vetorial)
* **ChromaDB** (Segundo banco vetorial, para a comparação pgvector x ChromaDB)
* **Docker & Docker Compose** (Infraestrutura)
* **Ollama** (Modelos de LLM e Embeddings locais)
* **springdoc-openapi** (Documentação interativa via Swagger UI)

## 📋 Pré-requisitos

Antes de iniciar, certifique-se de ter as seguintes ferramentas instaladas em sua máquina:

1. **Java 25** (JDK configurado no `PATH`).
2. **Maven 3.8+** (Caso não queira usar o *wrapper* `./mvnw` incluso no projeto).
3. **Docker e Docker Compose** (Para subir o PostgreSQL com pgvector).
4. **Ollama** (Obrigatório caso esteja usando modelos locais como LLaMA 3.2 / nomic-embed-text para não depender de APIs pagas).

## ⚙️ Configuração

O projeto é configurado pelo arquivo `application.properties`, que **não é versionado** no repositório por conter credenciais locais. Antes de rodar:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Depois, ajuste `spring.datasource.username` e `spring.datasource.password` com as credenciais do seu PostgreSQL local. As demais propriedades já vêm com valores padrão prontos para desenvolvimento local (Ollama em `http://localhost:11434`, modelo `llama3.2:latest`, banco `tc_rag_db`, etc.).

> **Dois bancos vetoriais:** o `application.properties.example` já exclui o autoconfigure do pgvector (`spring.autoconfigure.exclude`) e define as propriedades do ChromaDB. Os dois autoconfigures não podem coexistir, então o `PgVectorStore` é montado em `config/VectorStoreConfig`, lendo as mesmas propriedades `spring.ai.vectorstore.pgvector.*`. Se você já tinha um `application.properties` local, copie essas linhas do `.example`.

Todas as propriedades também podem ser sobrescritas por variáveis de ambiente (útil em produção/CI), seguindo o padrão de binding relaxado do Spring Boot — por exemplo, `spring.datasource.url` vira `SPRING_DATASOURCE_URL`.

> **Nota:** Certifique-se de baixar os modelos no seu Ollama local rodando no terminal:
> `ollama run llama3.2` e `ollama pull nomic-embed-text`.

## 🚀 Passo a Passo: Subindo a Infraestrutura e Aplicação

1. **Clone o repositório:**
   ```bash
   git clone https://github.com/lhcamposs/tc-corporate-rag.git
   cd tc-corporate-rag
   ```

2. **Configure o `application.properties`** conforme a seção [⚙️ Configuração](#️-configuração) acima.

3. **Inicie a infraestrutura (PostgreSQL, ChromaDB e Ollama):**
   O projeto conta com um arquivo `docker-compose.yaml` já configurado com a extensão `pgvector` e o script de inicialização `scripts/init-pgvector.sql` (que cria as tabelas `document_chunk` e `artigo_conhecimento`, já populada com registros de exemplo).
   ```bash
   docker-compose up -d
   ```

   > **Banco já existente (criado antes das últimas mudanças)?** O script de inicialização só roda em volume novo. Aplique, uma vez e em ordem, os arquivos de `db/migrations/` (`001` a `004`) ou recrie o volume com `docker compose down -v`. As migrações 002, 003 e 004 podem ser reexecutadas sem problema; a 001 apaga os vetores antigos e exige reingestão, e a 004 troca os registros de `artigo_conhecimento` e exige chamar de novo o `POST /api/relational/ingest`.

4. **Inicie a aplicação Spring Boot:**
   Utilize o Maven Wrapper já embutido no repositório.
   ```bash
   # No Linux/Mac
   ./mvnw spring-boot:run

   # No Windows
   mvnw.cmd spring-boot:run
   ```

   A aplicação iniciará na porta **8080**.

## 🧪 Experimento (avaliação)

O protocolo de avaliação da monografia (Seção 3.9) é executado pelo script [`avaliacao/avaliar.py`](avaliacao/README.md), que usa os endpoints `/api/avaliacao/*`: recuperação isolada nos três métodos (pgvector, ChromaDB e lexical), geração do teto com os trechos do gabarito e exportação dos trechos para anotação. A geração usa temperatura 0 (`rag.temperatura`), para que a mesma pergunta com os mesmos trechos produza sempre a mesma resposta.

## 📚 Documentação e Swagger UI

Com a aplicação rodando, você pode acessar a interface interativa do Swagger para testar os endpoints sem necessidade do terminal:

👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

## 💻 Exemplos de Requisições (cURL)

Abaixo estão exemplos práticos de como interagir com as rotas principais da API.

### 1. Ingestão de Documento Corporativo (PDF)

Endpoint responsável por receber um PDF, extrair o texto, dividir em chunks, gerar os embeddings e persistir no banco relacional (baseline lexical) e em **todos os bancos vetoriais disponíveis** (pgvector e ChromaDB), com os mesmos IDs, para que os dois armazenem exatamente o mesmo corpus. A ingestão é idempotente: reenviar o mesmo arquivo atualiza os dados em vez de duplicá-los.

```bash
curl --location 'http://localhost:8080/api/documents/upload' \
--form 'arquivo=@"/caminho/absoluto/para/o/seu/documento.pdf"'
```

### 2. Ingestão de Base Relacional

Lê os registros da tabela `artigo_conhecimento` (uma base estruturada de exemplo, no formato de perguntas frequentes, com registros redigidos a partir dos documentos públicos do IF Goiano), gera os embeddings e indexa nos bancos vetoriais, fechando o objetivo de ingestão a partir de fontes estruturadas além dos PDFs.

```bash
curl --location --request POST 'http://localhost:8080/api/relational/ingest'
```

### 3. Busca Semântica (Sistema RAG)

Realiza a consulta processada pela inteligência artificial. O sistema transforma a pergunta em um vetor, busca os trechos mais relevantes no banco vetorial escolhido e aciona o LLM para formular uma resposta fundamentada apenas no contexto recuperado.

O parâmetro `store` escolhe o banco vetorial usado na recuperação: `pgvector` (padrão) ou `chroma`. Use a mesma pergunta nos dois para comparar relevância e tempo (objetivo c).

```bash
curl --location 'http://localhost:8080/api/query?store=pgvector' \
--header 'Content-Type: application/json' \
--data '{
    "pergunta": "Quantos dias de férias eu tenho direito?"
}'
```

A resposta traz o texto gerado, as **fontes** (os trechos recuperados e enviados ao LLM) e os tempos de recuperação e de geração medidos separadamente:

```json
{
  "resposta": "Você tem direito a 30 dias corridos de férias por ano [1].",
  "store": "pgvector",
  "fontes": [
    {
      "vectorId": "2deb4eb9-ae91-3e4d-86b0-a7b62f03f591",
      "arquivo": "politica-ferias.pdf",
      "chunkIndex": 0,
      "trecho": "Colaboradores têm direito a 30 dias corridos de férias...",
      "score": 0.83
    }
  ],
  "tempoRecuperacaoMs": 120,
  "tempoGeracaoMs": 720,
  "tempoRespostaMs": 842
}
```

O `vectorId` é o mesmo no pgvector, no ChromaDB e na baseline lexical, então os três métodos podem ser comparados contra um único gabarito. O `score` da busca vetorial é uma similaridade, e o da baseline é `ts_rank`: as escalas são diferentes e não devem ser comparadas diretamente. Um `store` inválido ou não habilitado retorna `400`.

> **Verifique no primeiro uso do ChromaDB:** para a comparação ser justa, a coleção do Chroma precisa usar distância **cosseno**, como o pgvector (`COSINE_DISTANCE`). Confira a métrica da coleção criada pelo Spring AI no servidor do Chroma antes de coletar resultados.

### 4. Busca Lexical (Baseline - Comparação)

Busca por palavras-chave usando o full-text search nativo do PostgreSQL (`tsvector` + `ts_rank`), servindo como baseline de avaliação para a métrica de comparação do TCC. Ignora acentos (`ferias` encontra "férias"), aplica stemming em português, descarta stopwords e devolve os trechos ordenados por relevância (até 10), cada um com o `vectorId` do chunk. Os termos são combinados com OR, então também aceita perguntas em linguagem natural.

> Em bancos criados antes desta versão, aplique as migrações de `db/migrations/` (001 a 004), uma vez, na ordem.

```bash
curl --location 'http://localhost:8080/api/query/baseline?termo=ferias'
```

---

**Autor:** Luan Henrique Campos Soares

**Orientador:** Dr. Junio Cesar de Lima

**Curso:** Sistemas de Informação - Instituto Federal Goiano (Campus Urutaí)
