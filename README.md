# 🎓 Busca Semântica e RAG Corporativo (Protótipo de TCC)

Este repositório contém o protótipo de software desenvolvido para o Trabalho de Conclusão de Curso (TCC) intitulado **"Implementação de um Sistema de Busca Semântica e RAG para Bases de Conhecimento Corporativas"** no Instituto Federal Goiano - Campus Urutaí.

O objetivo do projeto é comparar uma abordagem de **Busca Semântica usando RAG (Retrieval-Augmented Generation)** com modelos de linguagem contra uma **baseline de Busca Lexical (SQL LIKE/BM25)** para extração de respostas em bases de conhecimento corporativas (documentos PDF e dados estruturados).

## 🛠️ Tecnologias Utilizadas
* **Java 25** + **Spring Boot 4**
* **Spring AI** (para orquestração de LLMs e Vetores)
* **PostgreSQL + pgvector** (Banco de dados relacional e vetorial)
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

3. **Inicie a infraestrutura de Banco de Dados:**
   O projeto conta com um arquivo `docker-compose.yaml` já configurado com a extensão `pgvector` e o script de inicialização `scripts/init-pgvector.sql` (que cria as tabelas `document_chunk` e `artigo_conhecimento`, já populada com registros de exemplo).
   ```bash
   docker-compose up -d
   ```

4. **Inicie a aplicação Spring Boot:**
   Utilize o Maven Wrapper já embutido no repositório.
   ```bash
   # No Linux/Mac
   ./mvnw spring-boot:run

   # No Windows
   mvnw.cmd spring-boot:run
   ```

   A aplicação iniciará na porta **8080**.

## 📚 Documentação e Swagger UI

Com a aplicação rodando, você pode acessar a interface interativa do Swagger para testar os endpoints sem necessidade do terminal:

👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

## 💻 Exemplos de Requisições (cURL)

Abaixo estão exemplos práticos de como interagir com as rotas principais da API.

### 1. Ingestão de Documento Corporativo (PDF)

Endpoint responsável por receber um PDF, extrair o texto, dividir em chunks, gerar os embeddings e persistir tanto no banco relacional (para a baseline lexical) quanto no vetorial (para o RAG).

```bash
curl --location 'http://localhost:8080/api/documents/upload' \
--form 'arquivo=@"/caminho/absoluto/para/o/seu/documento.pdf"'
```

### 2. Ingestão de Base Relacional

Lê os registros da tabela `artigo_conhecimento` (uma base estruturada de exemplo — artigos de wiki interna, FAQs, políticas), gera os embeddings e indexa no pgvector, fechando o objetivo de ingestão a partir de fontes estruturadas além dos PDFs.

```bash
curl --location --request POST 'http://localhost:8080/api/relational/ingest'
```

### 3. Busca Semântica (Sistema RAG)

Realiza a consulta processada pela inteligência artificial. O sistema transforma a pergunta em um vetor, busca os trechos mais relevantes no PostgreSQL (pgvector) e aciona o LLM para formular uma resposta fundamentada apenas no contexto recuperado.

```bash
curl --location 'http://localhost:8080/api/query' \
--header 'Content-Type: application/json' \
--data '{
    "pergunta": "Quantos dias de férias eu tenho direito?"
}'
```

### 4. Busca Lexical (Baseline - Comparação)

Realiza a consulta da mesma forma que os sistemas tradicionais (SQL `LIKE`), servindo como baseline de avaliação para a métrica de comparação do TCC.

```bash
curl --location 'http://localhost:8080/api/query/baseline?termo=ferias'
```

---

**Autor:** Luan Henrique Campos Soares

**Orientador:** Dr. Junio Cesar de Lima

**Curso:** Sistemas de Informação - Instituto Federal Goiano (Campus Urutaí)